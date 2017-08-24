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
package com.intellij.testGuiFramework.driver

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.computeOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.computeOnEdtWithTry
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.runOnEdt
import com.intellij.ui.LoadingNode
import org.fest.assertions.Assertions
import org.fest.reflect.core.Reflection
import org.fest.swing.cell.JTreeCellReader
import org.fest.swing.core.MouseButton
import org.fest.swing.core.MouseClickInfo
import org.fest.swing.core.Robot
import org.fest.swing.driver.ComponentPreconditions
import org.fest.swing.driver.JTreeDriver
import org.fest.swing.driver.JTreeLocation
import org.fest.swing.exception.ActionFailedException
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.util.Pair
import org.fest.swing.util.Triple
import org.fest.util.Lists
import org.fest.util.Preconditions
import java.awt.Point
import java.awt.Rectangle
import javax.annotation.Nonnull
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * To avoid confusions of parsing path from one String let's accept only splitted path into array of strings
 */
open class ExtendedJTreeDriver(robot: Robot) : JTreeDriver(robot) {

  private val pathFinder = ExtendedJTreePathFinder()
  private val location = JTreeLocation()


  fun clickPath(tree: JTree, pathStrings: List<String>, mouseClickInfo: MouseClickInfo)
    = clickPath(tree, pathStrings, mouseClickInfo.button(), mouseClickInfo.times())

  fun clickPath(tree: JTree, pathStrings: List<String>, button: MouseButton = MouseButton.LEFT_BUTTON, times: Int = 1) {
    val point = scrollToPath(tree, pathStrings)
    robot.click(tree, point, button, times)
  }

  //XPath contains order number of similar nodes: clickXPath("test(1)", "Java") - here (1) means the first node
  fun clickXPath(tree: JTree, xPathStrings: List<String>, button: MouseButton = MouseButton.LEFT_BUTTON, times: Int = 1) {
    val point = scrollToXPath(tree, xPathStrings)
    robot.click(tree, point, button, times)
  }

  fun checkPathExists(tree: JTree, pathStrings: List<String>) {
    matchingPathFor(tree = tree, pathStrings = pathStrings, isUniquePath = false)
  }

  fun nodeValue(tree: JTree, pathStrings: List<String>): String? = nodeText(tree, pathStrings)

  fun doubleClickPath(tree: JTree, pathStrings: List<String>) {
    val p = scrollToPath(tree, pathStrings)
    doubleClick(tree, p)
  }

  fun rightClickPath(tree: JTree, pathStrings: List<String>) {
    val p = scrollToPath(tree, pathStrings)
    rightClick(tree, p)
  }

  fun expandPath(tree: JTree, pathStrings: List<String>) {
    val info = scrollToMatchingPathAndGetToggleInfo(tree, pathStrings)
    if (!info.first) toggleCell(tree, info.second!!, info.third)
  }

  fun collapsePath(tree: JTree, pathStrings: List<String>) {
    val info = scrollToMatchingPathAndGetToggleInfo(tree, pathStrings)
    if (info.first) toggleCell(tree, info.second!!, info.third)
  }

  fun selectPath(tree: JTree, pathStrings: List<String>) {
    selectMatchingPath(tree, pathStrings)
  }

  fun scrollToPath(tree: JTree, pathStrings: List<String>): Point {
    robot.waitForIdle()
    return scrollToMatchingPath(tree, pathStrings).third!!
  }

  fun scrollToXPath(tree: JTree, xPathStrings: List<String>): Point {
    robot.waitForIdle()
    return scrollToMatchingXPath(tree, xPathStrings).third!!
  }

  fun showPopupMenu(tree: JTree, pathStrings: List<String>): JPopupMenu {
    val info = scrollToMatchingPath(tree, pathStrings)
    robot.waitForIdle()
    return robot.showPopupMenu(tree, info.third!!)
  }

  fun drag(tree: JTree, pathStrings: List<String>) {
    val p = selectMatchingPath(tree, pathStrings)
    drag(tree, p)
  }

  fun drop(tree: JTree, pathStrings: List<String>) {
    drop(tree, scrollToMatchingPath(tree, pathStrings).third!!)
  }


  ///OVERRIDDEN FUNCTIONS/////
  override fun clickPath(tree: JTree, path: String) = clickPath(tree, listOf(path))

