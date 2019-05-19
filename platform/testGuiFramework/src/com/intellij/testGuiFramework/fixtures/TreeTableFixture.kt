// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.driver.ExtendedJTreePathFinder
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.step
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.tree.TreeUtil
import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot
import org.fest.swing.driver.ComponentPreconditions
import org.fest.swing.driver.JTreeLocation
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.LocationUnavailableException
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JTable

class TreeTableFixture(robot: Robot, target: TreeTable) :
  ComponentFixture<TreeTableFixture, TreeTable>(TreeTableFixture::class.java, robot, target) {

  private fun getRow(vararg pathStrings: String): Int {
    return step("get row for path [${pathStrings.joinToString()}]") {
      val tree = target().tree
      val path = ExtendedJTreePathFinder(tree).findMatchingPath(pathStrings.toList())
      return@step GuiTestUtilKt.computeOnEdt { tree.getRowForPath(path) }!!
    }
  }

  fun getCheckboxState(vararg pathStrings: String): Boolean {
    return step("get checkbox state, path [${pathStrings.joinToString()}]") {
      val row = getRow(*pathStrings)
      val value = GuiTestUtilKt.computeOnEdt { target().model.getValueAt(row, 0) }
      logInfo("getCheckboxState: found $value")
      return@step value as Boolean
    }
  }

  fun expandTopLevel(){
    TreeUtil.expand(target().tree, 1)
  }

  fun isPathPresent(vararg pathStrings: String): Boolean {
    return try {
      val tree = target().tree
      ExtendedJTreePathFinder(tree).findMatchingPath(pathStrings.toList())
      true
    }
    catch (notFound: LocationUnavailableException) {
      false
    }
    catch (notFound: ComponentLookupException) {
      false
    }
  }

  fun clickColumn(column: Int, vararg pathStrings: String) {
    step("click at column #$column with path ${pathStrings.joinToString(prefix = "[", postfix = "]")}") {
      ComponentPreconditions.checkEnabledAndShowing(target())

      val tree = target().tree
      val path = ExtendedJTreePathFinder(tree).findMatchingPath(pathStrings.toList())
      val clickPoint = GuiTestUtilKt.computeOnEdt {
        var x = target().location.x + (0 until column).sumBy { target().columnModel.getColumn(it).width }
        x += target().columnModel.getColumn(column).width / 3
        val y = JTreeLocation().pathBoundsAndCoordinates(tree, path).second.y
        Point(x, y)
      }!!

      val visibleHeight = target().visibleRect.height

      target().scrollRectToVisible(Rectangle(Point(0, clickPoint.y + visibleHeight / 2), Dimension(0, 0)))

      robot().click(target(), clickPoint, MouseButton.LEFT_BUTTON, 1)
    }
  }
}

fun JTable.printModel() {
  val myColCount = columnCount
  val myRowCount = rowCount
  for (r in 0 until myRowCount) {
    print("$r: ")
    for (c in 0 until myColCount) {
      val value = GuiTestUtilKt.computeOnEdt { model.getValueAt(r, c) }
      print("($c) $value, ")
    }
    println()
  }
}