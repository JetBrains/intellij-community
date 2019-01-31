// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.recorder.ui

import com.intellij.testGuiFramework.recorder.GuiRecorderManager
import javax.swing.SwingUtilities

/**
 * @author Sergey Karashevich
 */
object Notifier {

  const val LONG_OPERATION_PREFIX: String = "<long>"

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