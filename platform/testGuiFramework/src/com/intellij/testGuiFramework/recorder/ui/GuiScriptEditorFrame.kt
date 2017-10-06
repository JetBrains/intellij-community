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
import java.awt.Container
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

class GuiScriptEditorFrame : Disposable {

  companion object {
    val GUI_SCRIPT_FRAME_TITLE = "GUI Script Editor"
  }

  private val myFrame: JFrame
  val guiScriptEditorPanel: GuiScriptEditorPanel

  init {
    myFrame = JFrame(GUI_SCRIPT_FRAME_TITLE)
    myFrame.preferredSize = Dimension(500, 800)

    guiScriptEditorPanel = GuiScriptEditorPanel()

    myFrame.contentPane = guiScriptEditorPanel.panel as Container
    myFrame.pack()
  }

  fun isShowing() = myFrame.isShowing

  fun show() {
    myFrame.isVisible = true
    StartPauseRecAction().setSelected(null, true)
  }

  fun toFront() {
    SwingUtilities.invokeLater { myFrame.toFront(); myFrame.repaint() }
  }

  override fun dispose() {
    guiScriptEditorPanel.dispose()
  }
}
