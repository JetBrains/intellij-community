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

import com.intellij.openapi.Disposable
import com.intellij.testGuiFramework.recorder.actions.StartPauseRecAction
import com.intellij.testGuiFramework.recorder.components.GuiRecorderComponent
import java.awt.Container
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities

class GuiScriptEditorFrame : Disposable {

  companion object {
    val GUI_SCRIPT_FRAME_TITLE = "GUI Script Editor"
  }

  override fun dispose() {
    guiScriptEditorPanel.releaseEditor()
    GuiRecorderComponent.disposeFrame()
  }

  val frameName = "GUI Script Editor"
  val myFrame: JFrame
  private val guiScriptEditorPanel: GuiScriptEditorPanel

  init {
    myFrame = JFrame(frameName)
    myFrame.preferredSize = Dimension(500, 800)

    //create editor if needed
    guiScriptEditorPanel = GuiScriptEditorPanel()
    val myContentPanel = guiScriptEditorPanel.panel as Container

    myFrame.contentPane = myContentPanel
    myFrame.pack()
    myFrame.isVisible = true

    GuiRecorderComponent.registerFrame(this)
    myFrame.addWindowListener(object : WindowAdapter() {
      override fun windowClosing(e: WindowEvent?) {
        dispose()
      }
    })

    val recAction = StartPauseRecAction()
    recAction.setSelected(null, true)
  }

  fun toFront() {
    SwingUtilities.invokeLater { myFrame.toFront(); myFrame.repaint() }
  }

  fun getGuiScriptEditorPanel() = guiScriptEditorPanel

  fun getEditor() = guiScriptEditorPanel.editor

  fun setSyncToEditor(toSync: Boolean) {
    guiScriptEditorPanel.syncToEditor = toSync
  }

  fun isSyncToEditor() = guiScriptEditorPanel.syncToEditor
}
