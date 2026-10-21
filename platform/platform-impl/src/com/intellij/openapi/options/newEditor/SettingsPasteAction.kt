// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actions.BasePasteHandler
import com.intellij.openapi.editor.actions.TextComponentEditorAction
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import java.awt.KeyboardFocusManager
import java.awt.Window

/**
 * Marks each window of [SettingsNonModalDialog].
 *
 * [SettingsPasteAction] updates on EDT for each paste shortcut in the IDE.
 * It checks this marker and does not refer to [SettingsNonModalDialog], so a paste outside Settings does not load that class.
 */
internal val SETTINGS_WINDOW_KEY: Key<Boolean> = Key.create("Settings.Window")

/**
 * Returns `true` if [window] is a Settings window or if a Settings window owns it directly or transitively.
 * A Settings window is a window with [SETTINGS_WINDOW_KEY].
 */
internal fun isInSettingsWindow(window: Window?): Boolean {
  var w = window
  while (w != null) {
    if (ClientProperty.isTrue(w, SETTINGS_WINDOW_KEY)) return true
    w = w.owner
  }
  return false
}

internal class SettingsPasteAction : TextComponentEditorAction(BasePasteHandler()), ActionPromoter {
  override fun update(e: AnActionEvent) {
    if (!isInSettingsContext()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }

  private fun isInSettingsContext(): Boolean {
    return isInSettingsWindow(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow)
  }

  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return listOf(this)
  }
}
