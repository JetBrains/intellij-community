// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.driver

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.cellReader.ProjectTreeCellReader
import com.intellij.testGuiFramework.cellReader.SettingsTreeCellReader
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import org.fest.assertions.Assertions
import org.fest.reflect.core.Reflection
import org.fest.swing.core.MouseButton
import org.fest.swing.core.MouseClickInfo
import org.fest.swing.core.Robot
import org.fest.swing.driver.ComponentPreconditions
import org.fest.swing.driver.JTreeDriver
import org.fest.swing.driver.JTreeLocation
import org.fest.swing.exception.ActionFailedException
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.util.Pair
import org.fest.swing.util.Triple
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

open class ExtendedJTreeDriver(robot: Robot = GuiRobotHolder.robot) : JTreeDriver(robot) {
  private val DEFAULT_FIND_PATH_ATTEMPTS: Int = 3

  init {
    val resultReader = when (javaClass.name) {
      "com.intellij.openapi.options.newEditor.SettingsTreeView\$MyTree" -> SettingsTreeCellReader()
      "com.intellij.ide.projectView.impl.ProjectViewPane\$1" -> ProjectTreeCellReader()
      else -> ExtendedJTreeCellReader()
    }
    this.replaceCellReader(resultReader)
  }

  fun clickPath(tree: JTree,
                treePath: TreePath,
                button: MouseButton = MouseButton.LEFT_BUTTON,
                times: Int = 1,
                attempts: Int = DEFAULT_FIND_PATH_ATTEMPTS) {
    val point = tree.scrollToPath(treePath)
    robot.click(tree, point, button, times)
    //check that path is selected or click it again
    if (!tree.checkPathIsSelected(treePath)) {
      if (attempts == 0)
        throw ExtendedJTreeException("Unable to click path in $DEFAULT_FIND_PATH_ATTEMPTS " +
                                     "attempts due to it high mutability. Maybe this path is loading async.")
      clickPath(tree, treePath, button, times, attempts - 1)
    }
  }

  fun JTree.scrollToPath(path: TreePath): Point {
    robot.waitForIdle()
    return this.scrollToMatchingPath(path).second
  }

  private fun JTree.scrollToMatchingPath(path: TreePath): Pair<Boolean, Point> {
    this.makeVisible(path, false)
    return this.scrollToPathToSelectExt(path)
  }

  private fun JTree.scrollToPathToSelectExt(path: TreePath): Pair<Boolean, Point> {
    val result =  GuiTestUtilKt.computeOnEdt {
      val isSelected = this.selectionCount == 1 && this.isPathSelected(path)
      Pair.of(isSelected, this.scrollToTreePathExt(path))
    }!!
    robot.waitForIdle()
    return result
  }

  private fun JTree.scrollToTreePathExt(path: TreePath): Point {
    val boundsAndCoordinates = JTreeLocation().pathBoundsAndCoordinates(this, path)
    this.scrollRectToVisible(boundsAndCoordinates.first as Rectangle)
    return boundsAndCoordinates.second!!
  }

  private fun JTree.makeVisible(path: TreePath, expandWhenFound: Boolean): Boolean {
    var changed = false
    if (path.pathCount > 1) {
      changed = makeParentVisible(path)
    }

    return if (!expandWhenFound) {
      changed
    }
    else {
      expandTreePath(path)
      waitForChildrenToShowUp(path)
      true
    }
  }

  private fun JTree.makeParentVisible(path: TreePath): Boolean {
    val changed = this.makeVisible(path.parentPath, true)
    if (changed) robot.waitForIdle()
    return changed
  }

  private fun JTree.expandTreePath(path: TreePath) {
    GuiTestUtilKt.runOnEdt {
      if (!isExpanded(path)) expandPath(path)
    }
  }

  private fun JTree.waitForChildrenToShowUp(path: TreePath) {
    val timeoutInSeconds = robot.settings().timeoutToBeVisible() * 1000 // convert ms to s
    try {
      GuiTestUtilKt.waitUntil("Waiting for children are shown up", timeoutInSeconds) { this.childCount(path) != 0 }
    }
    catch (waitTimedOutError: WaitTimedOutError) {
      throw LocationUnavailableException(waitTimedOutError.message!!)
    }
  }

  /**
   * @return true if the required path is selected
   * @return false if the incorrect path is selected
   * @throws ExtendedJTreeException if no one row or several ones are selected
   * */
  private fun JTree.checkPathIsSelected(treePath: TreePath): Boolean {
    val selectedPaths = selectionPaths
    if (selectedPaths.isEmpty()) throw ExtendedJTreeException("No one row has been selected at all")
    if (selectedPaths.size > 1) throw ExtendedJTreeException("More than one row has been selected")
    val selectedPath = selectedPaths.first()
    return treePath.lastPathComponent == selectedPath.lastPathComponent
  }

  /**
   * node that has as child LoadingNode
   */
  class LoadingNodeException(val node: Any, var treePath: TreePath?) :
    Exception("Meet loading node: $node (${treePath?.path?.joinToString()}")

