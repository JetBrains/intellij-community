package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language


fun Finder.terminal(@Language("xpath") xpath: String = "//div[contains(@class, 'TerminalPanel') or contains(@class, 'ConsoleTerminalWidget')]"): TerminalDialogUI =
  x(xpath, TerminalDialogUI::class.java)

fun Finder.terminal(action: TerminalDialogUI.() -> Unit) {
  x("//div[contains(@class, 'TerminalPanel') or contains(@class, 'ConsoleTerminalWidget')]", TerminalDialogUI::class.java).action()
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

  val terminalView: TerminalViewImpl by lazy { driver.cast(component, TerminalViewImpl::class) }



  val rdPortForwardingPanelWidget: RdPortForwardingPanelWidget
    get() = x(RdPortForwardingPanelWidget::class.java) { byJavaClass("com.jetbrains.thinclient.portForwarding.ThinClientPortForwardingPanelWidget") }

  fun execute(command: String, finishFlag: String = "") {
    this.click()
    keyboard {
      typeText(command)
      if (finishFlag.isNotEmpty()) waitFor { hasSubtext(finishFlag) }
    }
  }
}

class RdPortForwardingPanelWidget(data: ComponentData) : UiComponent(data) {
  val forwardedPortSuggestionDropDown: UiComponent
    get() = x { byJavaClass("com.jetbrains.thinclient.portForwarding.ui.ForwardedPortSuggestionDropDown") }

  val forwardedPortDropDown: UiComponent
    get() = x { byJavaClass("com.jetbrains.thinclient.portForwarding.ui.ForwardedPortDropDownLink") }
}

@Suppress("InjectedReferences")
@Remote("com.intellij.terminal.frontend.view.impl.TerminalViewImpl\$TerminalPanel", plugin = "org.jetbrains.plugins.terminal/intellij.terminal.frontend")
interface TerminalViewImpl {
  fun getActiveOutputModel() : TerminalOutputModel
}

@Remote("org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel")
interface TerminalOutputModel {
  val cursorOffset: TerminalOffset
}

@Remote("org.jetbrains.plugins.terminal.block.reworked.TerminalOffset",  plugin = "org.jetbrains.plugins.terminal")
interface TerminalOffset {
  fun toAbsolute(): Long
}