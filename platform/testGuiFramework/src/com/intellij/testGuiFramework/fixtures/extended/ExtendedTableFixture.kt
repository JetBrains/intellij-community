/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.fixtures.extended

import com.intellij.testGuiFramework.cellReader.ExtendedJTableCellReader
import com.intellij.testGuiFramework.fixtures.CheckBoxFixture
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JTableFixture
import javax.swing.JCheckBox
import javax.swing.JTable

class ExtendedTableFixture(private val myRobot: Robot, val myTable: JTable) : JTableFixture(myRobot, myTable) {

  init {
    replaceCellReader(ExtendedJTableCellReader())
  }

  fun row(i: Int): RowFixture = RowFixture(myRobot, i, this)
  fun row(value: String): RowFixture = RowFixture(myRobot, cell(value).row(), this)
}

class RowFixture(private val myRobot: Robot, val rowNumber: Int, val tableFixture: ExtendedTableFixture) {

  val myTable: JTable = tableFixture.myTable

  fun hasCheck(): Boolean =
    (0 until myTable.columnCount)
      .map { myTable.prepareRenderer(myTable.getCellRenderer(rowNumber, it), rowNumber, it) }
      .any { it is JCheckBox }

  fun isCheck(): Boolean = getCheckBox().isSelected

  fun check() {
    val checkBox = getCheckBox()
    if (!checkBox.isSelected) CheckBoxFixture(myRobot, checkBox).click()
  }

  fun uncheck() {
    val checkBox = getCheckBox()
    if (checkBox.isSelected) CheckBoxFixture(myRobot, checkBox).click()
  }

  fun values(): List<String> {
    val cellReader = ExtendedJTableCellReader()
    return (0 until myTable.columnCount)
      .map { cellReader.valueAt(myTable, rowNumber, it) ?: "null" }
  }

  private fun getCheckBox(): JCheckBox {
    if (!hasCheck()) throw Exception("Unable to find checkbox cell in row: $rowNumber")
    return (0 until myTable.columnCount)
      .map { myTable.prepareRenderer(myTable.getCellRenderer(rowNumber, it), rowNumber, it) }
      .find { it is JCheckBox } as JCheckBox
  }

}