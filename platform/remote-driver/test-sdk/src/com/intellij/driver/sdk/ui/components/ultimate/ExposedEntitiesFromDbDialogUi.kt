package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckboxTreeFixture
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import java.awt.Point
import javax.swing.JCheckBox
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private const val EX_ENTITY_RELATION_TREE_CLASS = "com.intellij.re.ui.bulk.MasterDetailsRelationPanel\$ExEntityRelationTree"

fun IdeaFrameUI.exposedEntitiesFromDbDialog(action: ExposedEntitiesFromDbDialogUi.() -> Unit = {}): ExposedEntitiesFromDbDialogUi =
  x(ExposedEntitiesFromDbDialogUi::class.java) { byTitle("Exposed Entities from DB") }.apply(action)

class ExposedEntitiesFromDbDialogUi(data: ComponentData) : DialogUiComponent(data) {

  val entityTree: JCheckboxTreeFixture get() = x(xQuery { byType(EX_ENTITY_RELATION_TREE_CLASS) }, JCheckboxTreeFixture::class.java)
  val refreshDataSourceButton: UiComponent get() = x(xQuery { byAccessibleName("Refresh IDEA Data Source") })

  fun refreshDataSource() {
    refreshDataSourceButton.click()
    waitForIntrospectionToFinish()
  }

  @OptIn(ManualWaitForIndicators::class)
  private fun waitForIntrospectionToFinish(timeout: Duration = 5.minutes) {
    driver.waitForIndicators(timeout)
  }

  fun waitForTable(tableName: String, timeout: Duration = 2.minutes) {
    waitFor("Table '$tableName' to appear in entity tree", timeout) {
      x(xQuery { byType(EX_ENTITY_RELATION_TREE_CLASS) and contains(byVisibleText(tableName)) }).present()
    }
  }

  fun selectAndCheckTable(tableName: String) {
    entityTree.clickPath("Tables", tableName, fullMatch = false)
    val row = entityTree.findExpandedPath("Tables", tableName, fullMatch = false)?.row
      ?: error("'$tableName' row not found in ExEntityRelationTree")
    entityTree.clickRow(row, Point(10, 0))
    check(isRowChecked(row)) { "$tableName checkbox should be checked in ExEntityRelationTree" }
  }

  fun setCheckboxes(vararg labels: String) {
    labels.forEach { label ->
      checkBox { and(byType(JCheckBox::class.java), byAccessibleName(label)) }.check()
    }
  }

  private fun isRowChecked(row: Int): Boolean =
    entityTree.collectCheckboxes().first { it.row == row }.checkboxState
}
