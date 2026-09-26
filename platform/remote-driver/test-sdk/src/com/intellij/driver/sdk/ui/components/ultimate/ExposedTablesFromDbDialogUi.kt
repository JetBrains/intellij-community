package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckboxTreeFixture
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleTable
import com.intellij.driver.sdk.waitFor
import java.awt.Point
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

fun Finder.exposedTablesFromDbDialog(action: ExposedTablesFromDbDialogUi.() -> Unit = {}): ExposedTablesFromDbDialogUi =
  x(ExposedTablesFromDbDialogUi::class.java) { byTitle("Exposed Tables from DB") }.apply(action)

class ExposedTablesFromDbDialogUi(data: ComponentData) : DialogUiComponent(data) {

  val entityTree: JCheckboxTreeFixture get() = x("//div[@class='ExEntityRelationTree']", JCheckboxTreeFixture::class.java)
  val refreshDataSourceButton: UiComponent = x("//div[@accessiblename='Refresh IDEA Data Source']")
  val columnsTable: JTableUiComponent get() = x("//div[@class='DbColumnsTable']", JTableUiComponent::class.java)
  val packageField: JEditorUiComponent get() = x("//div[@class='ComboboxEditorTextField']").editor()
  fun setPackage(packageName: String) {
    packageField.text = packageName
  }

  fun refreshDataSource() = refreshDataSourceButton.click()

  fun waitForTable(tableName: String, timeout: Duration = 2.minutes) {
    waitFor("Table '$tableName' to appear in entity tree", timeout) {
      x("//div[@class='ExEntityRelationTree'][contains(@visible_text, '$tableName')]").present()
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
    driver.withContext(OnDispatcher.EDT) {
      driver.cast(entityTree.component, JTreeCheckedRowsRef::class)
        .getPathForRow(row)
        .getLastPathComponent()
        .isChecked()
    }
}

@Remote("javax.swing.JTree")
private interface JTreeCheckedRowsRef {
  fun getPathForRow(row: Int): TreePathRef
}

@Remote("javax.swing.tree.TreePath")
private interface TreePathRef {
  fun getLastPathComponent(): CheckedTreeNodeRef
}

@Remote("com.intellij.ui.CheckedTreeNode")
private interface CheckedTreeNodeRef {
  fun isChecked(): Boolean
}
