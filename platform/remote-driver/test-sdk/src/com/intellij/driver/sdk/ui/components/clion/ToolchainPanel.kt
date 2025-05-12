package com.intellij.driver.sdk.ui.components.clion

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.*
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery
import java.awt.event.KeyEvent

fun Finder.toolchainPanel(action: ToolchainPanel.() -> Unit = {}) = x(ToolchainPanel::class.java) { byClass("DialogRootPane") }.apply(action)

class ToolchainPanel(data: ComponentData) : SettingsDialogUiComponent(data) {
  fun ToolchainPanel.getToolchainField(name: String): JTextFieldUI =
    textField("//div[@accessiblename='$name:' and @class='ExtendableTextField']")

  fun addNewToolchain(toolchain: Toolchain) {
    if (toolchain.name == ToolchainNames.DEFAULT) {
      jBlist(xQuery { byClass("JBList") }).clickItem(toolchain.name.toString())
    }
    else {
      actionButtonByXpath(xQuery { byAttribute("myicon", "add.svg") }).click()
      popupMenu().select(toolchain.toString())
      while (!jBlist(xQuery { byClass("JBList") }).items.first().contains("$toolchain (default)")) {
        moveToolchainUp()
      }
    }
  }

  fun moveToolchainUp() {
    actionButtonByXpath(xQuery { byTooltip("Up") }).click()
  }

  fun moveToolchainDown() {
    actionButtonByXpath(xQuery { byTooltip("Down") }).click()
  }

  fun setupToolchains(toolchain: Toolchain) {
    if (toolchain.buildTool != Make.DEFAULT) {
      getToolchainField("Build Tool").text = toolchain.buildTool.getMakePath()
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
}