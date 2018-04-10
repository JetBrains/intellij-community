// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.driver

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.ui.LoadingNode
import org.fest.swing.cell.JTreeCellReader
import org.fest.swing.exception.LocationUnavailableException
import org.fest.util.Lists
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class ExtendedJTreePathFinder {

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
        catch (e: ExtendedJTreeDriver.LoadingNodeException) {      //if we met loading node let's tell it to caller and probably expand path to clarify this node
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
      if (child is LoadingNode) throw ExtendedJTreeDriver.LoadingNodeException(child,
                                                                               null)
      if (pathString == value(tree, child)) {
        if (match != null) throw multipleMatchingNodes(pathString, value(tree, node))
        match = child
      }
    }

    return match
  }


  fun findMatchingPath(tree: JTree, pathStrings: List<String>, isUniquePath: Boolean = true): TreePath {
    if (isUniquePath) return findMatchingPath(tree, pathStrings)

    //remove node order if path is a not unique
    val pathStringsWithoutOrder = if (isUniquePath) pathStrings else pathStrings.map { it.getWithoutOrder() }

    val model = tree.model
    if (tree.isRootVisible) {
      if (pathStringsWithoutOrder[0] != value(tree, model.root)) throw pathNotFound(pathStringsWithoutOrder)
      if (pathStringsWithoutOrder.size == 1) return TreePath(arrayOf<Any>(model.root))
      val result: TreePath = findMatchingPath(tree, model.root,
                                                               pathStringsWithoutOrder.subList(1, pathStringsWithoutOrder.size)) ?: throw pathNotFound(
        pathStringsWithoutOrder)
      return TreePath(arrayOf<Any>(model.root, *result.path))
    }
    else {
      return findMatchingPath(tree, model.root, pathStringsWithoutOrder) ?: throw pathNotFound(pathStringsWithoutOrder)
    }
  }

  /**
   * this method tries to find any path. If tree contains multiple of searchable path it still accepts.
   */
  private fun findMatchingPath(tree: JTree, node: Any, pathStrings: List<String>): TreePath? {
    val model = tree.model
    val childCount = model.getChildCount(node)

    for (childIndex in 0..childCount - 1) {
      val child = model.getChild(node, childIndex)
      if (child is LoadingNode) throw ExtendedJTreeDriver.LoadingNodeException(child,
                                                                               getPathToNode(
                                                                                                                                      tree,
                                                                                                                                      node))
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
    val original = getWithoutOrder(xPathStrings[0], order)
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

  private fun getWithoutOrder(potentiallyOrderedNode: String, order: Int): CharSequence {
    return if (potentiallyOrderedNode.hasOrder()) potentiallyOrderedNode.subSequence(0,
                                                                                     potentiallyOrderedNode.length - 2 - (order.toString().length))
    else potentiallyOrderedNode
  }

  private fun String.getWithoutOrder(): String = getWithoutOrder(this, this.getOrder() ?: 0).toString()

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
    throw LocationUnavailableException(
      "There is more than one node with value '$pathString' under \"$parentText\"")
  }

  private fun value(tree: JTree, modelValue: Any): String {
    return eraseZeroSpaceSymbols(cellReader!!.valueAt(tree, modelValue)!!)
  }

  private fun eraseZeroSpaceSymbols(string: String): String = string.replace("\u200B", "")

  fun replaceCellReader(newCellReader: JTreeCellReader) {
    cellReader = newCellReader
  }

  fun cellReader(): JTreeCellReader {
    return cellReader!!
  }
}