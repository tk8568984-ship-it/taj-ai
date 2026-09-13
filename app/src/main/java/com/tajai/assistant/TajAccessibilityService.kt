package com.tajai.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * This is TAJ's "hands". It can:
 *  - open any installed app by name
 *  - type text into whatever text field is currently focused (e.g. a WhatsApp chat box)
 *  - tap a button by its visible text/description (e.g. the WhatsApp "Send" button)
 *  - open the system Wi-Fi panel (Android does not allow silently toggling Wi-Fi
 *    programmatically since Android 10 — this opens the quick panel instead)
 *
 * IMPORTANT: automating taps inside third-party apps (WhatsApp, Instagram, etc.) depends on
 * the internal screen layout of THAT app. If WhatsApp changes its UI in an update, the
 * "find and tap Send" step may need to be adjusted here.
 */
class TajAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TajAccessibility"
        var instance: TajAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "TAJ accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Reserved for future use: e.g. detecting which app/screen is currently open,
        // so TAJ can give context-aware trading commentary without the user saying
        // which app they're looking at.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ---------- App launching ----------

    fun openApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return true
    }

    // ---------- Calling ----------

    fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // ---------- WhatsApp message (open chat pre-filled + auto-tap send) ----------

    fun sendWhatsAppMessage(phoneNumberWithCountryCode: String, message: String) {
        val uri = Uri.parse(
            "https://wa.me/$phoneNumberWithCountryCode?text=${Uri.encode(message)}"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        // Give WhatsApp a moment to open the chat, then try to tap "Send".
        // This is best-effort: it depends on WhatsApp's current UI.
        android.os.Handler(mainLooper).postDelayed({
            tapButtonByDescription("Send")
        }, 3500)
    }

    // ---------- Generic screen actions ----------

    /** Types text into whatever EditText is currently focused on screen. */
    fun typeIntoFocusedField(text: String): Boolean {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Finds a button/view by its visible text or content-description and taps it. */
    fun tapButtonByDescription(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, label) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (root.text?.toString()?.contains(text, ignoreCase = true) == true ||
            root.contentDescription?.toString()?.contains(text, ignoreCase = true) == true
        ) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }

    fun openWifiPanel() {
        val intent = Intent(Settings.Panel.ACTION_WIFI)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun openSettingsScreen(action: String = Settings.ACTION_SETTINGS) {
        val intent = Intent(action)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