  private fun JTree.childCount(path: TreePath): Int {
    return GuiTestUtilKt.computeOnEdt {
      val lastPathComponent = path.lastPathComponent
      model.getChildCount(lastPathComponent)
    }!!
  }

  fun expandPath(tree: JTree, treePath: TreePath) {
    // do not try to expand leaf
    if(tree.model.isLeaf(treePath.lastPathComponent)) return
    val info = tree.scrollToMatchingPathAndGetToggleInfo(treePath)
    if (!info.first) tree.toggleCell(info.second!!, info.third)
  }

  fun collapsePath(tree: JTree, treePath: TreePath) {
    // do not try to collapse leaf
    if(tree.model.isLeaf(treePath.lastPathComponent)) return
    val info = tree.scrollToMatchingPathAndGetToggleInfo(treePath)
    if (info.first) tree.toggleCell(info.second!!, info.third)
  }

  fun selectPath(tree: JTree, treePath: TreePath) {
    selectMatchingPath(tree, treePath)
  }

  private fun selectMatchingPath(tree: JTree, treePath: TreePath): Point {
    val info = tree.scrollToMatchingPath(treePath)
    robot.waitForIdle()
    val where = info.second!!
    if (!info.first) robot.click(tree, where)
    return where
  }

  private fun JTree.toggleCell(p: Point, toggleClickCount: Int) {
    if (toggleClickCount == 0) {
      toggleRowThroughTreeUIExt(p)
      robot.waitForIdle()
    }
    else {
      robot.click(this, p, MouseButton.LEFT_BUTTON, toggleClickCount)
    }
  }

  private fun JTree.toggleRowThroughTreeUIExt(p: Point) {
    GuiTestUtilKt.runOnEdt {
      if (ui !is BasicTreeUI)
        throw ActionFailedException.actionFailure("Can't toggle row for $ui")
      else
        this.toggleExpandState(p)
    }
  }

  private fun JTree.toggleExpandState(pathLocation: Point) {
    val path = getPathForLocation(pathLocation.x, pathLocation.y)
    val treeUI = ui
    Assertions.assertThat(treeUI).isInstanceOf(BasicTreeUI::class.java)
    Reflection.method("toggleExpandState").withParameterTypes(TreePath::class.java).`in`(treeUI).invoke(path)
  }

  private fun JTree.scrollToMatchingPathAndGetToggleInfo(treePath: TreePath): Triple<Boolean, Point, Int> {
    val result = GuiTestUtilKt.computeOnEdt {
      ComponentPreconditions.checkEnabledAndShowing(this)
      val point = scrollToTreePathExt(treePath)
      Triple.of(isExpanded(treePath), point, toggleClickCount)
    }!!
    robot.waitForIdle()
    return result
  }

  fun showPopupMenu(tree: JTree, treePath: TreePath): JPopupMenu {
    val info = tree.scrollToMatchingPath(treePath)
    robot.waitForIdle()
    return robot.showPopupMenu(tree, info.second!!)
  }

  fun drag(tree: JTree, treePath: TreePath) {
    val p = selectMatchingPath(tree, treePath)
    drag(tree, p)
  }

  fun drop(tree: JTree, treePath: TreePath) {
    drop(tree, tree.scrollToMatchingPath(treePath).second!!)
  }

  fun findPath(tree: JTree, stringPath: List<String>, predicate: FinderPredicate = ExtendedJTreePathFinder.predicateEquality): TreePath {
    fun <T> List<T>.list2tree() = map { subList(0, indexOf(it) + 1) }
    lateinit var path: TreePath
    stringPath
      .list2tree()
      .forEach {
        path = ExtendedJTreePathFinder(tree).findMatchingPathByPredicate(predicate, *it.toTypedArray())
        expandPath(tree, path)
      }
    return path
  }

  fun findPathToNode(tree: JTree, node: String, predicate: FinderPredicate = ExtendedJTreePathFinder.predicateEquality): TreePath {
    val result: MutableList<String> = mutableListOf()
    var currentNode = tree.model.root as DefaultMutableTreeNode
    val e = currentNode.preorderEnumeration()
    while (e.hasMoreElements()) {
      currentNode = e.nextElement() as DefaultMutableTreeNode
      if (predicate(currentNode.toString(), node)) {
        break
      }
    }
    result.add(0, currentNode.toString())
    while (currentNode.parent != null) {
      currentNode = currentNode.parent as DefaultMutableTreeNode
      result.add(0, currentNode.toString())
    }
    return findPath(tree, result, predicate)
  }

  fun exists(tree: JTree, pathStrings: List<String>, predicate: FinderPredicate = ExtendedJTreePathFinder.predicateEquality): Boolean {
    return try {
      findPath(tree, pathStrings, predicate)
      true
    }
    catch (e: LocationUnavailableException){
      false
    }
  }
} // end of class


class ExtendedJTreeException(message: String) : Exception(message)
