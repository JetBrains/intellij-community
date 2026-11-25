package com.intellij.driver.sdk.database

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent

fun IdeaFrameUI.databaseToolWindow(action: DatabaseToolWindowUi.() -> Unit = {}): DatabaseToolWindowUi =
  x(DatabaseToolWindowUi::class.java) { byAccessibleName("Database Tool Window") }.apply(action)

class DatabaseToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  /** The Refresh action in the Database view toolbar. It stays disabled while the IDE cannot connect to the selected data source. */
  val refreshButton: UiComponent get() = x("//div[@myicon='refresh.svg']")
}
