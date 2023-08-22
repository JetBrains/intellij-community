package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.StringTable
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import org.intellij.lang.annotations.Language

fun Finder.table(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='JTable']",
                                                               JTableUiComponent::class.java)

class JTableUiComponent(data: ComponentData) : UiComponent(data) {
  private val fixture by lazy {  driver.new(JTableFixtureRef::class, robotService.robot, component) }

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