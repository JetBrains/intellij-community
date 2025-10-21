// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.popup

import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.components.*
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree

@State(name = "GitBranchesPopupTreeState", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], reportStatistic = false)
@Service(Service.Level.PROJECT)
internal class GitBranchesPopupTreeStateHolder : PersistentStateComponent<TreeState> {

  private var treeState: TreeState? = null

  fun applyStateTo(tree: JTree) {
    treeState?.applyTo(tree)
  }

  fun saveStateFrom(tree: JTree) {
    treeState = createStateForTree(tree)
  }

  override fun getState(): TreeState? = treeState

  override fun loadState(state: TreeState) {
    treeState = state
  }

  companion object {
    fun createStateForTree(tree: JTree): TreeState? {
      val model = tree.model ?: return null
      val root = model.root ?: return null

      val expandedTopLevelPaths =
        (0 until model.getChildCount(root))
          .asSequence()
          .map { index -> model.getChild(root, index) }
          .filter { firstLevelChild -> model.getChildCount(firstLevelChild) > 0 }
          .flatMap { firstLevelChild ->
            TreeUtil.nodeChildren(firstLevelChild, model)
              .mapTo(arrayListOf(TreePathUtil.convertCollectionToTreePath(listOf(root, firstLevelChild)))) { secondLevelChild ->
                TreePathUtil.convertCollectionToTreePath(listOf(root, firstLevelChild, secondLevelChild))
              }
          }
          .filter(tree::isExpanded)
          .toList()

      if (expandedTopLevelPaths.isEmpty()) return null

      return TreeState.createOn(tree, expandedTopLevelPaths, emptyList())
    }
  }
}
