// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.driver.ExtendedJTreeDriver
import com.intellij.testGuiFramework.driver.ExtendedJTreePathFinder
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.tree.TreeUtil
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
  fun selectPath(vararg pathStrings: String) {
    ComponentPreconditions.checkEnabledAndShowing(target)

    val tree = target.tree
    val pathWithoutRoot = ExtendedJTreePathFinder().findMatchingPath(tree, pathStrings.asList())
    val path = ExtendedJTreeDriver.addRootIfInvisible(tree, pathWithoutRoot)
    val point = JTreeLocation().pathBoundsAndCoordinates(tree, path).second

    val visibleHeight = target.visibleRect.height
    target.scrollRectToVisible(Rectangle(Point(0, point.y + visibleHeight / 2), Dimension(0, 0)))
    robot.click(target, point, MouseButton.LEFT_BUTTON, 1)
  }
}