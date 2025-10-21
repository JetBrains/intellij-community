// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend
import com.intellij.openapi.project.DumbAware
import com.intellij.util.application
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SortValuesToggleAction : ToggleAction(), DumbAware, FrontendOtherwiseBackend {
  override fun update(e: AnActionEvent) {
    super.update(e)

    val sessionProxy = DebuggerUIUtil.getSessionProxy(e)
    e.presentation.isEnabledAndVisible = sessionProxy != null && !sessionProxy.isValuesCustomSorted
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isSortValues
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isSortValues = state
    saveSettingsForRemoteDevelopment(application)
    XDebuggerUtilImpl.rebuildAllSessionsViews(e.project)
  }
}
