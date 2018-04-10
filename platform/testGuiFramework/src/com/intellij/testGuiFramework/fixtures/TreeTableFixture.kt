// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.driver.ExtendedJTreeDriver
import com.intellij.testGuiFramework.driver.ExtendedJTreePathFinder
import com.intellij.ui.treeStructure.treetable.TreeTable
import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot
import org.fest.swing.driver.ComponentPreconditions
import org.fest.swing.driver.JTreeLocation
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

class TreeTableFixture(val robot: Robot, val target: TreeTable) :
  ComponentFixture<TreeTableFixture, TreeTable>(TreeTableFixture::class.java, robot, target) {

  @Suppress("unused")
  fun clickColumn(column: Int, vararg pathStrings: String) {
    ComponentPreconditions.checkEnabledAndShowing(target)

    val tree = target.tree
    val pathWithoutRoot = ExtendedJTreePathFinder().findMatchingPath(tree, pathStrings.asList())
    val path = ExtendedJTreeDriver.addRootIfInvisible(tree, pathWithoutRoot)

    var x = target.location.x + (0 until column).sumBy { target.columnModel.getColumn(it).width }
    x += target.columnModel.getColumn(column).width / 3
    val y = JTreeLocation().pathBoundsAndCoordinates(tree, path).second.y

    val visibleHeight = target.visibleRect.height
    target.scrollRectToVisible(Rectangle(Point(0, y + visibleHeight / 2), Dimension(0, 0)))

    robot.click(target, Point(x, y), MouseButton.LEFT_BUTTON, 1)
  }
}