// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreePath

/**
 * SelectionModel that ignores selection requests for nodes marked with [ChangesBrowserNodeKeys.NON_SELECTABLE].
 */
@ApiStatus.Internal
class NonSelectableNodeFilteringSelectionModel : DefaultTreeSelectionModel() {
  private fun TreePath?.isNonSelectable(): Boolean {
    if (this == null) return false
    val node = this.lastPathComponent as? ChangesBrowserNode<*> ?: return false
    return node.getUserData(ChangesBrowserNodeKeys.NON_SELECTABLE) == true
  }

  override fun setSelectionPath(path: TreePath?) {
    if (!path.isNonSelectable()) {
      super.setSelectionPath(path)
    }
  }

  override fun setSelectionPaths(paths: Array<out TreePath>?) {
    if (paths == null) {
      super.setSelectionPaths(null)
      return
    }
    val firstNonSelectable = paths.indexOfFirst { it.isNonSelectable() }
    if (firstNonSelectable < 0) {
      super.setSelectionPaths(paths)
      return
    }
    val filtered = ArrayList<TreePath>(paths.size - 1)
    paths.filterTo(filtered) { !it.isNonSelectable() }
    super.setSelectionPaths(filtered.toTypedArray())
  }

  override fun addSelectionPath(path: TreePath?) {
    if (!path.isNonSelectable()) super.addSelectionPath(path)
  }

  override fun addSelectionPaths(paths: Array<out TreePath>?) {
    if (paths == null) return
    val firstNonSelectable = paths.indexOfFirst { it.isNonSelectable() }
    if (firstNonSelectable < 0) {
      super.addSelectionPaths(paths)
      return
    }
    val filtered = ArrayList<TreePath>(paths.size - 1)
    paths.filterTo(filtered) { !it.isNonSelectable() }
    if (filtered.isNotEmpty()) super.addSelectionPaths(filtered.toTypedArray())
  }
}
