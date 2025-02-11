package com.intellij.driver.sdk.ui.components.kubernetes

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.kubernetesPodShellPanel(action: KubernetesPodShellPanelUI.() -> Unit) {
  x("//div[@class='KubernetesPodShellPanel']", KubernetesPodShellPanelUI::class.java).action()
}

class KubernetesPodShellPanelUI(data: ComponentData): UiComponent(data) {
  private val terminal = x("//div[@class='JBTerminalPanel']")

  fun executeCommand(text: String) {
    terminal.keyboard { typeText(text) }
    terminal.keyboard { enter() }
  }

  private fun getTerminalText(): String =
    terminal.getAllTexts().asString("")

  fun isCommandSuccessful(command: String): Boolean =
    getTerminalText().substringAfter(command).isNotEmpty()
}