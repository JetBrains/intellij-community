package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.StringTable
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import javax.swing.JTable

fun Finder.table(@Language("xpath") xpath: String? = null) =
  x(xpath ?: xQuery { byType(JTable::class.java) }, JTableUiComponent::class.java)

fun Finder.table(init: QueryBuilder.() -> String) = x(JTableUiComponent::class.java, init)

open class JTableUiComponent(data: ComponentData) : UiComponent(data) {
  private val fixture by lazy { driver.new(JTableFixtureRef::class, robot, component) }

  // content()[ROW][COLUMN]
  fun content(): Map<Int, Map<Int, String>> = fixture.collectItems()
  fun rowCount(): Int = fixture.rowCount()
  fun selectionValue(): String = fixture.selectionValue()
  fun clickCell(row: Int, column: Int) = fixture.clickCell(row, column)
  fun rightClickCell(row: Int, column: Int) = fixture.rightClickCell(row, column)
  fun doubleClickCell(row: Int, column: Int) = fixture.doubleClickCell(row, column)
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JTableTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JTableFixtureRef {
  fun collectItems(): StringTable
  fun rowCount(): Int
  fun selectionValue(): String
  fun clickCell(row: Int, column: Int)
  fun rightClickCell(row: Int, column: Int)
  fun doubleClickCell(row: Int, column: Int)
}