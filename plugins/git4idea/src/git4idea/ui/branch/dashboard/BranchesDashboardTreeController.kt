// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.dvcs.branch.DvcsBranchManager.DvcsBranchManagerListener
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEMS
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.vcs.log.VcsLogBranchLikeFilter
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.with
import com.intellij.vcs.log.visible.filters.without
import com.intellij.vcs.ui.ProgressStripe
import git4idea.GitLocalBranch
import git4idea.actions.GitFetch
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.i18n.GitBundle.message
import git4idea.repo.*
import git4idea.ui.branch.GitBranchManager
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreePath
import kotlin.properties.Delegates

internal class BranchesDashboardTreeController(
  private val logData: VcsLogData,
  private val logProperties: VcsLogUiProperties,
  private val logFilterUi: VcsLogFilterUiEx,
  private val logNavigator: (BranchNodeDescriptor.LogNavigatable, focus: Boolean) -> Unit,
  private val tree: FilteringBranchesTree,
  private val branchesProgressStripe: ProgressStripe,
) : Disposable, UiDataProvider {

  private val project = logData.project

  private val refs = RefsCollection(hashSetOf<BranchInfo>(), hashSetOf<BranchInfo>(), hashSetOf<RefInfo>())

  var showOnlyMy: Boolean by AtomicObservableProperty(false) { old, new -> if (old != new) updateBranchesIsMyState() }

  private var rootsToFilter: Set<VirtualFile>? by Delegates.observable(null) { _, old, new ->
    if (new != null && old != null && old != new) {
      updateBranchesTree(false)
    }
  }

  init {
    project.messageBus.connect(this).subscribe(DvcsBranchManager.DVCS_BRANCH_SETTINGS_CHANGED, object : DvcsBranchManagerListener {
      override fun branchFavoriteSettingsChanged() {
        runInEdt {
          updateBranchesIsFavoriteState()
        }
      }

      override fun branchGroupingSettingsChanged(key: GroupingKey, state: Boolean) {
        runInEdt {
          tree.toggleGrouping(key, state)
          refreshTree()
        }
      }

      override fun showTagsSettingsChanged(state: Boolean) {
        runInEdt {
          updateBranchesTree(false)
        }
      }
    })
    project.messageBus.connect(this).subscribe(GitTagHolder.GIT_TAGS_LOADED, GitTagLoaderListener {
      runInEdt {
        updateBranchesTree(false)
      }
    })
    project.messageBus.connect(this)
      .subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, GitIncomingOutgoingListener {
        runInEdt { updateBranchesIncomingOutgoingState() }
      })

    val changeListener = DataPackChangeListener { updateBranchesTree(false) }
    logData.addDataPackChangeListener(changeListener)
    Disposer.register(this) {
      logData.removeDataPackChangeListener(changeListener)
    }

    val logUiFilterListener = VcsLogFilterUiEx.VcsLogFilterListener {
      val roots = logData.roots.toSet()
      rootsToFilter = if (roots.size == 1) {
        roots
      }
      else {
        VcsLogUtil.getAllVisibleRoots(roots, logFilterUi.filters)
      }
    }
    logFilterUi.addFilterListener(logUiFilterListener)

    val treeSelectionListener = TreeSelectionListener {
      if (!tree.component.isShowing) return@TreeSelectionListener

      if (logProperties[CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY]) {
        updateLogBranchFilter()
      }
      else if (logProperties[NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY]) {
        tree.component.getSelection().logNavigatableNodeDescriptor?.let { logNavigatableSelection ->
          logNavigator(logNavigatableSelection, false)
        }
      }
    }
    tree.component.addTreeSelectionListener(treeSelectionListener)
    Disposer.register(this) {
      tree.component.removeTreeSelectionListener(treeSelectionListener)
    }

    UiNotifyConnector.installOn(tree.component, object : Activatable {
      private var initial = true

      override fun showNotify() {
        updateBranchesTree(initial)
        initial = false
      }
    })

    logUiFilterListener.onFiltersChanged()
  }

  override fun dispose() {
    refs.forEach { infos, _ -> infos.clear() }
    rootsToFilter = null
  }

  fun updateLogBranchFilter() {
    val selectedFilters = tree.component.getSelection().selectedBranchFilters
    val oldFilters = logFilterUi.filters
    val newFilters = if (selectedFilters.isNotEmpty()) {
      oldFilters.without(VcsLogBranchLikeFilter::class.java).with(VcsLogFilterObject.fromBranches(selectedFilters))
    }
    else {
      oldFilters.without(VcsLogBranchLikeFilter::class.java)
    }
    logFilterUi.filters = newFilters
  }

  fun navigateLogToRef(selection: BranchNodeDescriptor.LogNavigatable) {
    logNavigator(selection, true)
  }
  fun getSelectedRemotes(): Map<GitRepository, Set<GitRemote>> {
    val selectedRemotes = tree.component.getSelection().selectedRemotes
    if (selectedRemotes.isEmpty()) return emptyMap()

    val result = hashMapOf<GitRepository, MutableSet<GitRemote>>()
    if (tree.isGroupingEnabled(GroupingKey.GROUPING_BY_REPOSITORY)) {
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

  private fun startLoadingBranches() {
    tree.component.emptyText.text = message("action.Git.Loading.Branches.progress")
    branchesProgressStripe.startLoading()
  }

  private fun stopLoadingBranches() {
    tree.component.emptyText.text = StatusText.getDefaultEmptyText()
    branchesProgressStripe.stopLoading()
  }

  fun launchFetch() {
    startLoadingBranches()
    GitFetch.performFetch(project) {
      stopLoadingBranches()
    }
  }

  private fun refreshTree() {
    tree.refreshTree(refs, showOnlyMy)
  }

  private fun updateBranchesTree(initial: Boolean) {
    if (!tree.component.isShowing) return

    val forceReload = tree.isGroupingEnabled(GroupingKey.GROUPING_BY_REPOSITORY)
    val changed = reloadBranches(forceReload)
    if (changed) {
      if (showOnlyMy) {
        updateBranchesIsMyState()
      }
      else {
        tree.refreshNodeDescriptorsModel(refs, showOnlyMy)
      }
    }
    tree.update(initial, changed)
  }

  private fun reloadBranches(force: Boolean): Boolean {
    startLoadingBranches()

    val newLocalBranches = BranchesDashboardUtil.getLocalBranches(project, rootsToFilter)
    val newRemoteBranches = BranchesDashboardUtil.getRemoteBranches(project, rootsToFilter)
    val newTags = BranchesDashboardUtil.getTags(project, rootsToFilter)

    val reloadedLocal = updateIfChanged(refs.localBranches, newLocalBranches, force)
    val reloadedRemote = updateIfChanged(refs.remoteBranches, newRemoteBranches, force)
    val reloadedTags = updateIfChanged(refs.tags, newTags, force)

    stopLoadingBranches()

    return reloadedLocal || reloadedRemote || reloadedTags
  }

  private fun <T : RefInfo> updateIfChanged(currentState: MutableCollection<T>, newState: Set<T>, force: Boolean) =
    if (force || newState != currentState) {
      currentState.clear()
      currentState.addAll(newState)
      true
    }
    else false

  private fun updateBranchesIsFavoriteState() {
    with(project.service<GitBranchManager>()) {
      refs.forEach { refs, refType ->
        for (ref in refs) {
          val isFavorite = ref.repositories.any { isFavorite(refType, it, ref.refName) }
          ref.isFavorite = isFavorite
        }
      }
    }

    refreshTree()
  }

  private fun updateBranchesIncomingOutgoingState() {
    val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
    for (localBranch in refs.localBranches) {
      val incomingOutgoing = incomingOutgoingManager.getIncomingOutgoingState(localBranch.repositories, GitLocalBranch(localBranch.branchName))
      localBranch.incomingOutgoingState = incomingOutgoing
    }

    refreshTree()
  }

  private fun updateBranchesIsMyState() {
    val allBranches = refs.localBranches + refs.remoteBranches
    val branchesToCheck = allBranches.filter { it.isMy == ThreeState.UNSURE }
    startLoadingBranches()
    calculateMyBranchesInBackground(
      run = { indicator ->
        BranchesDashboardUtil.checkIsMyBranchesSynchronously(logData, branchesToCheck, indicator)
      },
      onSuccess = { branches ->
        refs.localBranches.updateUnsureBranchesStateFrom(branches)
        refs.remoteBranches.updateUnsureBranchesStateFrom(branches)
        refreshTree()
      },
      onFinished = {
        stopLoadingBranches()
      })
  }

  private fun Collection<BranchInfo>.updateUnsureBranchesStateFrom(updateFromBranches: Collection<BranchInfo>) {
    if (updateFromBranches.isEmpty()) return

    for (branch in this) {
      if (branch.isMy == ThreeState.UNSURE) {
        branch.isMy = if (updateFromBranches.contains(branch)) ThreeState.YES else ThreeState.NO
      }
    }
  }

  private fun calculateMyBranchesInBackground(
    run: (ProgressIndicator) -> Set<BranchInfo>,
    onSuccess: (Set<BranchInfo>) -> Unit,
    onFinished: () -> Unit,
  ) {
    var calculatedBranches: Set<BranchInfo>? = null
    object : Task.Backgroundable(project, message("action.Git.Show.My.Branches.description.calculating.branches.progress"), true) {
      override fun run(indicator: ProgressIndicator) {
        calculatedBranches = run(indicator)
      }

      override fun onSuccess() {
        val branches = calculatedBranches
        if (branches != null) {
          onSuccess(branches)
        }
      }

      override fun onFinished() {
        onFinished()
      }
    }.queue()
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[BRANCHES_UI_CONTROLLER] = this
    snapshotSelectionActionsKeys(sink, tree.component.selectionPaths)
    sink[VcsLogInternalDataKeys.LOG_UI_PROPERTIES] = logProperties
  }

  companion object {
    /**
     * Note that at the moment [GitBranchActionsDataKeys] are used only for single ref actions.
     * In other actions [GIT_BRANCHES_TREE_SELECTION] is used
     *
     * Also see [git4idea.ui.branch.popup.GitBranchesTreePopupStep.Companion.createDataContext]
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

      when (selectedRef) {
        is BranchNodeDescriptor.Branch -> {
          sink[GitBranchActionsDataKeys.BRANCHES] = listOf(selectedRef.branchInfo.branch)
        }
        is BranchNodeDescriptor.Tag -> {
          sink[GitBranchActionsDataKeys.TAGS] = listOf(selectedRef.tagInfo.tag)
        }
      }

      val selectedRepositories = BranchesTreeSelection.Companion.getSelectedRepositories(selectedNode)
      sink[GitBranchActionsDataKeys.AFFECTED_REPOSITORIES] = selectedRepositories
      sink[GitBranchActionsDataKeys.SELECTED_REPOSITORY] = selectedRepositories.singleOrNull()
    }
  }
}
