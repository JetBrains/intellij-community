// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiffProcessorViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerScrollRequest
import com.intellij.collaboration.ui.codereview.diff.model.PreLoadingCodeReviewAsyncDiffViewModelDelegate
import com.intellij.collaboration.ui.codereview.diff.model.RefComparisonChangesSorter
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.ListSelection
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffReviewViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModelBase
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes

internal interface GitLabMergeRequestDiffViewModel : GitLabMergeRequestReviewViewModel, CodeReviewDiffProcessorViewModel<GitLabMergeRequestDiffChangeViewModel> {
  fun getViewModelFor(change: RefComparisonChange): Flow<GitLabMergeRequestDiffReviewViewModel?>

  companion object {
    val KEY: Key<GitLabMergeRequestDiffViewModel> = Key.create("GitLab.MergeRequest.Diff.ViewModel")
  }
}

internal class GitLabMergeRequestDiffProcessorViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  private val discussions: GitLabMergeRequestDiscussionsViewModels,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
) : GitLabMergeRequestDiffViewModel, GitLabMergeRequestReviewViewModelBase(
  parentCs.childScope("GitLab Merge Request Diff Review VM"),
  currentUser, mergeRequest
) {
  private val changesFetchFlow = computationStateFlow(mergeRequest.changes, GitLabMergeRequestChanges::loadRevisionsAndParseChanges)
  private val changesSorter = project.service<GitLabMergeRequestsPreferences>().changesGroupingState
    .map {
      { changes: List<RefComparisonChange> ->
        RefComparisonChangesSorter.Grouping(project, it).sort(changes)
      }
    }

  private val delegate: PreLoadingCodeReviewAsyncDiffViewModelDelegate<RefComparisonChange, GitLabMergeRequestDiffChangeViewModel> =
    PreLoadingCodeReviewAsyncDiffViewModelDelegate.create(changesFetchFlow, changesSorter) { allChanges, change ->
      GitLabMergeRequestDiffChangeViewModelImpl(this, project, allChanges, change)
    }

  override val changes: StateFlow<ComputedResult<CodeReviewDiffProcessorViewModel.State<GitLabMergeRequestDiffChangeViewModel>>?> =
    delegate.changes.stateIn(cs, SharingStarted.Lazily, null)

  override fun showChange(change: GitLabMergeRequestDiffChangeViewModel, scrollRequest: DiffViewerScrollRequest?) =
    delegate.showChange(change.change, scrollRequest)

  override fun showChange(changeIdx: Int, scrollRequest: DiffViewerScrollRequest?) {
    val changeVm = changes.value?.result?.getOrNull()?.selectedChanges?.list?.getOrNull(changeIdx) ?: return
    showChange(changeVm, scrollRequest)
  }

  fun showChanges(changes: ListSelection<RefComparisonChange>, scrollLocation: DiffLineLocation? = null) =
    delegate.showChanges(changes, scrollLocation?.let(DiffViewerScrollRequest::toLine))

  suspend fun handleSelection(listener: (ListSelection<RefComparisonChange>?) -> Unit): Nothing = delegate.handleSelection(listener)

  private val changeVmsMap = mutableMapOf<RefComparisonChange, StateFlow<GitLabMergeRequestDiffReviewViewModelImpl?>>()

  override fun getViewModelFor(change: RefComparisonChange): Flow<GitLabMergeRequestDiffReviewViewModel?> =
    changeVmsMap.getOrPut(change) {
      changesFetchFlow
        .mapNotNull { it.getOrNull() }
        .mapScoped { changes ->
          changes.patchesByChange[change]?.let { createChangeVm(changes, change, it) }
        }.stateIn(cs, SharingStarted.WhileSubscribed(5.minutes, ZERO), null)
    }

  private fun CoroutineScope.createChangeVm(
    changes: GitBranchComparisonResult,
    change: RefComparisonChange,
    diffData: GitTextFilePatchWithHistory,
  ) =
    GitLabMergeRequestDiffReviewViewModelImpl(project, this, mergeRequest, changes, diffData, change, discussions,
                                              discussionsViewOption, avatarIconsProvider)
}

private suspend fun GitLabMergeRequestChanges.loadRevisionsAndParseChanges(): GitBranchComparisonResult =
  coroutineScope {
    launch {
      ensureAllRevisionsFetched()
    }
    getParsedChanges()
  }
