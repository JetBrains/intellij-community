// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeDetails
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeList
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModelBase
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcsUtil.VcsFileUtil.relativePath
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.pullrequest.isViewed
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.threadsComputationFlow
import org.jetbrains.plugins.github.pullrequest.data.provider.viewedStateComputationState

@ApiStatus.Experimental
interface GHPRChangeListViewModel :
  CodeReviewChangeListViewModel.WithDetails,
  CodeReviewChangeListViewModel.WithGrouping {

  companion object {
    val DATA_KEY: DataKey<GHPRChangeListViewModel> = DataKey.create("GitHub.PullRequest.Details.Changes.List.ViewModel")
  }
}

/**
 * A change list of the cumulative pull request diff.
 *
 * The viewed state describes a file as a whole and is only reported for the cumulative diff, so a commit-scoped change
 * list does not expose it at all - only this view model implements [CodeReviewChangeListViewModel.WithViewedState].
 */
@ApiStatus.Experimental
interface GHPRCumulativeChangeListViewModel :
  GHPRChangeListViewModel,
  CodeReviewChangeListViewModel.WithViewedState

internal abstract class GHPRChangeListViewModelBase(
  parentCs: CoroutineScope,
  override val project: Project,
  private val dataContext: GHPRDataContext,
  protected val dataProvider: GHPRDataProvider,
  changeList: CodeReviewChangeList,
  private val openPullRequestDiff: (GHPRIdentifier?, Boolean) -> Unit,
) : GHPRChangeListViewModel, CodeReviewChangeListViewModelBase(parentCs, changeList) {
  private val preferences = GithubPullRequestsProjectUISettings.getInstance(project)
  protected val repository: GitRepository get() = dataContext.repositoryDataService.remoteCoordinates.repository

  protected val unresolvedThreadsCountByChange: Flow<Map<RefComparisonChange, Int>> =
    dataProvider.reviewData.threadsComputationFlow
      .filter { !it.isInProgress }.map { it.getOrNull().orEmpty() }
      .map { threads ->
        val unresolvedThreadsByPath = threads.asSequence().filter { !it.isResolved }.groupingBy { it.path }.eachCount()
        changes.associateWith { unresolvedThreadsByPath[relativePath(repository.root, it.filePath)] ?: 0 }
      }

  override val grouping: StateFlow<Set<String>> = preferences.changesGroupingState

  override fun showDiffPreview() {
    openPullRequestDiff(dataProvider.id, true)
  }

  override fun showDiff() {
    // TODO: show standalone
    showDiffPreview()
    /*val requestChain = dataProvider.diffRequestModel.requestChain ?: return
    DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.DEFAULT)*/
  }

  override fun setGrouping(grouping: Collection<String>) {
    preferences.changesGrouping = grouping.toSet()
  }
}

internal class GHPRCommitChangeListViewModelImpl(
  parentCs: CoroutineScope,
  project: Project,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider,
  changeList: CodeReviewChangeList,
  openPullRequestDiff: (GHPRIdentifier?, Boolean) -> Unit,
) : GHPRChangeListViewModelBase(parentCs, project, dataContext, dataProvider, changeList, openPullRequestDiff) {
  override val detailsByChange: StateFlow<Map<RefComparisonChange, CodeReviewChangeDetails>> =
    unresolvedThreadsCountByChange.map { threadsCount ->
      threadsCount.mapValues { (_, discussions) -> CodeReviewChangeDetails(isRead = true, discussions = discussions) }
    }.stateIn(cs, SharingStarted.Eagerly, emptyMap())
}

internal class GHPRCumulativeChangeListViewModelImpl(
  parentCs: CoroutineScope,
  project: Project,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider,
  changeList: CodeReviewChangeList,
  openPullRequestDiff: (GHPRIdentifier?, Boolean) -> Unit,
) : GHPRChangeListViewModelBase(parentCs, project, dataContext, dataProvider, changeList, openPullRequestDiff),
    GHPRCumulativeChangeListViewModel {
  private val viewedStateData = dataProvider.viewedStateData

  override val detailsByChange: StateFlow<Map<RefComparisonChange, CodeReviewChangeDetails>> =
    combine(
      unresolvedThreadsCountByChange,
      viewedStateData.viewedStateComputationState.filter { !it.isInProgress }.map { it.getOrNull().orEmpty() },
    ) { threadsCount, viewedStateByPath ->
      changes.associateWith { change ->
        val isRead = viewedStateByPath[relativePath(repository.root, change.filePath)]?.isViewed() ?: true
        CodeReviewChangeDetails(isRead, threadsCount[change] ?: 0)
      }
    }.stateIn(cs, SharingStarted.Eagerly, emptyMap())

  @RequiresEdt
  override fun setViewedState(changes: Iterable<RefComparisonChange>, viewed: Boolean) {
    cs.launchNow {
      val paths = changes.map { relativePath(repository.root, it.filePath) }
      // TODO: handle error
      viewedStateData.updateViewedState(paths, viewed)
    }
  }
}
