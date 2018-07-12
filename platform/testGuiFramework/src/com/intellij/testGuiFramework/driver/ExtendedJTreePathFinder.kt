// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.driver

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.ui.LoadingNode
import org.fest.swing.cell.JTreeCellReader
import org.fest.swing.exception.LocationUnavailableException
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

typealias FinderPredicate = (String, String) -> Boolean

class ExtendedJTreePathFinder(val jTree: JTree) {

  private val cellReader: JTreeCellReader = ExtendedJTreeCellReader()

  fun findMatchingPath(vararg pathStrings: String): TreePath =
    findMatchingPathByPredicate(pathStrings = *pathStrings, predicate = predicateEquality)

  fun findMatchingPathWithVersion(vararg pathStrings: String): TreePath =
    findMatchingPathByPredicate(pathStrings = *pathStrings, predicate = predicateWithVersion)

  // this is ex-XPath version
  fun findMatchingPathByPredicate(vararg pathStrings: String, predicate: FinderPredicate): TreePath {
    val model = jTree.model
    if (jTree.isRootVisible) {
      val childValue = jTree.value(model.root) ?: ""
      if (!predicate(pathStrings[0], childValue)) pathNotFound(*pathStrings)
      if (pathStrings.size == 1) return TreePath(arrayOf<Any>(model.root))

      val result: TreePath = jTree.traverseChildren(
        node = model.root,
        pathStrings = *pathStrings.toList().subList(1, pathStrings.size).toTypedArray(),
        predicate = predicate
      ) ?: throw pathNotFound(*pathStrings)
      return TreePath(arrayOf<Any>(model.root, *result.path))
    }
    else {
      val resultPath = jTree.traverseChildren(
        node = model.root,
        pathStrings = *pathStrings,
        predicate = predicate) ?: throw pathNotFound(*pathStrings)
      return jTree.addRootIfInvisible(resultPath)
    }
  }

  fun exists(vararg pathStrings: String) =
    existsByPredicate(pathStrings = *pathStrings, predicate = predicateEquality)

  fun existsWithVersion(vararg pathStrings: String) =
    existsByPredicate(pathStrings= *pathStrings, predicate = predicateWithVersion)

  fun existsByPredicate(vararg pathStrings: String, predicate: FinderPredicate): Boolean{
    return try{
      findMatchingPathByPredicate(
        pathStrings = *pathStrings,
        predicate = predicate
      )
      true
    }
    catch (e: Exception){
      false
    }
  }

