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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testGuiFramework.recorder.GlobalActionRecorder
import com.intellij.testGuiFramework.recorder.GuiRecorderListener
import com.intellij.testGuiFramework.recorder.GuiRecorderManager
import com.intellij.testGuiFramework.recorder.ui.Notifier

/**
 * @author Sergey Karashevich
 */
class StopRecAction : AnAction(null, "Stop Recording, Compiling, Running and Clear Buffer", AllIcons.Actions.Suspend) {

  override fun actionPerformed(p0: AnActionEvent) {
    GuiRecorderListener.notifyBeforeRecordingFinish()
    GlobalActionRecorder.deactivate()
    GuiRecorderManager.cancelCurrentTask()
    Notifier.updateStatus("Stopped")
    GuiRecorderManager.placeCaretToEnd()
    GuiRecorderListener.notifyRecordingFinished()
  }

}