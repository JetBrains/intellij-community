package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleTable
import com.intellij.driver.sdk.ui.components.elements.actionButton

fun IdeaFrameUI.dataViewToolWindow(action: DataViewToolWindowUi.() -> Unit = {}): DataViewToolWindowUi =
  x(DataViewToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Data View")) }.apply(action)

class DataViewToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {
  val chartViewButton: ActionButtonUi = actionButton { byAccessibleName("Chart View") }
  val tableViewButton: ActionButtonUi = actionButton { byAccessibleName("Table View") }
  val moreActionsButton: ActionButtonUi = actionButton { byAccessibleName("More Actions") }
  val tableView: JTableUiComponent = accessibleTable { byType("com.intellij.database.run.ui.table.TableResultView") }
}
