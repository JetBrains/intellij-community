package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.currentIdeFrame
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language


fun Finder.terminal(@Language("xpath") xpath: String = "//div[contains(@class, 'TerminalToolWindow') or contains(@class, 'ConsoleTerminalWidget')]"): TerminalDialogUI =
  x(xpath, TerminalDialogUI::class.java)

fun Finder.terminal(action: TerminalDialogUI.() -> Unit) {
  x("//div[contains(@class, 'TerminalToolWindow') or contains(@class, 'ConsoleTerminalWidget')]", TerminalDialogUI::class.java).action()
}

fun Finder.terminalPanel(@Language("xpath") xpath: String = "//div[contains(@class, 'TerminalPanel')]"): TerminalPanelUi =
  x(xpath, TerminalPanelUi::class.java)

fun Finder.terminalPanel(action: TerminalPanelUi.() -> Unit) {
  x("//div[contains(@class, 'TerminalPanel')]", TerminalPanelUi::class.java).action()
}

fun Driver.terminal(action: TerminalDialogUI.() -> Unit) {
  this.ui.terminal(action)
}

fun Driver.terminalPanel(action: TerminalPanelUi.() -> Unit) {
  this.ui.terminalPanel(action)
}

class TerminalPanelUi(data: ComponentData) : UiComponent(data) {
  val terminalView: TerminalViewImpl by lazy { driver.cast(component, TerminalViewImpl::class) }
}

class TerminalDialogUI(data: ComponentData) : UiComponent(data) {
  init {
    if (notPresent()) {
      driver.ui.currentIdeFrame().leftToolWindowToolbar.terminalButton.click()
    }
    waitFound()
  }

  val rdPortForwardingPanelWidget: RdPortForwardingPanelWidget
    get() = x(RdPortForwardingPanelWidget::class.java) { byJavaClass("com.intellij.terminal.frontend.view.portForwarding.PortForwardingWidget") }

  fun execute(command: String, finishFlag: String = "") {
    this.click()
    keyboard {
      typeText(command)
      if (finishFlag.isNotEmpty()) waitFor { hasSubtext(finishFlag) }
    }
  }
}

/**
 * The reworked terminal port-forwarding panel
 * (`com.intellij.terminal.frontend.view.portForwarding.PortForwardingWidget`).
 *
 * Each detected port is shown as a single `com.intellij.ui.components.DropDownLink`:
 * - When the port is not forwarded, the link shows the remote port and opens the "forward" actions;
 * - When forwarded, the link shows the local port and opens the "stop / open in browser" actions
 *   (next to a non-clickable remote-port link and a "forwarded to" label).
 *
 * The forwarded VS not-forwarded state is therefore distinguished by the panel's text (the "forwarded to" label).
 */
class RdPortForwardingPanelWidget(data: ComponentData) : UiComponent(data) {
  /** The drop-down link of the detected port. The same class in both forwarded and not-forwarded states. */
  val portDropDownLink: UiComponent
    get() = x { byJavaClass("com.intellij.ui.components.DropDownLink") }
}

@Suppress("InjectedReferences")
@Remote("com.intellij.terminal.frontend.view.impl.TerminalViewImpl\$TerminalPanel", plugin = "org.jetbrains.plugins.terminal/intellij.terminal.frontend")
interface TerminalViewImpl {
  fun getActiveOutputModel() : TerminalOutputModel
}

@Remote("org.jetbrains.plugins.terminal.view.TerminalOutputModel")
interface TerminalOutputModel {
  val cursorOffset: TerminalOffset
}

@Remote("org.jetbrains.plugins.terminal.view.TerminalOffset",  plugin = "org.jetbrains.plugins.terminal")
interface TerminalOffset {
  fun toAbsolute(): Long
}