package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

class DatabaseToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  /** The Refresh action in the Database view toolbar. It stays disabled while the IDE cannot connect to the selected data source. */
  val refreshButton: UiComponent get() = x("//div[@myicon='refresh.svg']")
}
