// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.dvcs.branch.DvcsBranchManager.DvcsBranchManagerListener
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.fetch.GitFetchInProgressListener
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitTagHolder
import git4idea.repo.GitTagLoaderListener
import git4idea.ui.branch.GitBranchManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.properties.Delegates.observable

@ApiStatus.Internal
interface BranchesDashboardTreeModel : BranchesTreeModel {
  var showOnlyMy: Boolean
}

@ApiStatus.Internal
class SyncBranchesDashboardTreeModel(logData: VcsLogData)
  : BranchesDashboardTreeModelBase(logData), BranchesDashboardTreeModel {
  init {
    updateBranchesTree()
  }

  override fun refreshTree() {
    val treeNodes = NodeDescriptorsModel.buildTreeNodes(
      project,
      refs,
      if (showOnlyMy) { ref -> (ref as? BranchInfo)?.isMy == ThreeState.YES } else { _ -> true },
      groupingConfig,
    )
    setTree(treeNodes)
  }
}

@ApiStatus.Internal
class AsyncBranchesDashboardTreeModel(private val cs: CoroutineScope, logData: VcsLogData)
  : BranchesDashboardTreeModelBase(logData), BranchesDashboardTreeModel {
  private val refreshMutex = OverflowSemaphore(1)

  init {
    Disposer.register(cs.nestedDisposable(), this)
    updateBranchesTree()
  }

  override fun refreshTree() {
    startLoading()
    cs.launch(Dispatchers.UI) {
      try {
        val treeNodes = refreshMutex.withPermit {
          val refs = refs.copy()
          val showOnlyMy = showOnlyMy
          val groupingConfig = groupingConfig.toMap()

          withContext(Dispatchers.Default) {
            NodeDescriptorsModel.buildTreeNodes(
              project,
              refs,
              if (showOnlyMy) { ref -> (ref as? BranchInfo)?.isMy == ThreeState.YES } else { _ -> true },
              groupingConfig,
            )
          }
        }
        setTree(treeNodes)
      }
      finally {
        finishLoading()
      }
    }
  }
}

@ApiStatus.Internal
abstract class BranchesDashboardTreeModelBase(
  private val logData: VcsLogData,
) : BranchesTreeModelBase(), BranchesDashboardTreeModel, Disposable {

  protected val project: Project = logData.project

  internal val refs: RefsCollection = RefsCollection(hashSetOf<BranchInfo>(), hashSetOf<BranchInfo>(), hashSetOf<RefInfo>())

  override val groupingConfig: MutableMap<GroupingKey, Boolean> = with(project.service<GitBranchManager>()) {
    hashMapOf(
      GroupingKey.GROUPING_BY_DIRECTORY to isGroupingEnabled(GroupingKey.GROUPING_BY_DIRECTORY),
      GroupingKey.GROUPING_BY_REPOSITORY to isGroupingEnabled(GroupingKey.GROUPING_BY_REPOSITORY)
    )
  }.toMutableMap()

  override var showOnlyMy: Boolean by observable(false) { _, old, new -> if (old != new) updateBranchesIsMyState() }

  var rootsToFilter: Set<VirtualFile>? by observable(null) { _, old, new ->
    if (new != null && old != null && old != new) {
      updateBranchesTree()
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
          groupingConfig[key] = state
          refreshTree()
        }
      }

      override fun showTagsSettingsChanged(state: Boolean) {
        runInEdt {
          updateBranchesTree()
        }
      }
    })
    project.messageBus.connect(this).subscribe(GitTagHolder.GIT_TAGS_LOADED, GitTagLoaderListener {
      runInEdt {
        updateBranchesTree()
      }
    })
    project.messageBus.connect(this)
      .subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, GitIncomingOutgoingListener {
        runInEdt { updateBranchesIncomingOutgoingState() }
      })

    val changeListener = DataPackChangeListener { updateBranchesTree() }
    logData.addDataPackChangeListener(changeListener)
    Disposer.register(this) {
      logData.removeDataPackChangeListener(changeListener)
    }

    project.messageBus.connect(this).subscribe(GitFetchInProgressListener.TOPIC, object : GitFetchInProgressListener {
      override fun fetchStarted() = runInEdt { startLoading() }
      override fun fetchFinished() = runInEdt { finishLoading() }
    })
  }

  override fun dispose() {
    refs.forEach { infos, _ -> infos.clear() }
    rootsToFilter = null
  }

  protected abstract fun refreshTree()

  @RequiresEdt
  protected fun updateBranchesTree() {
    val forceReload = groupingConfig[GroupingKey.GROUPING_BY_REPOSITORY] == true
    val changed = reloadBranches(forceReload)
    if (changed) {
      if (showOnlyMy) {
        updateBranchesIsMyState()
      }
      else {
        refreshTree()
      }
    }
  }

  private fun reloadBranches(force: Boolean): Boolean {
    startLoading()

    try {
      val allRepositories = GitRepositoryManager.getInstance(project).repositories
      val rootsToFilter = rootsToFilter
      val repositories = if (rootsToFilter == null) {
        allRepositories
      }
      else {
        allRepositories.filter { rootsToFilter.contains(it.root) }
      }

      val newLocalBranches = BranchesDashboardUtil.getLocalBranches(project, repositories)
      val newRemoteBranches = BranchesDashboardUtil.getRemoteBranches(project, repositories)
      val newTags = BranchesDashboardUtil.getTags(project, repositories)

      val reloadedLocal = updateIfChanged(refs.localBranches, newLocalBranches, force)
      val reloadedRemote = updateIfChanged(refs.remoteBranches, newRemoteBranches, force)
      val reloadedTags = updateIfChanged(refs.tags, newTags, force)

      return reloadedLocal || reloadedRemote || reloadedTags
    }
    finally {
      finishLoading()
    }
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
    startLoading()
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
        finishLoading()
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
}
