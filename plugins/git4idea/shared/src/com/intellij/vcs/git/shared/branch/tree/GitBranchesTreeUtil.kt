// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.branch.tree

import com.intellij.openapi.project.Project
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

@ApiStatus.Internal
object GitBranchesTreeUtil {
  @ApiStatus.Internal
  const val FILTER_DEBOUNCE_MS: Long = 100L

  fun JTree.overrideBuiltInAction(actionKey: String, override: (ActionEvent) -> Boolean) {
    val originalAction = actionMap[actionKey]
    actionMap.put(actionKey, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        if (override(e)) return
        originalAction.actionPerformed(e)
      }
    })
  }

  private val cycleScrolling: Boolean
    get() = TreeUtil.isCyclicScrollingAllowed()

  internal fun canHighlight(project: Project, tree: JTree, node: Any?): Boolean {
    return node is GitBranchesTreeModel.BranchesPrefixGroup
           || canSelect(project, tree, node)
  }

  internal fun canSelect(project: Project, tree: JTree, node: Any?): Boolean {
    val model = tree.model
    return when (node) {
      is PopupFactoryImpl.ActionItem -> GitBranchesTreeFilters.byActions(project)
      is GitBranchesTreeModel.RepositoryNode -> {
        if (!node.isLeaf) false else GitBranchesTreeFilters.byRepositoryName(project)
      }
      else -> model.isLeaf(node)
    }
  }

  private fun JTree.selectFirstRow(project: Project): Boolean {
    val path = getPathForRow(0)?:return false
    if (!canSelect(project, this, path.lastPathComponent)) return false

    scrollPathToVisible(path)
    selectionPath = path
    return true
  }

  fun JTree.selectNext(project: Project): Boolean {
    if (selectRow(project, true)) return true

    return selectNextLeaf()
  }

  private fun JTree.selectNextLeaf(): Boolean {
    val nextLeaf = model.findNextLeaf(selectionPath, true)
    val toSelect = nextLeaf ?: if (cycleScrolling) model.findFirstLeaf() else null ?: return false
    scrollPathToVisible(toSelect)
    selectionPath = toSelect
    return true
  }

  fun JTree.selectPrev(project: Project): Boolean {
    if (selectRow(project, false)) return true

    return selectPrevLeaf()
  }

  private fun JTree.selectPrevLeaf(): Boolean {
    val prevLeaf = model.findNextLeaf(selectionPath, false)
    val toSelect = prevLeaf ?: if (cycleScrolling) model.findLastLeaf() else null ?: return false
    scrollPathToVisible(toSelect)
    selectionPath = toSelect
    return true
  }

  fun JTree.selectFirst(project: Project): Boolean {
    if (selectFirstRow(project)) return true

    val toSelect = model.findFirstLeaf() ?: return false
    scrollPathToVisible(toSelect)
    selectionPath = toSelect
    return true
  }

  fun JTree.selectLast(project: Project): Boolean {
    if (selectLastRow(project)) return true

    val toSelect = model.findLastLeaf() ?: return false
    scrollPathToVisible(toSelect)
    selectionPath = toSelect
    return true
  }

  private fun JTree.selectLastRow(project: Project): Boolean {
    val path = getPathForRow(rowCount - 1) ?: return false
    if (!canSelect(project, this, path.lastPathComponent)) return false

    scrollPathToVisible(path)
    selectionPath = path
    return true
  }

  private fun JTree.selectRow(project: Project, forward: Boolean, curSelection: TreePath? = selectionPath): Boolean {
    var rowToSelect = getRowForPath(curSelection) + (if (forward) 1 else -1)
    if (rowToSelect !in 0 until rowCount) {
      if (cycleScrolling) rowToSelect = if (forward) 0 else rowCount - 1
    }
    val pathToSelect = getPathForRow(rowToSelect) ?: return false
    val nodeToSelect = pathToSelect.lastPathComponent
    if (nodeToSelect is SeparatorWithText) return selectRow(project, forward, pathToSelect)

    if (!canSelect(project, this, nodeToSelect)) {
      if (model.findNextLeaf(pathToSelect, forward) != null) {
        return if (forward) selectNextLeaf() else selectPrevLeaf()
      }
      return selectRow(project, forward, pathToSelect)
    }

    scrollPathToVisible(pathToSelect)
    selectionPath = pathToSelect
    return true
  }

  private fun TreeModel.findNextLeaf(start: TreePath?, forward: Boolean): TreePath? {
    if (start == null) return null
    if (start.parentPath == null) return null

    return findChildLeaf(start, forward) ?: findNextSiblingLeaf(start, forward)
  }

  private fun TreeModel.findChildLeaf(parentPath: TreePath, forward: Boolean, startChild: TreePath? = null): TreePath? {
    val parent = parentPath.lastPathComponent
    val childCount = getChildCount(parent)

    if (childCount <= 0) {
      return null
    }

    val startIndex =
      if (startChild == null) {
        if (forward) 0 else childCount - 1
      }
      else {
        val startChildIndex = getIndexOfChild(parentPath.lastPathComponent, startChild.lastPathComponent)
        if (forward) startChildIndex + 1 else startChildIndex - 1
      }

    if (startIndex < 0 || startIndex >= childCount) {
      return null
    }

    val indices = if (forward) startIndex until childCount else startIndex downTo 0
    for (i in indices) {
      val child = getChild(parent, i)!!
      val childPath = parentPath.pathByAddingChild(child)

      if (isLeaf(child)) {
        return childPath
      }

      val childOfChild = findChildLeaf(childPath, forward)
      if (childOfChild != null) {
        return childOfChild
      }
    }
    return null
  }

  private fun TreeModel.findNextSiblingLeaf(start: TreePath, forward: Boolean): TreePath? {
    val parent = start.parentPath
    if (parent == null) return null
    return findChildLeaf(parent, forward, start) ?: findNextSiblingLeaf(start.parentPath, forward)
  }

  private fun TreeModel.findFirstLeaf(): TreePath? {
    val rootPath = TreePath(root)
    return findChildLeaf(rootPath, true)
  }

  private fun TreeModel.findLastLeaf(): TreePath? {
    val rootPath = TreePath(root)
    return findChildLeaf(rootPath, false)
  }
}
