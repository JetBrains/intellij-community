package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.elements.JLabelUiComponent

class TerminalToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  val terminalEditor: JEditorUiComponent = editor()
  val terminalTabs: List<JLabelUiComponent> get() = xx(JLabelUiComponent::class.java) { byType(TAB_CLASS) }.list()
  val newTabButton: UiComponent = x { byAccessibleName("New Tab") }

  fun terminalTab(name: String): JLabelUiComponent = x(JLabelUiComponent::class.java) { byType(TAB_CLASS) and byAccessibleName(name) }

  fun clickNewTab() {
    toolWindowHeader.moveMouse()
    newTabButton.click()
  }

  companion object {
    private const val TAB_CLASS = "com.intellij.openapi.wm.impl.content.ContentTabLabel"
  }
}
