// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures.extended

import com.intellij.testGuiFramework.driver.ExtJTreeDriver
import com.intellij.testGuiFramework.driver.ExtJTreePathFinder
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import org.fest.swing.core.MouseButton
import org.fest.swing.core.MouseClickInfo
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JTreeFixture
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.tree.TreePath

open class ExtendedJTreePathFixture(
  val tree: JTree,
  val path: TreePath,
  robot: Robot = GuiRobotHolder.robot,
  private val myDriver: ExtJTreeDriver = ExtJTreeDriver(robot)
) : JTreeFixture(robot, tree) {

  init {
    replaceDriverWith(myDriver)
  }

  fun path(vararg pathStrings: String): ExtendedJTreePathFixture {
    val newPath = ExtJTreePathFinder(tree, *pathStrings).findMatchingPath()
    driver()
    return ExtendedJTreePathFixture(tree, newPath, robot(), myDriver)
  }

  fun hasPath() = ExtJTreePathFinder(tree)

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
}