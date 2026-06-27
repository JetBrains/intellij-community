// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.isSelectableSessionTreeId
import com.intellij.ui.treeStructure.Tree
import javax.swing.JTree
import javax.swing.tree.TreePath

internal fun installSessionTreeStructuralSelectionFilter(
  tree: Tree,
  modelProvider: () -> SessionTreeModel,
) {
  var applyingFilteredSelection = false
  tree.addTreeSelectionListener { event ->
    if (applyingFilteredSelection) return@addTreeSelectionListener
    val currentPaths = tree.selectionPaths
    val filteredPaths = filterSelectableSessionTreeSelectionPaths(
      tree = tree,
      model = modelProvider(),
      selectionPaths = currentPaths,
      oldLeadSelectionPath = event.oldLeadSelectionPath,
      newLeadSelectionPath = event.newLeadSelectionPath,
    )
    if (sameSelectionPaths(currentPaths, filteredPaths)) return@addTreeSelectionListener

    applyingFilteredSelection = true
    try {
      if (filteredPaths.isEmpty()) {
        tree.clearSelection()
      }
      else {
        tree.selectionPaths = filteredPaths
      }
    }
    finally {
      applyingFilteredSelection = false
    }
  }
}

internal fun filterSelectableSessionTreeSelectionPaths(
  tree: JTree,
  model: SessionTreeModel,
  selectionPaths: Array<TreePath>?,
  oldLeadSelectionPath: TreePath?,
  newLeadSelectionPath: TreePath?,
): Array<TreePath> {
  val paths = selectionPaths ?: return emptyArray()
  val selectablePaths = paths.filter { path -> path.isSelectableSessionTreePath(model) }
  if (selectablePaths.size == paths.size) return paths
  if (selectablePaths.isNotEmpty()) return selectablePaths.toTypedArray()

  val replacement = findSelectablePathNearStructuralRow(
    tree = tree,
    model = model,
    oldLeadSelectionPath = oldLeadSelectionPath,
    newLeadSelectionPath = newLeadSelectionPath,
  )
  return replacement?.let { arrayOf(it) } ?: emptyArray()
}

private fun findSelectablePathNearStructuralRow(
  tree: JTree,
  model: SessionTreeModel,
  oldLeadSelectionPath: TreePath?,
  newLeadSelectionPath: TreePath?,
): TreePath? {
  val structuralPath = newLeadSelectionPath ?: return oldLeadSelectionPath?.takeIf { it.isSelectableSessionTreePath(model) }
  val structuralRow = tree.getRowForPath(structuralPath)
  if (structuralRow < 0) return oldLeadSelectionPath?.takeIf { it.isSelectableSessionTreePath(model) }

  val oldLeadRow = oldLeadSelectionPath?.let(tree::getRowForPath)?.takeIf { it >= 0 }
  val preferForward = oldLeadRow == null || oldLeadRow < structuralRow
  return if (preferForward) {
    findSelectablePathInRows(tree, model, startInclusive = structuralRow + 1, endExclusive = tree.rowCount, step = 1)
    ?: findSelectablePathInRows(tree, model, startInclusive = structuralRow - 1, endExclusive = -1, step = -1)
    ?: oldLeadSelectionPath?.takeIf { it.isSelectableSessionTreePath(model) }
  }
  else {
    findSelectablePathInRows(tree, model, startInclusive = structuralRow - 1, endExclusive = -1, step = -1)
    ?: findSelectablePathInRows(tree, model, startInclusive = structuralRow + 1, endExclusive = tree.rowCount, step = 1)
    ?: oldLeadSelectionPath.takeIf { it.isSelectableSessionTreePath(model) }
  }
}

private fun findSelectablePathInRows(
  tree: JTree,
  model: SessionTreeModel,
  startInclusive: Int,
  endExclusive: Int,
  step: Int,
): TreePath? {
  var row = startInclusive
  while (row != endExclusive) {
    val path = tree.getPathForRow(row)
    if (path != null && path.isSelectableSessionTreePath(model)) {
      return path
    }
    row += step
  }
  return null
}

private fun TreePath.isSelectableSessionTreePath(model: SessionTreeModel): Boolean {
  val id = lastPathComponent?.let(::extractSessionTreeId) ?: return false
  return isSelectableSessionTreeId(model, id)
}

private fun sameSelectionPaths(left: Array<TreePath>?, right: Array<TreePath>): Boolean {
  if (left == null) return right.isEmpty()
  return left.contentEquals(right)
}
