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
      writeTextField("Build Tool:", toolchain.buildTool.getMakePath())
    }
    writeTextField("C Compiler:", toolchain.compiler.getCCompilerPath())
    writeTextField("C++ Compiler:", toolchain.compiler.getCppCompilerPath())

    if (toolchain !is Toolchain.Default) {
      setDebugger(toolchain.debugger.getDebuggerPath())
    }
  }

  private fun setDebugger(debugger: String) {
    textField(xQuery { and(byAccessibleName("Debugger:"), byClass("ExtendableTextField")) }).click()
    keyboard { key(KeyEvent.VK_DOWN) }
    driver.ui.popup("//div[@class='CustomComboPopup']").waitFound().list().clickItem(debugger)
  }

  private fun writeTextField(accessibleName: String, text: String) {
    textField(xQuery { and(byAccessibleName(accessibleName), byClass("ExtendableTextField")) }).text = text
  }
}