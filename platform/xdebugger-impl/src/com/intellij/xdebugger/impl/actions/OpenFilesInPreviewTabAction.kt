// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class OpenFilesInPreviewTabAction : ToggleAction(), DumbAware, SplitDebuggerAction {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return UISettings.getInstance().openInPreviewTabIfPossible
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    UISettings.getInstance().openInPreviewTabIfPossible = state
    saveSettingsForRemoteDevelopment(e.coroutineScope, application)
  }
}
