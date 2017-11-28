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
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JTableFixture
import javax.swing.JCheckBox
import javax.swing.JTable

class ExtendedTableFixture(val myRobot: Robot, val myTable: JTable) : JTableFixture(myRobot, myTable) {

  init {
    replaceCellReader(ExtendedJTableCellReader())
  }

  fun row(i: Int): RowFixture = RowFixture(i, this)
  fun row(value: String): RowFixture = RowFixture(cell(value).row(), this)


}

class RowFixture(val rowNumber: Int, val tableFixture: ExtendedTableFixture) {

  val myTable = tableFixture.myTable

  fun hasCheck(): Boolean =
    (0..myTable.columnCount - 1)
      .map { myTable.prepareRenderer(myTable.getCellRenderer(rowNumber, it), rowNumber, it) }
      .any { it is JCheckBox }


  fun isCheck(): Boolean {
    val checkBox = getCheckBox()
    return checkBox.isSelected

  }

  fun check() {
    val checkBox = getCheckBox()
    return checkBox.model.setSelected(true)
  }

  fun uncheck() {
    val checkBox = getCheckBox()
    return checkBox.model.setSelected(false)
  }

  fun values(): List<String> {
    val cellReader = ExtendedJTableCellReader()
    return (0..myTable.columnCount - 1)
      .map { cellReader.valueAt(myTable, rowNumber, it) ?: "null" }
  }

  private fun getCheckBox(): JCheckBox {
    if (!hasCheck()) throw Exception("Unable to find checkbox cell in row: $rowNumber")
    return (0..myTable.columnCount - 1)
      .map { myTable.prepareRenderer(myTable.getCellRenderer(rowNumber, it), rowNumber, it) }
      .find { it is JCheckBox } as JCheckBox
  }

}