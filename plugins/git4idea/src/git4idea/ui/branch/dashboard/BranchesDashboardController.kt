// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.dvcs.branch.DvcsBranchManager.DvcsBranchManagerListener
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.i18n.GitBundle.message
import git4idea.repo.*
import git4idea.ui.branch.GitBranchManager
import kotlin.properties.Delegates

internal class BranchesDashboardController(
  private val project: Project,
  private val ui: BranchesDashboardUi,
) : Disposable, UiDataProvider {

  private val changeListener = DataPackChangeListener { ui.updateBranchesTree(false) }
  private val logUiFilterListener = VcsLogFilterUiEx.VcsLogFilterListener { rootsToFilter = ui.getRootsToFilter() }
  private val logUiPropertiesListener = object : VcsLogUiProperties.PropertiesChangeListener {
    override fun <T : Any?> onPropertyChanged(property: VcsLogUiProperties.VcsLogUiProperty<T>) {
      if (property == SHOW_GIT_BRANCHES_LOG_PROPERTY) {
        ui.toggleBranchesPanelVisibility()
      }
    }
  }

  val refs = RefsCollection(hashSetOf<BranchInfo>(), hashSetOf <BranchInfo>(), hashSetOf<RefInfo>())

  var showOnlyMy: Boolean by AtomicObservableProperty(false) { old, new -> if (old != new) updateBranchesIsMyState() }

  private var rootsToFilter: Set<VirtualFile>? by Delegates.observable(null) { _, old, new ->
    if (new != null && old != null && old != new) {
      ui.updateBranchesTree(false)
    }
  }

  init {
    Disposer.register(ui, this)
    project.messageBus.connect(this).subscribe(DvcsBranchManager.DVCS_BRANCH_SETTINGS_CHANGED, object : DvcsBranchManagerListener {
      override fun branchFavoriteSettingsChanged() {
        updateBranchesIsFavoriteState()
      }

      override fun branchGroupingSettingsChanged(key: GroupingKey, state: Boolean) {
        toggleGrouping(key, state)
      }

      override fun showTagsSettingsChanged(state: Boolean) {
        ui.updateBranchesTree(false)
      }
    })
    project.messageBus.connect(this).subscribe(GitTagHolder.GIT_TAGS_LOADED, GitTagLoaderListener {
      runInEdt {
        ui.updateBranchesTree(false)
      }
    })
    project.messageBus.connect(this)
      .subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, GitIncomingOutgoingListener {
        runInEdt { updateBranchesIncomingOutgoingState() }
      })
  }

  override fun dispose() {
    refs.forEach { infos, _ -> infos.clear() }
    rootsToFilter = null
  }

  fun updateLogBranchFilter() {
    ui.updateLogBranchFilter()
  }

  fun navigateLogToSelectedBranch() {
    ui.navigateToSelectedBranch(true)
  }

  fun toggleGrouping(key: GroupingKey, state: Boolean) {
    ui.toggleGrouping(key, state)
  }

  fun getSelectedRemotes(): Map<GitRepository, Set<GitRemote>> {
    val selectedRemotes = ui.getSelection().selectedRemotes
    if (selectedRemotes.isEmpty()) return emptyMap()

    val result = hashMapOf<GitRepository, MutableSet<GitRemote>>()
    if (ui.isGroupingEnabled(GroupingKey.GROUPING_BY_REPOSITORY)) {
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

  fun reloadBranches(): Boolean {
    val forceReload = ui.isGroupingEnabled(GroupingKey.GROUPING_BY_REPOSITORY)
    val changed = reloadBranches(forceReload)
    if (!changed) return false

    if (showOnlyMy) {
      updateBranchesIsMyState()
    }
    else {
      ui.refreshTreeModel()
    }
    return true
  }

  private fun reloadBranches(force: Boolean): Boolean {
    ui.startLoadingBranches()

    val newLocalBranches = BranchesDashboardUtil.getLocalBranches(project, rootsToFilter)
    val newRemoteBranches = BranchesDashboardUtil.getRemoteBranches(project, rootsToFilter)
    val newTags = BranchesDashboardUtil.getTags(project, rootsToFilter)

    val reloadedLocal = updateIfChanged(refs.localBranches, newLocalBranches, force)
    val reloadedRemote = updateIfChanged(refs.remoteBranches, newRemoteBranches, force)
    val reloadedTags = updateIfChanged(refs.tags, newTags, force)

    ui.stopLoadingBranches()

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

    ui.refreshTree()
  }

  private fun updateBranchesIncomingOutgoingState() {
    val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
    for (localBranch in refs.localBranches) {
      val incomingOutgoing = incomingOutgoingManager.getIncomingOutgoingState(localBranch.repositories, GitLocalBranch(localBranch.branchName))
      localBranch.incomingOutgoingState = incomingOutgoing
    }

    ui.refreshTree()
  }

  private fun updateBranchesIsMyState() {
    VcsProjectLog.runWhenLogIsReady(project) {
      val allBranches = refs.localBranches + refs.remoteBranches
      val branchesToCheck = allBranches.filter { it.isMy == ThreeState.UNSURE }
      ui.startLoadingBranches()
      calculateMyBranchesInBackground(
        run = { indicator ->
          BranchesDashboardUtil.checkIsMyBranchesSynchronously(VcsProjectLog.getInstance(project), branchesToCheck, indicator)
        },
        onSuccess = { branches ->
          refs.localBranches.updateUnsureBranchesStateFrom(branches)
          refs.remoteBranches.updateUnsureBranchesStateFrom(branches)
          ui.refreshTree()
        },
        onFinished = {
          ui.stopLoadingBranches()
        })
    }
  }

  private fun Collection<BranchInfo>.updateUnsureBranchesStateFrom(updateFromBranches: Collection<BranchInfo>) {
    if (updateFromBranches.isEmpty()) return

    for (branch in this) {
      if (branch.isMy == ThreeState.UNSURE) {
        branch.isMy = if (updateFromBranches.contains(branch)) ThreeState.YES else ThreeState.NO
      }
    }
  }

  private fun calculateMyBranchesInBackground(run: (ProgressIndicator) -> Set<BranchInfo>,
                                              onSuccess: (Set<BranchInfo>) -> Unit,
                                              onFinished: () -> Unit) {
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

  fun registerDataPackListener(vcsLogData: VcsLogData) {
    vcsLogData.addDataPackChangeListener(changeListener)
  }

  fun removeDataPackListener(vcsLogData: VcsLogData) {
    vcsLogData.removeDataPackChangeListener(changeListener)
  }

  fun registerLogUiPropertiesListener(vcsLogUiProperties: VcsLogUiProperties) {
    vcsLogUiProperties.addChangeListener(logUiPropertiesListener)
  }

  fun removeLogUiPropertiesListener(vcsLogUiProperties: VcsLogUiProperties) {
    vcsLogUiProperties.removeChangeListener(logUiPropertiesListener)
  }

  fun registerLogUiFilterListener(logFilterUi: VcsLogFilterUiEx) {
    logFilterUi.addFilterListener(logUiFilterListener)
    logUiFilterListener.onFiltersChanged()
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[BRANCHES_UI_CONTROLLER] = this
  }
}
