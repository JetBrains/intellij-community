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
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import org.fest.swing.core.Robot
import javax.swing.JLabel

class EditorNotificationPanelFixture(val robot: Robot,
                                     val ideFrame: IdeFrameFixture,
                                     private val editorNotificationPanel: EditorNotificationPanel) {

  fun getLabelText(): String {
    val label = robot.finder().findAll(editorNotificationPanel) {
      it is JLabel
      && it.isShowing
      && it.isVisible
      && it.isEnabled
      && it.nonNullSize()
    }.firstOrNull() ?: throw Exception("Unable to find JLabel component for editor notification panel")
    return (label as JLabel).text
  }

  fun clickLink(linkText: String) {
    val hyperlinkLabel = robot.finder().findAll(
      editorNotificationPanel) { it is HyperlinkLabel && it.text == linkText }
                           .filterIsInstance(HyperlinkLabel::class.java)
                           .firstOrNull() ?: throw Exception(
      "Unable to find HyperlinkLabel with text \"$linkText\" for the EditorNotificationPanel")
    HyperlinkLabelFixture(robot, hyperlinkLabel).clickLink(linkText)
  }

  private fun JLabel.nonNullSize() = this.height > 0 && this.width > 0

  companion object {
    fun findEditorNotificationPanel(robot: Robot, ideFrame: IdeFrameFixture, timeoutInSeconds: Int = 30): EditorNotificationPanelFixture {
      val panel = GuiTestUtilKt.withPauseWhenNull(timeoutInSeconds) { findEditorNotificationPanel(robot, ideFrame) }
      return EditorNotificationPanelFixture(robot, ideFrame, panel)
    }

    private fun findEditorNotificationPanel(robot: Robot, ideFrame: IdeFrameFixture): EditorNotificationPanel? {
      return robot
        .finder()
        .findAll(ideFrame.target()) { it is EditorNotificationPanel }
        .firstOrNull() as EditorNotificationPanel?
    }
  }
}