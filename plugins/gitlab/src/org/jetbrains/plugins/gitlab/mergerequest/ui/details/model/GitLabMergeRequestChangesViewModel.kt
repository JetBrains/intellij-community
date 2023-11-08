// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesContainer
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelDelegate
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCommit
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

internal interface GitLabMergeRequestChangesViewModel : CodeReviewChangesViewModel<GitLabCommit> {
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
    CodeReviewChangesViewModel<GitLabCommit> {
  private val cs = parentCs.childScope()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val changesContainer: Flow<Result<CodeReviewChangesContainer>> = mergeRequest.changes.mapLatest {
    runCatchingUser {
      val changes = it.getParsedChanges()
      CodeReviewChangesContainer(changes.changes, changes.commits.map { it.sha }, changes.changesByCommits)
    }
  }

  private val delegate = CodeReviewChangesViewModelDelegate(cs, changesContainer) {
    GitLabMergeRequestChangeListViewModelImpl(project, this, mergeRequest, it)
  }

  override val reviewCommits: SharedFlow<List<GitLabCommit>> =
    mergeRequest.changes.map { it.commits.await() }.modelFlow(cs, LOG)

  override val selectedCommitIndex: SharedFlow<Int> = reviewCommits.combine(delegate.selectedCommit) { commits, sha ->
    if (sha == null) -1
    else commits.indexOfFirst { it.sha == sha }
  }.modelFlow(cs, LOG)

  override val selectedCommit: SharedFlow<GitLabCommit?> = reviewCommits.combine(selectedCommitIndex) { commits, index ->
    index.takeIf { it >= 0 }?.let { commits[it] }
  }.modelFlow(cs, LOG)

  override val changeListVm: StateFlow<ComputedResult<GitLabMergeRequestChangeListViewModelImpl>> = delegate.changeListVm

  override fun selectCommit(index: Int) {
    delegate.selectCommit(index)
  }

  override fun selectNextCommit() {
    delegate.selectNextCommit()
  }

  override fun selectPreviousCommit() {
    delegate.selectPreviousCommit()
  }

  override fun selectChange(change: RefComparisonChange) {
    delegate.selectChange(change)
  }

  override fun commitHash(commit: GitLabCommit): String = commit.shortId
}

