/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.recorder.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.testGuiFramework.recorder.components.GuiRecorderComponent
import com.intellij.testGuiFramework.recorder.ui.Notifier

/**
 * @author Sergey Karashevich
 */
class SyncEditorAction : ToggleAction(null, "Synchronize Editor with Generated GUI Script", AllIcons.Actions.Refresh) {

  override fun isSelected(e: AnActionEvent?): Boolean = if (GuiRecorderComponent.getFrame() != null) GuiRecorderComponent.getFrame()!!.isSyncToEditor() else false

  override fun setSelected(e: AnActionEvent?, toSync: Boolean) {
    val frame = GuiRecorderComponent.getFrame() ?: return
    frame.setSyncToEditor(toSync)
    if (toSync)
      Notifier.updateStatus("Synchronization is on")
    else
      Notifier.updateStatus("Synchronization is off")
  }

}