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
package com.intellij.testGuiFramework.recorder.ui

import com.intellij.testGuiFramework.recorder.GuiRecorderManager
import javax.swing.SwingUtilities

/**
 * @author Sergey Karashevich
 */
object Notifier {

  val LONG_OPERATION_PREFIX = "<long>"

  fun updateStatus(statusMessage: String) {

    val guiScriptEditorPanel = GuiRecorderManager.frame.guiScriptEditorPanel

    val statusHandler: (String) -> Unit = { status ->
      if (status.startsWith(LONG_OPERATION_PREFIX)) {
        guiScriptEditorPanel.updateStatusWithProgress(status.removePrefix(LONG_OPERATION_PREFIX))
      }
      else {
        guiScriptEditorPanel.stopProgress()
        guiScriptEditorPanel.updateStatus(status)
      }
    }
    SwingUtilities.invokeLater { statusHandler.invoke(statusMessage) }
  }

}