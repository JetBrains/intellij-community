package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.xQuery

fun IdeaFrameUI.jbTerminalPanel(): JBTerminalPanelUiComponent =
  x(xQuery { byClass("JBTerminalPanel") }, JBTerminalPanelUiComponent::class.java)

class JBTerminalPanelUiComponent(data: ComponentData) : UiComponent(data) {
  private val terminal by lazy { driver.cast(component, JBTerminalPanelRef::class) }
  val text: String
    get() {
      val builder = StringBuilder()
      for (i in 0 until terminal.getTerminalTextBuffer().historyLinesStorage.size) {
        builder.append(terminal.getTerminalTextBuffer().historyLinesStorage.get(i).getText())
      }
      return builder.toString()
    }
}

@Remote("com.intellij.terminal.JBTerminalPanel")
interface JBTerminalPanelRef {
  fun getTerminalTextBuffer(): TerminalTextBufferRef
  fun getText(): String
}

@Remote("com.jediterm.terminal.model.TerminalTextBuffer")
interface TerminalTextBufferRef {
  var historyLinesStorage: LinesStorage
}

@Remote("com.jediterm.terminal.model.LinesStorage")
interface LinesStorage {
  val size: Int
  fun get(index: Int): TerminalLine
}

@Remote("com.jediterm.terminal.model.TerminalLine")
interface TerminalLine {
  fun getText(): String
}
