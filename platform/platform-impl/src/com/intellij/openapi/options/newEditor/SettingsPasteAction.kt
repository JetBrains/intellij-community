// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actions.BasePasteHandler
import com.intellij.openapi.editor.actions.TextComponentEditorAction
import java.awt.KeyboardFocusManager

internal class SettingsPasteAction : TextComponentEditorAction(BasePasteHandler()), ActionPromoter {
  override fun update(e: AnActionEvent) {
    if (!isInSettingsContext()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }

  private fun isInSettingsContext(): Boolean {
    val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
    return SettingsNonModalDialog.isSettingsWindow(focusedWindow)
  }

  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return listOf(this)
  }
}