  /* TODO: remove after check
  fun findMatchingPathByPredicate(
    isUniquePath: Boolean = true,
    predicate: FinderPredicate): TreePath {
    if (isUniquePath) return jTree.findUniqueMatchingPath(predicate)

    //remove node order if path is a not unique
    val pathStringsWithoutOrder = if (isUniquePath) pathStrings else pathStrings.map { it.getWithoutOrder() }

    val model = jTree.model
    if (jTree.isRootVisible) {
      if (pathStringsWithoutOrder[0] != jTree.value(model.root)) throw pathNotFound(pathStringsWithoutOrder)
      if (pathStringsWithoutOrder.size == 1) return TreePath(arrayOf<Any>(model.root))
      val result: TreePath = jTree.traverseChildren(model.root,
                                                    pathStringsWithoutOrder.subList(1, pathStringsWithoutOrder.size), predicate)
                             ?: throw pathNotFound(pathStringsWithoutOrder)
      return TreePath(arrayOf<Any>(model.root, *result.path))
    }
    else {
      return jTree.traverseChildren(model.root, pathStringsWithoutOrder, predicate) ?: throw pathNotFound(pathStringsWithoutOrder)
    }
  }
*/
  // JTree extensions
  // this is ex-xPath version
  private fun JTree.traverseChildren(node: Any,
                                     vararg pathStrings: String,
                                     predicate: FinderPredicate): TreePath? {
    val model = model
    val childCount = model.getChildCount(node)

    val order = pathStrings[0].getOrder() ?: 0
    val original = pathStrings[0].getWithoutOrder()
    var currentOrder = 0

    for (childIndex in 0 until childCount) {
      val child = model.getChild(node, childIndex)
      if (child is LoadingNode)
        throw ExtendedJTreeDriver.LoadingNodeException(node = child, treePath = this.getPathToNode(node))
      val childValue = value(child) ?: continue
      if (predicate(original, childValue)) {
        if (currentOrder == order) {
          if (pathStrings.size == 1) {
            return TreePath(arrayOf<Any>(child))
          }
          else {
            val childResult = this.traverseChildren(
              node = child,
              pathStrings = *pathStrings.toList().subList(1, pathStrings.size).toTypedArray(),
              predicate = predicate
            )
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

  /* TODO: remove after check
  private fun JTree.findUniqueMatchingPath(predicate: FinderPredicate): TreePath {


    val model = model
    val newPathValues = mutableListOf<Any>()
    var node: Any = model.root
    val pathElementCount = pathStrings.size

    for (stringIndex in 0 until pathElementCount) {
      val pathString = pathStrings[stringIndex]
      if (stringIndex == 0 && isRootVisible) {
        if (!predicate(pathString, value(node))) throw pathNotFound(pathStrings)
        newPathValues.add(node)
      }
      else {
        try {
          node = traverseUniqueChildren(node, pathString, predicate) ?: throw pathNotFound(
            pathStrings)
        }
        catch (e: ExtJTreeDriver.LoadingNodeException) {
          //if we met loading node let's tell it to caller and probably expand path to clarify this node
          e.treePath = TreePath(newPathValues.toTypedArray())
          throw e
        }
        newPathValues.add(node)
      }
    }
    return TreePath(newPathValues.toTypedArray())
  }

  private fun JTree.traverseUniqueChildren(node: Any,
                                           pathString: String,
                                           predicate: FinderPredicate): Any? {
    var match: Any? = null
    val model = model
    val childCount = model.getChildCount(node)

    for (childIndex in 0 until childCount) {
      val child = model.getChild(node, childIndex)
      if (child is LoadingNode)
        throw ExtJTreeDriver.LoadingNodeException(child, null)
      if (predicate(pathString, value(child))) {
        if (match != null) throw multipleMatchingNodes(pathString, value(node))
        match = child
      }
    }

    return match
  }

  /**
   * this method tries to find any path. If tree contains multiple of searchable path it still accepts.
   */
  private fun JTree.traverseChildren(node: Any,
                                     pathStrings: List<String>,
                                     predicate: FinderPredicate): TreePath? {
    val model = this.model
    val childCount = model.getChildCount(node)

    for (childIndex in 0 until childCount) {
      val child = model.getChild(node, childIndex)
      if (child is LoadingNode) throw ExtJTreeDriver.LoadingNodeException(node = child,
                                                                               treePath = this.getPathToNode(node))
      if (pathStrings.size == 1 && predicate(pathStrings[0], this.value(child))) {

        return TreePath(arrayOf<Any>(child))
      }
      else {
        if (predicate(pathStrings[0], this.value(child))) {
          val childResult = this.traverseChildren(child, pathStrings.subList(1, pathStrings.size), predicate)
          if (childResult != null) return TreePath(arrayOf<Any>(child, *childResult.path))
        }
      }
    }
    return null
  }
*/
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
  private fun pathNotFound(vararg path: String): LocationUnavailableException {
    throw LocationUnavailableException("Unable to find path \"${path.toList()}\"")
  }

  private fun multipleMatchingNodes(pathString: String, parentText: Any): LocationUnavailableException {
    throw LocationUnavailableException(
      "There is more than one node with value '$pathString' under \"$parentText\"")
  }

  companion object {
    val predicateEquality: FinderPredicate = { left: String, right: String -> left == right }
    val predicateWithVersion = { left: String, right: String ->
      val pattern = Regex(",\\s+\\(.*\\)$")
      if (right.contains(pattern))
        left == right.dropLast(right.length - right.indexOfLast { it == ',' })
      else left == right
    }
  }

  private fun JTree.addRootIfInvisible(path: TreePath): TreePath {
    val root = model.root
    if (!isRootVisible && root != null) {
      return if (path.pathCount > 0 && root === path.getPathComponent(0)) {
        path
      }
      else {
        val pathAsArray = path.path
        if (pathAsArray == null) {
          TreePath(listOf(root))
        }
        else {
          val newPath = mutableListOf(*pathAsArray)
          newPath.add(0, root)
          TreePath(newPath.toTypedArray())
        }
      }
    }
    else {
      return path
    }
  }

  fun findPathToNode(node: String) = findPathToNodeByPredicate(node, predicateEquality)

  fun findPathToNodeWithVersion(node: String) = findPathToNodeByPredicate(node, predicateWithVersion)

  fun findPathToNodeByPredicate(node: String, predicate: FinderPredicate): TreePath{
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
  return findMatchingPathByPredicate(pathStrings = *result.toTypedArray(), predicate = predicate)
  }
}