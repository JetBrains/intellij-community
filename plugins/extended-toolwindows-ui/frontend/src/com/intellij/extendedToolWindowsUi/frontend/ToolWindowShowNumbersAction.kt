// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.extendedToolWindowsUi.frontend

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareToggleAction

internal class ToolWindowShowNumbersAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = UISettings.getInstance().showToolWindowsNumbers

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val uiSettings = UISettings.getInstance()
    uiSettings.showToolWindowsNumbers = state
    uiSettings.fireUISettingsChanged()
  }
}
