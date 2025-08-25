// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class UnmuteOnStopAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun isSelected(e: AnActionEvent): Boolean {
    return XDebuggerSettingManagerImpl.getInstanceImpl().generalSettings.isUnmuteOnStop
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    XDebuggerSettingManagerImpl.getInstanceImpl().generalSettings.isUnmuteOnStop = state
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}