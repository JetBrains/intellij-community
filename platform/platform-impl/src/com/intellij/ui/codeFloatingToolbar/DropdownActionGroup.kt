// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import javax.swing.JComponent

@Deprecated("Implement such group on your own")
class DropdownActionGroup: DefaultActionGroup(), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object: ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun actionPerformed(event: AnActionEvent) {
        showActionGroupPopup(this@DropdownActionGroup, event)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val activeActions = ActionGroupUtil.getActiveActions(this, e)
    e.presentation.isEnabled = activeActions.isNotEmpty
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}