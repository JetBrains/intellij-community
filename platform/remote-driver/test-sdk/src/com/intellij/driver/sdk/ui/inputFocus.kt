package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.remote.Window

/**
 * Reports whether a window of the IDE has the focus.
 * AWT gives no focused window when another application has the focus.
 */
internal fun Driver.isIdeFocused(): Boolean =
  utility(KeyboardFocusManagerRef::class).getCurrentKeyboardFocusManager().getFocusedWindow() != null

@Remote("java.awt.KeyboardFocusManager")
internal interface KeyboardFocusManagerRef {
  fun getCurrentKeyboardFocusManager(): KeyboardFocusManagerRef
  fun getFocusedWindow(): Window?
}