  override fun clickPath(tree: JTree, path: String, button: MouseButton) = clickPath(tree, listOf(path), button)
  override fun clickPath(tree: JTree, path: String, mouseClickInfo: MouseClickInfo) = clickPath(tree, listOf(path), mouseClickInfo)
  override fun checkPathExists(tree: JTree, path: String) {
    matchingPathFor(tree, listOf(path))
  }

  override fun nodeValue(tree: JTree, path: String): String? = nodeValue(tree, listOf(path))
  override fun nodeValue(tree: JTree, row: Int): String? = nodeText(tree, row, location)
  override fun doubleClickPath(tree: JTree, path: String) = doubleClickPath(tree, listOf(path))
  override fun rightClickPath(tree: JTree, path: String) = rightClickPath(tree, listOf(path))
  override fun expandPath(tree: JTree, path: String) = expandPath(tree, listOf(path))
  override fun collapsePath(tree: JTree, path: String) = collapsePath(tree, listOf(path))
  override fun selectPath(tree: JTree, path: String) = selectPath(tree, listOf(path))
  override fun showPopupMenu(tree: JTree, path: String) = showPopupMenu(tree, listOf(path))
  override fun drag(tree: JTree, path: String) = drag(tree, listOf(path))
  override fun drop(tree: JTree, path: String) = drop(tree, listOf(path))
  ///--------------------/////


  ///PRIVATE FUNCTIONS/////

  private fun doubleClick(tree: JTree, p: Point) {
    robot.click(tree, p, MouseButton.LEFT_BUTTON, 2)
  }

  private fun rightClick(@Nonnull tree: JTree, @Nonnull p: Point) {
    robot.click(tree, p, MouseButton.RIGHT_BUTTON, 1)
  }

  private fun scrollToMatchingPath(tree: JTree, pathStrings: List<String>): Triple<TreePath, Boolean, Point> {
    val matchingPath = verifyJTreeIsReadyAndFindMatchingPath(tree, pathStrings)
    makeVisible(tree, matchingPath, false)
    val info = scrollToPathToSelect(tree, matchingPath, location)
    return Triple.of(matchingPath, info.first, info.second)
  }

  private fun scrollToMatchingXPath(tree: JTree, xPathStrings: List<String>): Triple<TreePath, Boolean, Point> {
    val matchingPath = verifyJTreeIsReadyAndFindMatchingXPath(tree, xPathStrings)
    makeVisible(tree, matchingPath, false)
    val info = scrollToPathToSelect(tree, matchingPath, location)
    return Triple.of(matchingPath, info.first, info.second)
  }

  private fun scrollToPathToSelect(@Nonnull tree: JTree, @Nonnull path: TreePath, @Nonnull location: JTreeLocation): Pair<Boolean, Point> {
    return computeOnEdt {
      val isSelected = tree.selectionCount == 1 && tree.isPathSelected(path)
      Pair.of(isSelected, scrollToTreePath(tree, path, location))
    }!!
  }

  private fun scrollToTreePath(tree: JTree, path: TreePath, location: JTreeLocation): Point {
    val boundsAndCoordinates = location.pathBoundsAndCoordinates(tree, path)
    tree.scrollRectToVisible(boundsAndCoordinates.first as Rectangle)
    return boundsAndCoordinates.second!!
  }

  private fun scrollToMatchingPathAndGetToggleInfo(tree: JTree,
                                                   pathStrings: List<String>): Triple<Boolean, Point, Int> {
    return computeOnEdt {
      ComponentPreconditions.checkEnabledAndShowing(tree)
      val matchingPath = matchingPathFor(tree, pathStrings)
      val point = scrollToTreePath(tree, matchingPath, location)
      Triple.of(tree.isExpanded(matchingPath), point, tree.toggleClickCount)
    }!!
  }

  private fun makeVisible(tree: JTree, path: TreePath, expandWhenFound: Boolean): Boolean {
    var changed = false
    if (path.pathCount > 1) {
      changed = makeParentVisible(tree, path)
    }

    if (!expandWhenFound) {
      return changed
    }
    else {
      expandTreePath(tree, path)
      waitForChildrenToShowUp(tree, path)
      return true
    }
  }

