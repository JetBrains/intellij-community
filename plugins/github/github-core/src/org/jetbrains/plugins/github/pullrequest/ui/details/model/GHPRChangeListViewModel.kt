// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.details.model.*
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcsUtil.VcsFileUtil.relativePath
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
  CodeReviewChangeListViewModel.WithGrouping,
  CodeReviewChangeListViewModel.WithViewedState {

  val isOnLatest: Boolean

  companion object {
    val DATA_KEY: DataKey<GHPRChangeListViewModel> = DataKey.create<GHPRChangeListViewModel>("GitHub.PullRequest.Details.Changes.List.ViewModel")
  }
}

internal class GHPRChangeListViewModelImpl(
  parentCs: CoroutineScope,
  override val project: Project,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  changes: CodeReviewChangesContainer,
  changeList: CodeReviewChangeList,
  private val openPullRequestDiff: (GHPRIdentifier?, Boolean) -> Unit,
) : GHPRChangeListViewModel, CodeReviewChangeListViewModelBase(parentCs, changeList) {
  private val preferences = GithubPullRequestsProjectUISettings.getInstance(project)
  private val repository: GitRepository get() = dataContext.repositoryDataService.remoteCoordinates.repository

  override val isOnLatest: Boolean = changeList.commitSha == null || changes.commits.size == 1

  private val viewedStateData = dataProvider.viewedStateData

  override val detailsByChange: StateFlow<Map<RefComparisonChange, CodeReviewChangeDetails>> =
    if (isOnLatest) {
      createDetailsByChangeFlow().stateIn(cs, SharingStarted.Eagerly, emptyMap())
    }
    else {
      MutableStateFlow(emptyMap())
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

  @RequiresEdt
  override fun setViewedState(changes: Iterable<RefComparisonChange>, viewed: Boolean) {
    cs.launchNow {
      val paths = changes.map { relativePath(repository.root, it.filePath) }
      // TODO: handle error
      viewedStateData.updateViewedState(paths, viewed)
    }
  }

  override fun setGrouping(grouping: Collection<String>) {
    preferences.changesGrouping = grouping.toSet()
  }

  private fun createDetailsByChangeFlow(): Flow<Map<RefComparisonChange, CodeReviewChangeDetails>> {
    val threadsFlow = dataProvider.reviewData.threadsComputationFlow
      .filter { !it.isInProgress }.map { it.getOrNull().orEmpty() }
    val viewedStateFlow = viewedStateData.viewedStateComputationState
      .filter { !it.isInProgress }.map { it.getOrNull().orEmpty() }
    return combine(threadsFlow, viewedStateFlow) { threads, viewedStateByPath ->
      val unresolvedThreadsByPath = threads.asSequence().filter { !it.isResolved }.groupingBy { it.path }.eachCount()
      changes.associateWith { change ->
        val path = relativePath(repository.root, change.filePath)
        val isRead = viewedStateByPath[path]?.isViewed() ?: true
        val discussions = unresolvedThreadsByPath[path] ?: 0
        CodeReviewChangeDetails(isRead, discussions)
      }
    }
  }
}