// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesContainer
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelDelegate
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

internal interface GitLabMergeRequestChangesViewModel : CodeReviewChangesViewModel<GitLabCommitViewModel> {
  /**
   * View model of a current change list
   */
  val changeListVm: StateFlow<ComputedResult<GitLabMergeRequestChangeListViewModel>>

  fun selectChange(change: RefComparisonChange)
}

private val LOG = logger<GitLabMergeRequestChangesViewModel>()

internal class GitLabMergeRequestChangesViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestChangesViewModel,
    CodeReviewChangesViewModel<GitLabCommitViewModel> {
  private val cs = parentCs.childScope(javaClass.name)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val changesContainer: StateFlow<Result<GitLabChangesContainer>?> = mergeRequest.changes.mapLatest {
    runCatchingUser {
      GitLabChangesContainer(it.getParsedChanges())
    }
  }.stateInNow(cs, null)

  private val delegate = CodeReviewChangesViewModelDelegate.create(cs, changesContainer.filterNotNull()) { changesContainer, changeList ->
    changesContainer as GitLabChangesContainer

    GitLabMergeRequestChangeListViewModelImpl(project, this, mergeRequest, changesContainer.changes, changeList)
  }

  override val reviewCommits: SharedFlow<List<GitLabCommitViewModel>> =
    mergeRequest.changes
      .map { it.commits.await().map { commit -> GitLabCommitViewModel(project, mergeRequest, commit) } }
      .modelFlow(cs, LOG)

  override val selectedCommitIndex: SharedFlow<Int> = reviewCommits.combine(delegate.selectedCommit) { commits, sha ->
    if (sha == null) -1
    else commits.indexOfFirst { it.sha == sha }
  }.modelFlow(cs, LOG)

  override val selectedCommit: SharedFlow<GitLabCommitViewModel?> = reviewCommits.combine(selectedCommitIndex) { commits, index ->
    index.takeIf { it >= 0 }?.let { commits[it] }
  }.modelFlow(cs, LOG)

  override val changeListVm: StateFlow<ComputedResult<GitLabMergeRequestChangeListViewModelImpl>> = delegate.changeListVm

  override fun selectCommit(index: Int) {
    delegate.selectCommit(index)?.selectChange(null)
  }

  override fun selectNextCommit() {
    delegate.selectNextCommit()?.selectChange(null)
  }

  override fun selectPreviousCommit() {
    delegate.selectPreviousCommit()?.selectChange(null)
  }

  override fun selectChange(change: RefComparisonChange) {
    val commit = changesContainer.value?.getOrNull()?.let {
      it.commitsByChange[change]
    }
    delegate.selectCommit(commit)?.selectChange(change)
  }

  override fun commitHash(commit: GitLabCommitViewModel): String = commit.shortId
}

internal class GitLabChangesContainer(
  val changes: GitBranchComparisonResult
) : CodeReviewChangesContainer(
  changes.changes,
  changes.commits.map { it.sha },
  changes.changesByCommits
)

