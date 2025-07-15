// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEMS
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.vcs.git.actions.GitSingleRefActions
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreePath

internal class BranchesDashboardTreeController(
  private val project: Project,
  private val selectionHandler: BranchesDashboardTreeSelectionHandler,
  private val model: BranchesDashboardTreeModel,
  private val tree: BranchesTreeComponent,
) : UiDataProvider, BranchesDashboardTreeSelectionHandler by selectionHandler {

  var showOnlyMy: Boolean by model::showOnlyMy

  init {
    val treeSelectionListener = TreeSelectionListener {
      if (!tree.isShowing) return@TreeSelectionListener

      when (selectionHandler.selectionAction) {
        BranchesDashboardTreeSelectionHandler.SelectionAction.FILTER -> updateLogBranchFilter()
        BranchesDashboardTreeSelectionHandler.SelectionAction.NAVIGATE ->
          tree.getSelection().logNavigatableNodeDescriptor?.let { logNavigatableSelection ->
            selectionHandler.navigateTo(logNavigatableSelection, false)
          }
        null -> return@TreeSelectionListener
      }
    }
    tree.addTreeSelectionListener(treeSelectionListener)
  }

  fun updateLogBranchFilter() {
    val treeSelection = tree.getSelection()
    val selectedFilters = treeSelection.selectedBranchFilters
    val repositories = if (repositoryGroupingEnabled()) treeSelection.repositoriesOfSelectedBranches else emptySet()

    selectionHandler.filterBy(selectedFilters, repositories)
  }

  fun navigateLogToRef(selection: BranchNodeDescriptor.LogNavigatable) {
    selectionHandler.navigateTo(selection, true)
  }

  fun getSelectedRemotes(): Map<GitRepository, Set<GitRemote>> {
    val selectedRemotes = tree.getSelection().selectedRemotes
    if (selectedRemotes.isEmpty()) return emptyMap()

    val result = hashMapOf<GitRepository, MutableSet<GitRemote>>()
    if (repositoryGroupingEnabled()) {
      for (selectedRemote in selectedRemotes) {
        val repository = selectedRemote.repository ?: continue
        val remote = repository.remotes.find { it.name == selectedRemote.remoteName } ?: continue
        result.getOrPut(repository) { hashSetOf() }.add(remote)
      }
    }
    else {
      val remoteNames = selectedRemotes.mapTo(hashSetOf()) { it.remoteName }
      for (repository in GitRepositoryManager.getInstance(project).repositories) {
        val remotes = repository.remotes.filter { remote -> remoteNames.contains(remote.name) }
        if (remotes.isNotEmpty()) {
          result.getOrPut(repository) { hashSetOf() }.addAll(remotes)
        }
      }
    }

    return result
  }

  private fun repositoryGroupingEnabled(): Boolean = model.groupingConfig[GroupingKey.GROUPING_BY_REPOSITORY] ?: false

  override fun uiDataSnapshot(sink: DataSink) {
    sink[BRANCHES_UI_CONTROLLER] = this
    snapshotSelectionActionsKeys(sink, tree.selectionPaths)
  }

  companion object {
    /**
     * Note that at the moment [GitBranchActionsDataKeys] are used only for single ref actions.
     * In other actions [GIT_BRANCHES_TREE_SELECTION] is used
     *
     * Also see [com.intellij.vcs.git.branch.popup.GitDefaultBranchesPopupStep.Companion.createDataContext]
     */
    @VisibleForTesting
    internal fun snapshotSelectionActionsKeys(sink: DataSink, selectionPaths: Array<TreePath>?) {
      val selection = BranchesTreeSelection(selectionPaths)

      sink[GIT_BRANCHES_TREE_SELECTION] = selection
      sink[SELECTED_ITEMS] = selectionPaths

      val selectedNode = selection.selectedNodes.singleOrNull() ?: return
      val selectedDescriptor = selectedNode.getNodeDescriptor()
      if (selection.headSelected) {
        sink[GitBranchActionsDataKeys.USE_CURRENT_BRANCH] = true
      }

      val selectedRef = selectedDescriptor as? BranchNodeDescriptor.Ref ?: return
      sink[GitSingleRefActions.SELECTED_REF_DATA_KEY] = selectedRef.refInfo.ref

      val selectedRepositories = BranchesTreeSelection.Companion.getSelectedRepositories(selectedNode)
      sink[GitBranchActionsDataKeys.AFFECTED_REPOSITORIES] = selectedRepositories
      sink[GitBranchActionsDataKeys.SELECTED_REPOSITORY] = selectedRepositories.singleOrNull()
    }
  }
}
