package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleTable
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun IdeaFrameUI.dataViewToolWindow(action: DataViewToolWindowUi.() -> Unit = {}): DataViewToolWindowUi =
  x(DataViewToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Data View")) }.apply(action)

class DataViewToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {
  /**
   * Waits until the toolbar keeps the same 'More Actions' button for 2 seconds.
   * The toolbar replaces its buttons after the table data arrives, and a click on the replaced button is lost.
   */
  override fun waitReady(timeout: Duration) {
    super.waitReady(timeout)
    if (!actionButton { byAccessibleName("More Actions") }.present()) return
    var previous: Int? = null
    var sameReadings = 0
    waitFor("The Data View toolbar keeps its 'More Actions' button", timeout = timeout, interval = 1.seconds) {
      // A new lookup each time: the hash code of a found component is the identity of the Swing component.
      val current = runCatching {
        actionButton { byAccessibleName("More Actions") }.component.takeIf { it.isShowing() }?.hashCode()
      }.getOrNull()
      sameReadings = if (current != null && current == previous) sameReadings + 1 else 0
      previous = current
      sameReadings >= 2
    }
  }

  val chartViewButton: ActionButtonUi = actionButton { byAccessibleName("Chart View") }
  val tableViewButton: ActionButtonUi = actionButton { byAccessibleName("Table View") }
  val moreActionsButton: ActionButtonUi = actionButton { byAccessibleName("More Actions") }
  // Older builds use TableResultView; byJavaClass matches the main GridTable and skips the pinned strip, a subclass.
  val tableView: JTableUiComponent = accessibleTable {
    or(byType("com.intellij.database.run.ui.table.TableResultView"), byJavaClass("com.intellij.database.run.ui.table.GridTable"))
  }

  /** Waits for a tab named [name]. Each 'View as' click opens one more tab, so several tabs can have the same name. */
  fun waitForTab(name: String, timeout: Duration = 30.seconds) {
    waitFor("The '$name' tab is visible in Data View", timeout = timeout) {
      xx("//div[@text='$name']").list().isNotEmpty()
    }
  }
}
