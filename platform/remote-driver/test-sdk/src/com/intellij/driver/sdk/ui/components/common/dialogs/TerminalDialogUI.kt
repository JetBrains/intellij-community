package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language

fun Finder.terminal(@Language("xpath") xpath: String = "//div[contains(@class, 'TerminalToolWindow') or contains(@class, 'ConsoleTerminalWidget')]") =
  x(xpath, TerminalDialogUI::class.java)

fun Finder.terminal(action: TerminalDialogUI.() -> Unit) {
  x("//div[contains(@class, 'TerminalToolWindow') or contains(@class, 'ConsoleTerminalWidget')]", TerminalDialogUI::class.java).action()
}

fun Driver.terminal(action: TerminalDialogUI.() -> Unit) {
  this.ui.terminal(action)
}

class TerminalDialogUI(data: ComponentData) : UiComponent(data) {
  init {
    if (notPresent()) {
      driver.ui.ideFrame().leftToolWindowToolbar.terminalButton.click()
    }
    waitFound()
  }

  fun execute(command: String, finishFlag: String = "") {
    this.click()
    keyboard {
      typeText(command)
      if (finishFlag.isNotEmpty()) waitFor { hasSubtext(finishFlag) }
    }
  }
}