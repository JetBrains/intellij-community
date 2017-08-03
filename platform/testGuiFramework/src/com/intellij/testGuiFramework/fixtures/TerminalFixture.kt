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

import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalPanel
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import org.fest.swing.core.Robot

class TerminalFixture(project: Project, robot: Robot): ToolWindowFixture("Terminal", project, robot) {

  private val myJBTerminalPanel: JBTerminalPanel

  init {
    val content = this.getContent("") ?: throw Exception("Unable to get content of terminal tool window")
    myJBTerminalPanel = myRobot.finder().find(content.component) { component -> component is JBTerminalPanel } as JBTerminalPanel

  }

  fun getScreenLines(): String {
    return myJBTerminalPanel.terminalTextBuffer.screenLines
  }

  fun getLastLine(): String {
    val lastLineIndex = myJBTerminalPanel.terminalTextBuffer.height - 1
    return myJBTerminalPanel.terminalTextBuffer.getLine(lastLineIndex).text
  }

  fun waitUntilTextAppeared(text: String, timeoutInSeconds: Int = 60) {
    waitUntil(condition = "'$text' appeared in terminal", timeoutInSeconds = timeoutInSeconds) {
      myJBTerminalPanel.terminalTextBuffer.screenLines.contains(text)
    }
  }

}