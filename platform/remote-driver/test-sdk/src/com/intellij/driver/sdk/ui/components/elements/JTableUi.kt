package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.model.StringTable
import com.intellij.driver.sdk.ui.CellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import javax.swing.JTable

fun Finder.table(@Language("xpath") xpath: String? = null) =
  x(xpath ?: xQuery { byType(JTable::class.java) }, JTableUiComponent::class.java)

fun Finder.table(init: QueryBuilder.() -> String) = x(JTableUiComponent::class.java, init)

open class JTableUiComponent(data: ComponentData) : UiComponent(data) {
  protected val fixture by lazy { driver.new(JTableFixtureRef::class, robot, component) }
  protected val tableComponent by lazy { driver.cast(component, JTableComponent::class) }

  // content()[ROW][COLUMN]
  fun content(): Map<Int, Map<Int, String>> = fixture.collectItems()
  fun rowCount(): Int = fixture.rowCount()
  fun selectionValue(): String = fixture.selectionValue()
  fun clickCell(row: Int, column: Int) = fixture.clickCell(row, column)
  fun rightClickCell(row: Int, column: Int) = fixture.rightClickCell(row, column)
  fun doubleClickCell(row: Int, column: Int) = fixture.doubleClickCell(row, column)
  fun replaceCellRendererReader(reader: CellRendererReader) = fixture.replaceCellRendererReader(reader)
  fun isRowSelected(row: Int): Boolean = tableComponent.isRowSelected(row)
  fun getSelectedRow(): Int = tableComponent.getSelectedRow()
  fun getComponentAt(row: Int, column: Int): Component = fixture.getComponentAt(row, column)
  fun getValueAt(row: Int, column: Int): String? = content()[row]?.get(column)
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JTableTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JTableFixtureRef {
  fun collectItems(): StringTable
  fun rowCount(): Int
  fun selectionValue(): String
  fun clickCell(row: Int, column: Int)
  fun rightClickCell(row: Int, column: Int)
  fun doubleClickCell(row: Int, column: Int)
  fun replaceCellRendererReader(reader: CellRendererReader)
  fun getComponentAt(row: Int, column: Int): Component
}

@Remote("javax.swing.JTable")
interface JTableComponent {
  fun isRowSelected(row: Int): Boolean
  fun getSelectedRow(): Int
}