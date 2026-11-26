// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.extendedToolWindowsUi.frontend

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAwareToggleAction

internal class ToolWindowRotateIconsAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = AdvancedSettings.getBoolean(ROTATE_ICONS_SETTING_ID)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    AdvancedSettings.setBoolean(ROTATE_ICONS_SETTING_ID, state)
  }
}
