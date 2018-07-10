  // Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures.extended

import com.intellij.testGuiFramework.driver.ExtendedJTreeDriver
import com.intellij.testGuiFramework.driver.ExtendedJTreePathFinder
import com.intellij.testGuiFramework.driver.FinderPredicate
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import org.fest.swing.core.MouseButton
import org.fest.swing.core.MouseClickInfo
import org.fest.swing.core.Robot
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.fixture.JTreeFixture
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.tree.TreePath

open class ExtendedJTreePathFixture(
  val tree: JTree,
  private val stringPath: List<String>,
  private val predicate: FinderPredicate = ExtendedJTreePathFinder.predicateEquality,
  robot: Robot = GuiRobotHolder.robot,
  private val myDriver: ExtendedJTreeDriver = ExtendedJTreeDriver(robot)
) : JTreeFixture(robot, tree) {

  constructor(
    tree: JTree,
    path: TreePath,
    predicate: FinderPredicate = ExtendedJTreePathFinder.predicateEquality,
    robot: Robot = GuiRobotHolder.robot,
    driver: ExtendedJTreeDriver = ExtendedJTreeDriver(robot)
  ) :
    this(tree, path.path.map { it.toString() }.toList(), predicate, robot, driver)

  init {
    replaceDriverWith(myDriver)
  }

  private val cachePaths = mutableMapOf<List<String>, TreePath>()

  protected val path: TreePath
    get() {
      return if (!cachePaths.containsKey(stringPath))
        expandAndGetPathStepByStep(stringPath)
      else
        cachePaths.getValue(stringPath)
    }

  /**
   * Create a new object of ExtendedJTreePathFixture for a new path
   * It's supposed the new path in the same tree
   * @param pathStrings full new path
   * @throws LocationUnavailableException if path not found
   * */
  fun path(vararg pathStrings: String): ExtendedJTreePathFixture {
    return ExtendedJTreePathFixture(tree, pathStrings.toList(), predicate, robot(), myDriver)
  }

  /**
   * Create a new object of ExtendedJTreePathFixture for a path
   * containing a specified [node]
   * It's supposed the new path in the same tree
   * @param node one node value somewhere whithin the same tree
   * @throws LocationUnavailableException if node not found
   * TODO complete
   * */
  fun pathToNode(node: String): ExtendedJTreePathFixture{
    val newPath = myDriver.findPathToNode(tree, node, predicate)
    return ExtendedJTreePathFixture(tree, newPath, predicate, robot(), myDriver)
  }

  fun hasPath(vararg pathStrings: String): Boolean =
    ExtendedJTreePathFixture(tree, pathStrings.toList(), predicate, robot(), myDriver).hasPath()

  fun hasPath(): Boolean {
    return try {
      path
      true
    }
    catch (e: Exception) {
      false
    }
  }

  fun clickPath(mouseClickInfo: MouseClickInfo): Unit = myDriver.clickPath(tree, path, mouseClickInfo)

  fun clickPath(button: MouseButton = MouseButton.LEFT_BUTTON, times: Int = 1): Unit =
    myDriver.clickPath(tree, path, button, times)

  fun doubleClickPath(): Unit = myDriver.doubleClickPath(tree, path)

  fun rightClickPath(): Unit = myDriver.rightClickPath(tree, path)

  fun expandPath() {
    myDriver.expandPath(tree, path)
  }

  protected fun expandAndGetPathStepByStep(stringPath: List<String>): TreePath {
    fun <T> List<T>.list2tree() = map { subList(0, indexOf(it) + 1) }
    if (!cachePaths.containsKey(stringPath)){
      var partialPath: TreePath? = null
      for (partialList in stringPath.list2tree()) {
        GuiTestUtil.pause(condition = "wait to find a correct path to click", timeoutSeconds = 2L) {
          try {
            partialPath = ExtendedJTreePathFinder(tree)
              .findMatchingPathByPredicate(predicate = predicate, pathStrings = *partialList.toTypedArray())
            partialPath != null
          }
          catch (e: Exception) {
            false
          }
        }
        cachePaths[partialList] = partialPath!!
        myDriver.expandPath(tree, cachePaths.getValue(partialList))
      }
    }
    return cachePaths.getValue(stringPath)
  }

  fun collapsePath(): Unit = myDriver.collapsePath(tree, path)

  fun selectPath(): Unit = myDriver.selectPath(tree, path)

  //  fun scrollToPath(): Point = myDriver.scrollToPath(tree, path)
  fun openPopupMenu(): JPopupMenu = myDriver.showPopupMenu(tree, path)

  fun drag(): Unit = myDriver.drag(tree, path)

  fun drop(): Unit = myDriver.drop(tree, path)

  fun getPathStrings(): List<String> = path.path.map { it.toString() }
}