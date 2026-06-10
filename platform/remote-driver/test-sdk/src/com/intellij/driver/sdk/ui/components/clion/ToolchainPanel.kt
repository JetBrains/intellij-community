package com.intellij.driver.sdk.ui.components.clion

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.actionButtonByXpath
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.jBlist
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.wait
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.seconds

fun Finder.toolchainPanel(action: ToolchainPanel.() -> Unit = {}) = x(ToolchainPanel::class.java) { byClass("CPPToolchainsPanel") }.apply(action)

class ToolchainPanel(data: ComponentData) : SettingsDialogUiComponent(data) {
  fun ToolchainPanel.getToolchainField(name: String): JTextFieldUI =
    textField("//div[@accessiblename='$name:' and @class='ExtendableTextField']")

  // Toolset TextField for Windows toolchain is missing accessible name
  private fun ToolchainPanel.getToolsetField(): JTextFieldUI =
    textField("//div[@class='JBLabel' and @accessiblename='Toolset:']/following-sibling::div[@class='ToolchainActionItemsComboboxWithBrowseExtension'][1]//div[@class='ExtendableTextField']")

  fun addNewToolchain(toolchain: Toolchain) {
    if (toolchain.name == ToolchainNames.DEFAULT) {
      getToolchainList().clickItem(toolchain.name.toString())
    }
    else {
      addToolchain()
      popupMenu().select(toolchain.toString())
      while (!getToolchainList().items.first().contains("$toolchain (default)")) {
        moveToolchainUp()
      }
    }
  }

  fun getToolchainList(): JListUiComponent =
    jBlist(xQuery { byClass("JBList") })

  fun addToolchain() {
    actionButtonByXpath(xQuery { byTooltip("Add") }).click()
  }

  fun moveToolchainUp() {
    actionButtonByXpath(xQuery { byTooltip("Up") }).click()
  }

  fun removeToolchain() {
    actionButtonByXpath(xQuery { byTooltip("Remove") }).click()
  }

  fun moveToolchainDown() {
    actionButtonByXpath(xQuery { byTooltip("Down") }).click()
  }

  fun setupToolchains(toolchain: Toolchain) {
    if (toolchain.buildTool != BuildTool.DEFAULT) {
      getToolchainField("Build Tool").text = toolchain.buildTool.getPath()
    }
    getToolchainField("C Compiler").text = toolchain.compiler.getCCompilerPath()
    getToolchainField("C++ Compiler").text = toolchain.compiler.getCppCompilerPath()
    if (toolchain !is Toolchain.Default) {
      setDebugger(toolchain.debugger)
    }
  }

  private fun setDebugger(debugger: Debugger) {
    getToolchainField("Debugger").click()
    keyboard { key(KeyEvent.VK_DOWN) }
    driver.ui.popup("//div[@class='CustomComboPopup']").waitFound().list().clickItem(debugger.getDebuggerFieldName())
    if (debugger.name.startsWith("CUSTOM")) {
      getToolchainField("Debugger").text = debugger.getDebuggerPath()
    }
  }

  fun setToolset(path: String) {
    getToolsetField().click()
    keyboard { key(KeyEvent.VK_DOWN) }
    getToolsetField().text = path
    keyboard { enter() }
  }

  fun setupCMake(cmakePath: String) {
    getToolchainField("CMake").click()
    keyboard { key(KeyEvent.VK_DOWN) }
    getToolchainField("CMake").text = cmakePath
    keyboard { enter() }
  }

  fun setupRemoteHost(host: String, username: String, port: String, password: String) {
    actionButtonByXpath(xQuery { byClass("FixedSizeButton") }).click()
    driver.ui.dialog(xQuery { byTitle("SSH Configurations") }) {
      waitFound()
      actionButtonByXpath(xQuery { byAccessibleName("Add") }).click()
      wait(1.seconds)
      textField(xQuery { and(byAccessibleName("Host:"), byClass("JBTextField")) }).text = host
      textField(xQuery { and(byAccessibleName("Username:"), byClass("JBTextField")) }).text = username
      textField(xQuery { and(byAccessibleName("Port:"), byClass("JBTextField")) }).text = port
      textField { and(byAccessibleName("Password:"), byClass("JPasswordField")) }.text = password
      okButton.click()
    }
  }

  fun setUpRemoteToolchainPassword(password: String) {
    actionButtonByXpath(xQuery { byClass("FixedSizeButton") }).click()
    driver.ui.dialog(xQuery { byTitle("SSH Configurations") }) {
      textField { and(byAccessibleName("Password:"), byClass("JPasswordField")) }.text = password
      checkBox(xQuery { byAccessibleName("Save password") }).check()
      okButton.click()
    }
  }
}