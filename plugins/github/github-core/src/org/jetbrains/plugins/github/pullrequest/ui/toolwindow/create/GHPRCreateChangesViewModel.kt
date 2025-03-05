// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.details.model.*
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.map
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext

internal class GHPRCreateChangesViewModel(
  private val project: Project,
  private val settings: GithubPullRequestsProjectUISettings,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val baseBranch: GitRemoteBranch,
  private val headBranch: GitBranch,
  private val commits: List<VcsCommitMetadata>,
) : CodeReviewChangesViewModel<VcsCommitMetadata> {
  private val cs = parentCs.childScope(javaClass.name)

  override val reviewCommits: StateFlow<List<VcsCommitMetadata>> = MutableStateFlow(commits)

  private val stateHandler = CodeReviewCommitsChangesStateHandler.create(cs, commits, {
    CommitChangesViewModel(this, it).apply {
      selectChange(null)
    }
  })

  override val selectedCommitIndex: StateFlow<Int> = stateHandler.selectedCommit.mapState { commits.indexOf(it) }
  override val selectedCommit: StateFlow<VcsCommitMetadata?> = stateHandler.selectedCommit

  val commitChangesVm: StateFlow<CommitChangesViewModel> = stateHandler.changeListVm

  override fun selectCommit(index: Int) {
    stateHandler.selectCommit(index)?.selectChange(null)
  }

  override fun selectNextCommit() {
    stateHandler.selectNextCommit()?.selectChange(null)
  }

  override fun selectPreviousCommit() {
    stateHandler.selectPreviousCommit()?.selectChange(null)
  }

  override fun commitHash(commit: VcsCommitMetadata): String = commit.id.toShortString()

  inner class CommitChangesViewModel(cs: CoroutineScope, commit: VcsCommitMetadata?) {
    private val changeSelectionRequests = MutableSharedFlow<RefComparisonChange?>(replay = 1)

    val changeListVm: StateFlow<ComputedResult<CodeReviewChangeListViewModelBase>> = computationStateFlow(flowOf(Unit)) {
      loadChanges(commit)
    }.mapScoped { result ->
      result.map {
        createChangesVm(commit, it)
      }
    }.stateInNow(cs, ComputedResult.loading())

    private fun CoroutineScope.createChangesVm(commit: VcsCommitMetadata?, changes: Collection<RefComparisonChange>) =
      GHPRCreateChangeListViewModel(this, commit, changes.toList()).also { vm ->
        launchNow {
          changeSelectionRequests.collect {
            vm.selectChange(it)
          }
        }
      }

    private suspend fun loadChanges(commit: VcsCommitMetadata?): Collection<RefComparisonChange> =
      if (commit == null) {
        dataContext.creationService.getDiff(baseBranch, headBranch)
      }
      else {
        dataContext.creationService.getDiff(commit)
      }

    fun selectChange(change: RefComparisonChange?) {
      changeSelectionRequests.tryEmit(change)
    }
  }

  private inner class GHPRCreateChangeListViewModel(parentCs: CoroutineScope, commit: VcsCommitMetadata?, changes: List<RefComparisonChange>)
    : CodeReviewChangeListViewModelBase(parentCs, CodeReviewChangeList(commit?.id?.asString(), changes)),
      CodeReviewChangeListViewModel.WithGrouping {
    override val grouping: StateFlow<Set<String>> = settings.changesGroupingState

    override fun setGrouping(grouping: Collection<String>) {
      settings.changesGrouping = grouping.toSet()
    }

    override val project: Project = this@GHPRCreateChangesViewModel.project

    override fun showDiffPreview() {
      dataContext.filesManager.createAndOpenDiffFile(null, true)
    }

    override fun showDiff() {
      showDiffPreview() // implement
    }
  }
}