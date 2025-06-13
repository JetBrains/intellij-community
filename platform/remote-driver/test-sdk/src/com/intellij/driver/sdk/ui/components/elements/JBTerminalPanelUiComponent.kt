package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.xQuery

fun Finder.jbTerminalPanel() =
  x(xQuery { byClass("JBTerminalPanel") }, JBTerminalPanelUiComponent::class.java)

class JBTerminalPanelUiComponent(data: ComponentData) : UiComponent(data) {
  private val terminal by lazy { driver.cast(component, JBTerminalPanelRef::class) }
  val text: String
    get() {
      var result = ""
      for (i in 0 until terminal.getTerminalTextBuffer().historyLinesStorage.size) {
        result += terminal.getTerminalTextBuffer().historyLinesStorage.get(i).getText() + "\n"
      }
      return result
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
  fun getLinesAsString(): String
}

@Remote("com.jediterm.terminal.model.TerminalLine")
interface TerminalLine {
  fun getText(): String
}