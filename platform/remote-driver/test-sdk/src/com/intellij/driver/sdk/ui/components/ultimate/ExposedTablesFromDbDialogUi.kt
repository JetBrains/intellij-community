package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckboxTreeFixture
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleTable
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import java.awt.Point
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

private const val EX_ENTITY_RELATION_TREE_CLASS = "com.intellij.re.ui.bulk.MasterDetailsRelationPanel\$ExEntityRelationTree"
private const val DB_COLUMNS_TABLE_CLASS = "com.intellij.re.ui.DbColumnsTable"
private const val COMBOBOX_EDITOR_TEXT_FIELD_CLASS = "com.intellij.ui.ComboboxEditorTextField"

fun Finder.exposedTablesFromDbDialog(action: ExposedTablesFromDbDialogUi.() -> Unit = {}): ExposedTablesFromDbDialogUi =
  x(ExposedTablesFromDbDialogUi::class.java) { byTitle("Exposed Tables from DB") }.apply(action)

class ExposedTablesFromDbDialogUi(data: ComponentData) : DialogUiComponent(data) {

  val entityTree: JCheckboxTreeFixture get() = x(xQuery { byType(EX_ENTITY_RELATION_TREE_CLASS) }, JCheckboxTreeFixture::class.java)
  val refreshDataSourceButton: UiComponent = x(xQuery { byAccessibleName("Refresh IDEA Data Source") })
  val columnsTable: JTableUiComponent get() = x(xQuery { byType(DB_COLUMNS_TABLE_CLASS) }, JTableUiComponent::class.java)
  val packageField: JEditorUiComponent get() = x(xQuery { byType(COMBOBOX_EDITOR_TEXT_FIELD_CLASS) }).editor()
  fun setPackage(packageName: String) {
    packageField.text = packageName
  }

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
    waitFor("$tableName columns to load", 30.seconds) { columnsTable.present() }
    val row = entityTree.findExpandedPath("Tables", tableName, fullMatch = false)?.row
      ?: error("'$tableName' row not found in ExEntityRelationTree")
    entityTree.clickRow(row, Point(10, 0))
    check(isRowChecked(row)) { "$tableName checkbox should be checked in ExEntityRelationTree" }
  }

  fun selectColumnCell(textContains: String) {
    waitFor("DbColumnsTable to stabilize", 10.seconds) { columnsTable.present() }
    accessibleTable { byClass("DbColumnsTable") }.clickCell { it.contains(textContains) }
  }

  private fun isRowChecked(row: Int): Boolean =
    entityTree.collectCheckboxes().first { it.row == row }.checkboxState
}