  private fun waitForChildrenToShowUp(@Nonnull tree: JTree, @Nonnull path: TreePath) {
    val timeout = robot.settings().timeoutToBeVisible().toLong()

    try {
      pause(timeout) { childCount(tree, path) != 0 }
    }
    catch (waitTimedOutError: WaitTimedOutError) {
      throw LocationUnavailableException(waitTimedOutError.message!!)
    }

  }

  private fun makeParentVisible(tree: JTree, path: TreePath): Boolean {
    val changed = makeVisible(tree, Preconditions.checkNotNull(path.parentPath), true)
    if (changed) robot.waitForIdle()
    return changed
  }

  internal class ExtendedJTreePathFinder {

    private var cellReader: JTreeCellReader? = null

    init {
      this.replaceCellReader(ExtendedJTreeCellReader())
    }

    fun findMatchingPath(tree: JTree, pathStrings: List<String>): TreePath {

      val model = tree.model
      val newPathValues = Lists.newArrayList<Any>()
      var node: Any = model.root
      val pathElementCount = pathStrings.size

      for (stringIndex in 0..pathElementCount - 1) {
        val pathString = pathStrings[stringIndex]
        if (stringIndex == 0 && tree.isRootVisible) {
          if (pathString != value(tree, node)) throw pathNotFound(pathStrings)
          newPathValues.add(node)
        }
        else {
          try {
            node = traverseChildren(tree, node, pathString) ?: throw pathNotFound(pathStrings)
          }
          catch(e: LoadingNodeException) {      //if we met loading node let's tell it to caller and probably expand path to clarify this node
            e.treePath = TreePath(newPathValues.toTypedArray())
            throw e
          }
          newPathValues.add(node)
        }
      }
      return TreePath(newPathValues.toTypedArray())
    }

    private fun traverseChildren(tree: JTree,
                                 node: Any,
                                 pathString: String): Any? {
      var match: Any? = null
      val model = tree.model
      val childCount = model.getChildCount(node)

      for (childIndex in 0..childCount - 1) {
        val child = model.getChild(node, childIndex)
        if (child is LoadingNode) throw LoadingNodeException(child, null)
        if (pathString == value(tree, child)) {
          if (match != null) throw multipleMatchingNodes(pathString, value(tree, node))
          match = child
        }
      }

      return match
    }


    fun findMatchingPath(tree: JTree, pathStrings: List<String>, isUniquePath: Boolean = true): TreePath {
      if (isUniquePath) return findMatchingPath(tree, pathStrings)

      val model = tree.model
      if (tree.isRootVisible) {
        if (pathStrings[0] != value(tree, model.root)) throw pathNotFound(pathStrings)
        if (pathStrings.size == 1) return TreePath(arrayOf<Any>(model.root))
        val result: TreePath = findMatchingPath(tree, model.root, pathStrings.subList(1, pathStrings.size)) ?: throw pathNotFound(
          pathStrings)
        return TreePath(arrayOf<Any>(model.root, *result.path))
      }
      else {
        return findMatchingPath(tree, model.root, pathStrings) ?: throw pathNotFound(pathStrings)
      }
    }

    /**
     * this method tries to find any path. If tree contains multiple of searchable path it still accepts
     */
    private fun findMatchingPath(tree: JTree, node: Any, pathStrings: List<String>): TreePath? {
      val model = tree.model
      val childCount = model.getChildCount(node)

      for (childIndex in 0..childCount - 1) {
        val child = model.getChild(node, childIndex)
        if (child is LoadingNode) throw LoadingNodeException(child, getPathToNode(tree, node))
        if (pathStrings.size == 1 && value(tree, child) == pathStrings[0]) {

          return TreePath(arrayOf<Any>(child))
        }
        else {
          if (pathStrings[0] == value(tree, child)) {
            val childResult = findMatchingPath(tree, child, pathStrings.subList(1, pathStrings.size))
            if (childResult != null) return TreePath(arrayOf<Any>(child, *childResult.path))
          }
        }
      }
      return null
    }

    fun getPathToNode(tree: JTree, node: Any): TreePath {
      val treeModel = tree.model as DefaultTreeModel
      var path = treeModel.getPathToRoot(node as TreeNode)
      if (!tree.isRootVisible) path = path.sliceArray(1..path.size - 1)
      return TreePath(path)
    }

