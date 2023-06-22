// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.components.*
import com.intellij.ui.tree.TreePathUtil
import javax.swing.JTree

@State(name = "GitBranchesPopupTreeState", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], reportStatistic = false)
@Service(Service.Level.PROJECT)
class GitBranchesPopupTreeStateHolder : PersistentStateComponent<TreeState> {

  private var treeState: TreeState? = null

  fun applyStateTo(tree: JTree) {
    treeState?.applyTo(tree)
  }

  fun saveStateFrom(tree: JTree){
    treeState = createState(tree)
  }

  private fun createState(tree: JTree): TreeState? {
    val model = tree.model ?: return null
    val root = model.root ?: return null

    val expandedTopLevelPaths =
      (0 until model.getChildCount(root))
        .asSequence()
        .map { index -> model.getChild(root, index) }
        .filter { child -> model.getChildCount(child) > 0 }
        .map { child -> TreePathUtil.convertCollectionToTreePath(listOf(root, child)) }
        .filter(tree::isExpanded)
        .toList()

    if (expandedTopLevelPaths.isEmpty()) return null

    return TreeState.createOn(tree, expandedTopLevelPaths, emptyList())
  }

  override fun getState(): TreeState? = treeState

  override fun loadState(state: TreeState) {
    treeState = state
  }
}
