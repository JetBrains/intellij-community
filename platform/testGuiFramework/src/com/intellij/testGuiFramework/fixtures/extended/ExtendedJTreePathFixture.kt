// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures.extended

import com.intellij.testGuiFramework.driver.ExtendedJTreeDriver
import com.intellij.testGuiFramework.driver.ExtendedJTreePathFinder
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import org.fest.swing.core.MouseButton
import org.fest.swing.core.MouseClickInfo
import org.fest.swing.core.Robot
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.fixture.JTreeFixture
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

open class ExtendedJTreePathFixture(
  val tree: JTree,
  val path: TreePath,
  robot: Robot = GuiRobotHolder.robot,
  private val myDriver: ExtendedJTreeDriver = ExtendedJTreeDriver(robot)
) : JTreeFixture(robot, tree) {

  init {
    replaceDriverWith(myDriver)
  }

  /**
   * Create a new object of ExtendedJTreePathFixture for a new path
   * It's supposed the new path in the same tree
   * @param pathStrings full new path
   * @throws LocationUnavailableException if path not found
   * */
  fun path(vararg pathStrings: String): ExtendedJTreePathFixture {
    val newPath = ExtendedJTreePathFinder(tree).findMatchingPath(*pathStrings)
    return ExtendedJTreePathFixture(tree, newPath, robot(), myDriver)
  }

  /**
   * Create a new object of ExtendedJTreePathFixture for a path
   * containing a specified [node]
   * It's supposed the new path in the same tree
   * @param node one node value somewhere whithin the same tree
   * @throws LocationUnavailableException if node not found
   * */
  fun pathToNode(node: String): ExtendedJTreePathFixture{
    val newPath = ExtendedJTreePathFinder(tree).findPathToNode(node)
    return ExtendedJTreePathFixture(tree, newPath, robot(), myDriver)
  }

  fun hasPath(vararg pathStrings: String):Boolean = ExtendedJTreePathFinder(tree).exists(*pathStrings)

  fun clickPath(mouseClickInfo: MouseClickInfo): Unit = myDriver.clickPath(tree, path, mouseClickInfo)

  fun clickPath(button: MouseButton = MouseButton.LEFT_BUTTON, times: Int = 1): Unit =
    myDriver.clickPath(tree, path, button, times)

  fun doubleClickPath(): Unit = myDriver.doubleClickPath(tree, path)

  fun rightClickPath(): Unit = myDriver.rightClickPath(tree, path)

  fun expandPath(): Unit = myDriver.expandPath(tree, path)

  fun collapsePath(): Unit = myDriver.collapsePath(tree, path)

  fun selectPath(): Unit = myDriver.selectPath(tree, path)

  //  fun scrollToPath(): Point = myDriver.scrollToPath(tree, path)
  fun openPopupMenu(): JPopupMenu = myDriver.showPopupMenu(tree, path)

  fun drag(): Unit = myDriver.drag(tree, path)

  fun drop(): Unit = myDriver.drop(tree, path)

  fun getPathStrings(): List<String> = myDriver.getPathStrings(tree, path)
}