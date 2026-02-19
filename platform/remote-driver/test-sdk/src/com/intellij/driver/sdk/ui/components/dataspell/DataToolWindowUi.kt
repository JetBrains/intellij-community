package com.intellij.driver.sdk.ui.components.dataspell

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent

/**
 * DataSpell: "Data" tool window component.
 *
 * Pattern follows other tool window components like ProjectView/Debug.
 * Keep locators conservative and rely on accessible names/types to reduce brittleness.
 */
class DataToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {
  val dataToolwindowHeader = x(DataToolWindowHeaderUiComponent::class.java) { byType("com.intellij.toolWindow.ToolWindowHeader") }

  class DataToolWindowHeaderUiComponent(data: ComponentData): UiComponent(data) {
    val plusButton = x { byAccessibleName("Add Data") }
    val optionsButton = x { byAccessibleName("Options") }
    val hideButton = x { byAccessibleName("Hide") }
  }
}

/**
 * Entry point: obtain the Data tool window by name.
 * Falls back to a stricter locator if name matching changes in future.
 */
fun IdeaFrameUI.dataToolWindow(action: DataToolWindowUi.() -> Unit = {}): DataToolWindowUi =
  x(DataToolWindowUi::class.java) { byAccessibleName("Data Tool Window") }.apply(action)