    fun findMatchingXPath(tree: JTree, xPathStrings: List<String>): TreePath {
      val model = tree.model
      if (tree.isRootVisible) {
        if (xPathStrings[0] != value(tree, model.root)) throw pathNotFound(xPathStrings)
        if (xPathStrings.size == 1) return TreePath(arrayOf<Any>(model.root))

        val result: TreePath = findMatchingXPath(tree, model.root, xPathStrings.subList(1, xPathStrings.size)) ?: throw pathNotFound(
          xPathStrings)
        return TreePath(arrayOf<Any>(model.root, *result.path))
      }
      else {
        return findMatchingXPath(tree, model.root, xPathStrings) ?: throw pathNotFound(xPathStrings)
      }
    }

    private fun findMatchingXPath(tree: JTree, node: Any, xPathStrings: List<String>): TreePath? {
      val model = tree.model
      val childCount = model.getChildCount(node)

      val order = xPathStrings[0].getOrder() ?: 0
      val original = if (xPathStrings[0].hasOrder()) xPathStrings[0].subSequence(0,
                                                                                 xPathStrings[0].length - 2 - (order.toString().length))
      else xPathStrings[0]
      var currentOrder = 0

      for (childIndex in 0..childCount - 1) {
        val child = model.getChild(node, childIndex)
        if (original == value(tree, child)) {
          if (currentOrder == order) {
            if (xPathStrings.size == 1) {
              return TreePath(arrayOf<Any>(child))
            }
            else {
              val childResult = findMatchingXPath(tree, child, xPathStrings.subList(1, xPathStrings.size))
              if (childResult != null) return TreePath(arrayOf<Any>(child, *childResult.path))
            }
          }
          else {
            currentOrder++
          }
        }
      }
      return null
    }

    private fun String.hasOrder(): Boolean =
      Regex("\\(\\d\\)").find(this)?.value?.isNotEmpty() ?: false


    private fun String.getOrder(): Int? {
      val find: MatchResult = Regex("\\(\\d\\)").find(this) ?: return null
      return find.value.removeSurrounding("(", ")").toInt()
    }

    private fun pathNotFound(path: List<String>): LocationUnavailableException {
      throw LocationUnavailableException("Unable to find path \"$path\"")
    }

    private fun multipleMatchingNodes(pathString: String, parentText: Any): LocationUnavailableException {
      throw LocationUnavailableException("There is more than one node with value '$pathString' under \"$parentText\"")
    }

    private fun value(tree: JTree, modelValue: Any): String {
      return cellReader!!.valueAt(tree, modelValue)!!
    }

    fun replaceCellReader(newCellReader: JTreeCellReader) {
      cellReader = newCellReader
    }

    fun cellReader(): JTreeCellReader {
      return cellReader!!
    }
  }

  /**
   * node that has as child LoadingNode
   */
  class LoadingNodeException(val node: Any, var treePath: TreePath?) : Exception("Meet loading node: $node")


  private fun childCount(tree: JTree, path: TreePath): Int {
    return computeOnEdt {
      val lastPathComponent = path.lastPathComponent
      tree.model.getChildCount(lastPathComponent)
    }!!
  }

  private fun nodeText(tree: JTree, row: Int, location: JTreeLocation): String? {
    return computeOnEdt {
      val matchingPath = location.pathFor(tree, row)
      pathFinder.cellReader().valueAt(tree, Preconditions.checkNotNull(matchingPath.lastPathComponent))
    }
  }


  private fun nodeText(tree: JTree, pathStrings: List<String>): String? {
    return computeOnEdt {
      val matchingPath = matchingPathWithRootIfInvisible(tree, pathStrings, true)
      pathFinder.cellReader().valueAt(tree, matchingPath.lastPathComponent!!)
    }
  }


  private fun verifyJTreeIsReadyAndFindMatchingPath(tree: JTree, pathStrings: List<String>): TreePath {
    return computeOnEdt {
      ComponentPreconditions.checkEnabledAndShowing(tree)
      matchingPathWithRootIfInvisible(tree, pathStrings, true)
    }!!
  }

