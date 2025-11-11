// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesTree.TreeStateStrategy
import com.intellij.platform.vcs.changes.ChangesUtil.isMergeConflict
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.tree.TreePath

@ApiStatus.Internal
class ChangesViewTreeStateStrategy : TreeStateStrategy<ChangesViewTreeStateStrategy.Companion.MyState> {

  override fun saveState(tree: ChangesTree): MyState {
    val oldRoot = tree.root
    val state = TreeState.createOn(tree, oldRoot)
    state.setScrollToSelection(false)

    val fileCount = oldRoot.getDefaultChangeListNode()?.getFileCount() ?: 0

    return MyState(state, fileCount)
  }

  override fun restoreState(tree: ChangesTree, state: MyState, scrollToSelection: Boolean) {
    val newRoot = tree.root
    state.treeState.applyTo(tree, newRoot)

    initTreeStateIfNeeded(tree as ChangesListView, newRoot, state.oldFileCount)
  }

  private fun initTreeStateIfNeeded(
    view: ChangesListView,
    newRoot: ChangesBrowserNode<*>,
    oldFileCount: Int,
  ) {
    view.getFirstMergeConflictNode()?.let { firstMergeNode ->
      TreeUtil.selectNode(view, firstMergeNode)
    }

    val defaultListNode = newRoot.getDefaultChangeListNode() ?: return
    if (view.selectionCount == 0) {
      TreeUtil.selectNode(view, defaultListNode)
    }

    // IJPL-75200: Expand Default changelist only if it was empty and no other changelists are expanded
    if (shouldExpandDefaultChangeList(newRoot, oldFileCount, isNodeExpanded = {  view.isExpanded(TreePath(it.path))})) {
      view.expandSafe(defaultListNode)
    }
  }

  private fun ChangesBrowserNode<*>.getDefaultChangeListNode() =
    iterateNodeChildren()
      .asSequence()
      .filterIsInstance<ChangesBrowserChangeListNode>()
      .find { node: ChangesBrowserChangeListNode ->
        val list = node.getUserObject()
        list is LocalChangeList && list.isDefault()
      }

  private fun ChangesBrowserNode<*>.hasNonDefaultExpandedChangeLists(isNodeExpanded: (ChangesBrowserChangeListNode) -> Boolean) =
    iterateNodeChildren()
      .asSequence()
      .filterIsInstance<ChangesBrowserChangeListNode>()
      .filter { node ->
        val list = node.getUserObject()
        list is LocalChangeList && !list.isDefault()
      }.any(isNodeExpanded)

  private fun ChangesListView.getFirstMergeConflictNode() = getFirstConflictNode() ?: getFirstResolvedConflictNode()

  private fun ChangesListView.getFirstConflictNode() =
    VcsTreeModelData.allUnderTag(this, CONFLICTS_NODE_TAG).iterateRawNodes()
      .asSequence()
      .filterIsInstance<ChangesBrowserChangeNode>()
      .find { node -> isMergeConflict(node.getUserObject()) }


  private fun ChangesListView.getFirstResolvedConflictNode() =
    VcsTreeModelData.allUnderTag(this, RESOLVED_CONFLICTS_NODE_TAG).iterateRawNodes()
      .asSequence()
      .filterIsInstance<ChangesBrowserChangeNode>()
      .find { node -> isMergeConflict(node.getUserObject()) }

  @VisibleForTesting
  fun shouldExpandDefaultChangeList(
    newRoot: ChangesBrowserNode<*>,
    oldFileCount: Int,
    isNodeExpanded: (ChangesBrowserChangeListNode) -> Boolean,
  ): Boolean = oldFileCount == 0 && !newRoot.hasNonDefaultExpandedChangeLists(isNodeExpanded)

  companion object {
    data class MyState(val treeState: TreeState, val oldFileCount: Int)
  }
}
