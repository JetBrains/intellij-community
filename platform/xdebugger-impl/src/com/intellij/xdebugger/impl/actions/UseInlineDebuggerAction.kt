/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.actions

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.util.application
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
class UseInlineDebuggerAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun isSelected(e: AnActionEvent): Boolean {
    return XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isShowValuesInline
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isShowValuesInline = state
    saveSettingsForRemoteDevelopment(application)
    XDebuggerUtilImpl.rebuildAllSessionsViews(e.project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