  private fun verifyJTreeIsReadyAndFindMatchingXPath(tree: JTree, xPathStrings: List<String>): TreePath {
    return computeOnEdt {
      ComponentPreconditions.checkEnabledAndShowing(tree)
      matchingXPathWithRootIfInvisible(tree, xPathStrings, false)
    }!!
  }


  /**
   * we are trying to find TreePath for a tree, if we met loading node (LoadingTreeNode)
   */
  protected fun matchingPathFor(tree: JTree, pathStrings: List<String>, countDownAttempts: Int = 30, isUniquePath: Boolean = true): TreePath {
    if (countDownAttempts == 0) throw Exception("Unable to find path($pathStrings) for tree: $tree, attempts count exceeded")
    try {
      return computeOnEdtWithTry {
        matchingPathWithRootIfInvisible(tree, pathStrings, isUniquePath)
      }!!
    }
    catch (e: LoadingNodeException) {
      if (e.treePath != null) expandTreePath(tree, e.treePath!!)
      return matchingPathFor(tree, pathStrings, countDownAttempts - 1, isUniquePath)
    }
  }

  private fun matchingPathWithRootIfInvisible(tree: JTree, pathStrings: List<String>, isUniquePath: Boolean): TreePath {
    val matchingPath = pathFinder.findMatchingPath(tree, pathStrings, isUniquePath)
    return addRootIfInvisible(tree, matchingPath)
  }

  private fun matchingXPathWithRootIfInvisible(tree: JTree, xPathStrings: List<String>, isUniquePath: Boolean): TreePath {
    val matchingPath = pathFinder.findMatchingXPath(tree, xPathStrings)
    return addRootIfInvisible(tree, matchingPath)
  }

  private fun addRootIfInvisible(@Nonnull tree: JTree, @Nonnull path: TreePath): TreePath {
    val root = tree.model.root
    if (!tree.isRootVisible && root != null) {
      if (path.pathCount > 0 && root === path.getPathComponent(0)) {
        return path
      }
      else {
        val pathAsArray = path.path
        if (pathAsArray == null) {
          return TreePath(Lists.newArrayList(*arrayOf(root)))
        }
        else {
          val newPath = Lists.newArrayList(*pathAsArray)
          newPath.add(0, root)
          return TreePath(newPath.toTypedArray())
        }
      }
    }
    else {
      return path
    }
  }

  private fun expandTreePath(tree: JTree, path: TreePath) {
    runOnEdt {
      val realPath = addRootIfInvisible(tree, path)
      if (!tree.isExpanded(path)) tree.expandPath(realPath)
    }
  }

  private fun toggleCell(@Nonnull tree: JTree, @Nonnull p: Point, toggleClickCount: Int) {
    if (toggleClickCount == 0) {
      toggleRowThroughTreeUI(tree, p)
      robot.waitForIdle()
    }
    else {
      robot.click(tree, p, MouseButton.LEFT_BUTTON, toggleClickCount)
    }
  }

  private fun toggleRowThroughTreeUI(@Nonnull tree: JTree, @Nonnull p: Point) {
    runOnEdt {
      if (tree.ui !is BasicTreeUI)
        throw ActionFailedException.actionFailure("Can't toggle row for ${tree.ui}")
      else
        toggleExpandState(tree, p)
    }
  }

  private fun toggleExpandState(@Nonnull tree: JTree, @Nonnull pathLocation: Point) {
    val path = tree.getPathForLocation(pathLocation.x, pathLocation.y)
    val treeUI = tree.ui
    Assertions.assertThat(treeUI).isInstanceOf(BasicTreeUI::class.java)
    Reflection.method("toggleExpandState").withParameterTypes(*arrayOf<Class<*>>(TreePath::class.java)).`in`(treeUI).invoke(
      *arrayOf<Any>(path))
  }

  private fun selectMatchingPath(tree: JTree, pathStrings: List<String>): Point {
    val info = scrollToMatchingPath(tree, pathStrings)
    robot.waitForIdle()
    val where = info.third!!
    if (!info.second) robot.click(tree, where)
    return where
  }

  private fun pause(timeout: Long, condition: () -> Boolean) {
    Pause.pause(object : Condition("ExtendedJTreeDriver wait condition:") {
      override fun test() = condition()
    })
  }

}

