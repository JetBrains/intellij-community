// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.util.application
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class UnmuteOnStopAction : ToggleAction(), DumbAware, SplitDebuggerAction {
  override fun isSelected(e: AnActionEvent): Boolean {
    return XDebuggerSettingsManager.getInstance().generalSettings.isUnmuteOnStop
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    XDebuggerSettingsManager.getInstance().generalSettings.isUnmuteOnStop = state
    saveSettingsForRemoteDevelopment(e.coroutineScope, application)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}