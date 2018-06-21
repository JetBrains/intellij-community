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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalPanel
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import com.intellij.ui.content.Content
import com.jediterm.terminal.model.TerminalTextBuffer
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError

class TerminalFixture(project: Project, robot: Robot, toolWindowId: String) : ToolWindowFixture(toolWindowId, project, robot) {

  private val myJBTerminalPanel: JBTerminalPanel
  private val terminalTextBuffer: TerminalTextBuffer
  private val LOG: Logger = Logger.getInstance("#com.intellij.testGuiFramework.fixtures.TerminalFixture")

  init {
    val content: Content = this.getContent(getActiveTabName()) ?: throw Exception("Unable to get content of terminal tool window")
    try {
      myJBTerminalPanel = GuiTestUtilKt.withPauseWhenNull {
        getJBTerminalPanel(content)
      }
    }
    catch (e: WaitTimedOutError) {
      throw ComponentLookupException("Unable to find JBTerminalPanel")
    }
    terminalTextBuffer = myJBTerminalPanel.terminalTextBuffer
  }

  fun getScreenLines(): String {
    return terminalTextBuffer.screenLines
  }

  fun getLastLine(): String {
    val lastLineIndex = terminalTextBuffer.height - 1
    return terminalTextBuffer.getLine(lastLineIndex).text
  }

  fun waitUntilTextAppeared(text: String, timeoutInSeconds: Int = 60) {
    try {
      waitUntil(condition = "'$text' appeared in terminal", timeoutInSeconds = timeoutInSeconds) {
        terminalTextBuffer.screenLines.contains(text)
      }
    }
    finally {
      LOG.info("Text to find: $text")
      LOG.info("Terminal text: ${terminalTextBuffer.screenLines}")
    }

  }

  fun waitUntilRegExAppeared(regex: Regex, timeoutInSeconds: Int = 60) {
    waitUntil(condition = "'$regex' appeared in terminal", timeoutInSeconds = timeoutInSeconds) {
      terminalTextBuffer.screenLines.contains(regex)
    }
  }

  private fun getActiveTabName(): String {
    for (c in this.contents) {
      if (c.isSelected) return c.tabName
    }
    return ""
  }

  private fun getJBTerminalPanel(content: Content): JBTerminalPanel? {
    return try {
      myRobot.finder().find(content.component) { component -> component is JBTerminalPanel } as JBTerminalPanel
    }
    catch (e: ComponentLookupException) {
      null
    }
  }

}
