// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.driver

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.cellReader.ProjectTreeCellReader
import com.intellij.testGuiFramework.cellReader.SettingsTreeCellReader
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.util.FinderPredicate
import com.intellij.testGuiFramework.util.Predicate
import com.intellij.testGuiFramework.util.step
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.tree.TreeUtil
import org.fest.assertions.Assertions
import org.fest.reflect.core.Reflection
import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot
import org.fest.swing.driver.ComponentPreconditions
import org.fest.swing.driver.JTreeDriver
import org.fest.swing.exception.ActionFailedException
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Timeout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JTree
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.TreePath

open class ExtendedJTreeDriver(robot: Robot = GuiRobotHolder.robot) : JTreeDriver(robot) {
  private val DEFAULT_FIND_PATH_ATTEMPTS: Int = 3

  protected data class PathInfo(val expandPoint: Point, val clickPoint: Point, val toggleClickCount: Int, val bounds: Rectangle)

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
    step("click path '${treePath.path.joinToString()}' in tree") {
      val pathInfo = tree.scrollToPath(treePath)
      robot.click(tree, pathInfo.clickPoint, button, times)
      //check that path is selected or click it again
      if (!tree.checkPathIsSelected(treePath)) {
        if (attempts == 0)
          throw ExtendedJTreeException("Unable to click path in $DEFAULT_FIND_PATH_ATTEMPTS " +
                                       "attempts due to it high mutability. Maybe this path is loading async.")
        clickPath(tree, treePath, button, times, attempts - 1)
      }
    }
  }

  protected fun JTree.scrollToPath(path: TreePath) : PathInfo {
      robot.waitForIdle()
      val result = GuiTestUtilKt.computeOnEdt {
        ComponentPreconditions.checkEnabledAndShowing(this)
        val pathInfo = this.getPathInfo(path)
        scrollRectToVisible(pathInfo.bounds)
        pathInfo
      }!!
      robot.waitForIdle()
    return result
  }

  private fun JTree.isInnerExpandControl(): Boolean = this is SimpleTree || this is TreeTable

  private fun JTree.getExpandCoord(path: TreePath): Int {
    val bounds = this.getPathBounds(path)

    return if (isInnerExpandControl()) {
      // expand/collapse symbol is located inside path bounds
      bounds.x + 1
    }
    else {
      // in other trees the expand/collapse symbol is located out of the path bounds
      // so we have to expand the bounds to the left
      val expandControlRange = TreeUtil.getExpandControlRange(this, path)
      when {
        expandControlRange != null -> expandControlRange.from + (expandControlRange.to - expandControlRange.from) / 2
        bounds.x < bounds.height / 2 -> x + 8
        else -> bounds.x - bounds.height / 2
      }
    }
  }

  private fun JTree.refineToggleClickCount(): Int = if (isInnerExpandControl()) {
    // expand/collapse symbol is located inside path bounds
    // so the way how to expand/collapse node is controlled by the tree
    toggleClickCount
  }
  else {
    // in other trees the expand/collapse symbol is located out of the path bounds
    // we manually expand/collapse the node
    1
  }

  // to be overridden by CheckboxTree to take into account size of the checkbox control
  protected open fun getLabelXCoord(jTree: JTree, path: TreePath): Int = jTree.getPathBounds(path).x + 1

  private fun JTree.getScrollBounds(path: TreePath): Rectangle {
    val bounds = this.getPathBounds(path)
    return if (isInnerExpandControl()) {
      bounds
    }
    else {
      val expandControlRange = TreeUtil.getExpandControlRange(this, path)
      Rectangle(expandControlRange?.from ?: x, bounds.y, bounds.width, bounds.height)
    }
  }

  private fun JTree.getPathInfo(path: TreePath): PathInfo {
    val bounds = this.getPathBounds(path)
    val clickY = bounds.y + bounds.height / 2
    return PathInfo(
      expandPoint = Point(getExpandCoord(path), clickY),
      clickPoint = Point(getLabelXCoord(this, path), clickY),
      toggleClickCount = refineToggleClickCount(),
      bounds = getScrollBounds(path)
    )
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
    try {
      GuiTestUtilKt.waitUntil("Waiting for children are shown up",
        Timeout.timeout(robot.settings().timeoutToBeVisible().toLong())) { this.childCount(path) != 0 }
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
    if (GuiTestUtilKt.computeOnEdt { tree.model.isLeaf(treePath.lastPathComponent) } != false) return
    val info = tree.scrollToPath(treePath)
    if (tree.isExpanded(treePath).not()) tree.toggleCell(info.expandPoint, info.toggleClickCount)
  }

  fun collapsePath(tree: JTree, treePath: TreePath) {
    // do not try to collapse leaf
    if (GuiTestUtilKt.computeOnEdt { tree.model.isLeaf(treePath.lastPathComponent) } != false) return
    val info = tree.scrollToPath(treePath)
    if (tree.isExpanded(treePath)) tree.toggleCell(info.expandPoint, info.toggleClickCount)
  }

  fun selectPath(tree: JTree, treePath: TreePath) {
    val pathInfo = tree.scrollToPath(treePath)
    val isSelected = GuiTestUtilKt.computeOnEdt {
      tree.selectionCount == 1 && tree.isPathSelected(treePath)
    } ?: false
    robot.waitForIdle()
    if (isSelected.not()) robot.click(tree, pathInfo.clickPoint)
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

  fun findPath(tree: JTree, stringPath: List<String>, predicate: FinderPredicate = Predicate.equality): TreePath {
    fun <T> List<T>.list2tree() = map { subList(0, indexOf(it) + 1) }
    lateinit var path: TreePath
    stringPath
      .list2tree()
      .forEach {
        path = ExtendedJTreePathFinder(tree).findMatchingPathByPredicate(predicate, it)
        expandPath(tree, path)
      }
    return path
  }

  fun findPathToNode(tree: JTree, node: String, predicate: FinderPredicate = Predicate.equality): TreePath {

    fun JTree.iterateChildren(root: Any, node: String, rootPath: TreePath, predicate: FinderPredicate): TreePath? {
      for (index in 0 until (GuiTestUtilKt.computeOnEdt { this.model.getChildCount(root) } ?: 0)) {
        val child = GuiTestUtilKt.computeOnEdt { this.model.getChild(root, index) }!!
        val childPath = TreePath(arrayOf(*rootPath.path, child))
        if (predicate(child.toString(), node)) {
          return childPath
        }
        if (GuiTestUtilKt.computeOnEdt { this.model.isLeaf(child) } == false) {
          makeVisible(childPath, true)
          val found = this.iterateChildren(child, node, childPath, predicate)
          if (found != null) return found
        }
      }
      return null
    }

    val root = GuiTestUtilKt.computeOnEdt { tree.model.root } ?: throw IllegalStateException("root is null")
    return tree.iterateChildren(root, node, TreePath(root), predicate)
           ?: throw LocationUnavailableException("Node `$node` not found")
  }

  fun exists(tree: JTree, pathStrings: List<String>, predicate: FinderPredicate = Predicate.equality): Boolean {
    return try {
      findPath(tree, pathStrings, predicate)
      true
    }
    catch (e: LocationUnavailableException) {
      false
    }
  }
} // end of class


class ExtendedJTreeException(message: String) : Exception(message)
