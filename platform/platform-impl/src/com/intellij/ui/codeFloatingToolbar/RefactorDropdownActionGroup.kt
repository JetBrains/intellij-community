// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import javax.swing.JComponent

internal class RefactorDropdownActionGroup: DefaultActionGroup(), CustomComponentAction {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object: ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun actionPerformed(event: AnActionEvent) {
        showActionGroupPopup(this@RefactorDropdownActionGroup, event)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val language = e.getData(PlatformDataKeys.LANGUAGE)
    if (language != null) {
      e.presentation.isEnabledAndVisible = !hasMinimalFloatingToolbar(language)
    }
    else {
      val activeActions = ActionGroupUtil.getActiveActions(this, e)
      e.presentation.isVisible = true
      e.presentation.isEnabled = activeActions.isNotEmpty
    }
  }
}