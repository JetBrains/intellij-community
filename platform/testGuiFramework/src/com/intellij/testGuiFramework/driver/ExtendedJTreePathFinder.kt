// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.driver

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.util.FinderPredicate
import com.intellij.testGuiFramework.util.Predicate
import com.intellij.ui.LoadingNode
import org.fest.swing.cell.JTreeCellReader
import org.fest.swing.exception.LocationUnavailableException
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class ExtendedJTreePathFinder(val jTree: JTree) {

  private val cellReader: JTreeCellReader = ExtendedJTreeCellReader()

  fun findMatchingPath(pathStrings: List<String>): TreePath =
    findMatchingPathByPredicate(Predicate.equality, pathStrings)

  fun findMatchingPathWithVersion(pathStrings: List<String>): TreePath =
    findMatchingPathByPredicate(Predicate.withVersion, pathStrings)

  // this is ex-XPath version
  fun findMatchingPathByPredicate(predicate: FinderPredicate, pathStrings: List<String>): TreePath {
    val model = jTree.model
    if (jTree.isRootVisible) {
      val childValue = jTree.value(model.root) ?: ""
      if (predicate(pathStrings[0], childValue)) {
        if (pathStrings.size == 1) return TreePath(arrayOf<Any>(model.root))
        return traverseChildren(jTree, model.root, TreePath(model.root), predicate, pathStrings.drop(1)) ?: throw pathNotFound(pathStrings)
      }
      else {
        pathNotFound(pathStrings)
      }
    }
    return traverseChildren(jTree, model.root, TreePath(model.root), predicate, pathStrings) ?: throw pathNotFound(pathStrings)
  }

  fun exists(pathStrings: List<String>) =
    existsByPredicate(Predicate.equality, pathStrings)

  fun existsWithVersion(pathStrings: List<String>) =
    existsByPredicate(Predicate.withVersion, pathStrings)

  fun existsByPredicate(predicate: FinderPredicate, pathStrings: List<String>): Boolean {
    return try {
      findMatchingPathByPredicate(
        pathStrings = pathStrings,
        predicate = predicate
      )
      true
    }
    catch (e: Exception) {
      false
    }
  }

  fun traverseChildren(jTree: JTree,
                       node: Any,
                       pathTree: TreePath,
                       predicate: FinderPredicate,
                       pathStrings: List<String>): TreePath? {
    val childCount = jTree.model.getChildCount(node)

    val order = pathStrings[0].getOrder() ?: 0
    val original = pathStrings[0].getWithoutOrder()
    var currentOrder = 0

    for (childIndex in 0 until childCount) {
      val child = jTree.model.getChild(node, childIndex)
      if (child is LoadingNode)
        throw ExtendedJTreeDriver.LoadingNodeException(node = child, treePath = jTree.getPathToNode(node))
      val childValue = jTree.value(child) ?: continue
      if (predicate(original, childValue)) {
        if (currentOrder == order) {
          val newPath = TreePath(arrayOf<Any>(*pathTree.path, child))
          return if (pathStrings.size == 1) {
            newPath
          }
          else {
            traverseChildren(jTree, child, newPath, predicate, pathStrings.subList(1, pathStrings.size))
          }
        }
        else {
          currentOrder++
        }
      }
    }
    return null
  }

  private fun JTree.getPathToNode(node: Any): TreePath {
    val treeModel = model as DefaultTreeModel
    var path = treeModel.getPathToRoot(node as TreeNode)
    if (!isRootVisible) path = path.sliceArray(1 until path.size)
    return TreePath(path)
  }

  private fun JTree.value(modelValue: Any): String? {
    return cellReader.valueAt(this, modelValue)?.eraseZeroSpaceSymbols()
  }

  private fun String.eraseZeroSpaceSymbols(): String = replace("\u200B", "")

  // function to work with order - so with lines like `abc(0)`
  private val orderPattern = Regex("\\(\\d+\\)")

  private fun String.getWithoutOrder(): String =
    if (this.hasOrder())
      this.dropLast(2 + this.getOrder().toString().length)
    else this

  private fun String.hasOrder(): Boolean =
    orderPattern.find(this)?.value?.isNotEmpty() ?: false

  // TODO: may be to throw an exception instead of returning null?
  private fun String.getOrder(): Int? {
    val find: MatchResult = orderPattern.find(this) ?: return null
    return find.value.removeSurrounding("(", ")").toInt()
  }

  // exception wrappers
  private fun pathNotFound(path: List<String>): LocationUnavailableException {
    throw LocationUnavailableException("Unable to find path \"$path\"")
  }

  private fun multipleMatchingNodes(pathString: String, parentText: Any): LocationUnavailableException {
    throw LocationUnavailableException(
      "There is more than one node with value '$pathString' under \"$parentText\"")
  }

  fun findPathToNode(node: String) = findPathToNodeByPredicate(node, Predicate.equality)

  fun findPathToNodeWithVersion(node: String) = findPathToNodeByPredicate(node, Predicate.withVersion)

  fun findPathToNodeByPredicate(node: String, predicate: FinderPredicate): TreePath {
    //    expandNodes()
    //    Pause.pause(1000) //Wait for EDT thread to finish expanding
    val result: MutableList<String> = mutableListOf()
    var currentNode = jTree.model.root as DefaultMutableTreeNode
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
    return findMatchingPathByPredicate(predicate, result)
  }
}