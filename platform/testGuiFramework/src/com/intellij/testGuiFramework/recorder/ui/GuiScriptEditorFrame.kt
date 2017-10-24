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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testGuiFramework.recorder.actions.StartPauseRecAction
import sun.awt.WindowClosingListener
import java.awt.Container
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import javax.swing.JFrame
import javax.swing.SwingUtilities

class GuiScriptEditorFrame : JFrame("GUI Script Editor"), Disposable {

  val guiScriptEditorPanel: GuiScriptEditorPanel

  init {
    preferredSize = Dimension(500, 800)
    guiScriptEditorPanel = GuiScriptEditorPanel()
    contentPane = guiScriptEditorPanel.panel as Container
    pack()
    Disposer.register(Disposer.get("ui"),this)
  }

  override fun show() {
    super.show()
    StartPauseRecAction().setSelected(null, true)
  }

  override fun toFront() {
    SwingUtilities.invokeLater { toFront(); repaint() }
  }
}