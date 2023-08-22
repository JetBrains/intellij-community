// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStatusViewModelImpl

@ApiStatus.Experimental
interface GHPRDetailsViewModel : CodeReviewDetailsViewModel {
  val branchesVm: GHPRBranchesViewModel
  val changesVm: GHPRChangesViewModel
  val statusVm: GHPRStatusViewModelImpl
  val reviewFlowVm: GHPRReviewFlowViewModelImpl
}

internal class GHPRDetailsViewModelImpl (
  project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider,
  details: GHPullRequest
) : GHPRDetailsViewModel {
  private val cs = parentCs.childScope()

  private val detailsState = MutableStateFlow(details)

  override val number: String = "#${detailsState.value.number}"
  override val url: String = detailsState.value.url

  override val title: SharedFlow<String> = detailsState.map { it.title }
    .shareIn(cs, SharingStarted.Lazily, 1)

  override val description: SharedFlow<String> = detailsState.map { it.body }
    .shareIn(cs, SharingStarted.Lazily, 1)

  override val reviewRequestState: SharedFlow<ReviewRequestState> = detailsState.map { details ->
    if (details.isDraft) return@map ReviewRequestState.DRAFT
    when (details.state) {
      GHPullRequestState.CLOSED -> ReviewRequestState.CLOSED
      GHPullRequestState.MERGED -> ReviewRequestState.MERGED
      GHPullRequestState.OPEN -> ReviewRequestState.OPENED
    }
  }.shareIn(cs, SharingStarted.Lazily, 1)

  private val commitsLoadingModel = GHCompletableFutureLoadingModel<List<GHCommit>>(cs.nestedDisposable())

  init {
    dataProvider.changesData.loadCommitsFromApi(cs.nestedDisposable()) {
      commitsLoadingModel.future = it
    }
  }

  override val branchesVm = GHPRBranchesViewModel(cs, project, dataContext.repositoryDataService.repositoryMapping, detailsState)

  override val changesVm = GHPRChangesViewModelImpl(cs, project, dataContext, dataProvider)

  override val statusVm = GHPRStatusViewModelImpl(cs, project, detailsState, dataProvider.stateData)

  override val reviewFlowVm =
    GHPRReviewFlowViewModelImpl(cs,
                                project,
                                detailsState,
                                dataContext.repositoryDataService,
                                dataContext.securityService,
                                dataContext.avatarIconsProvider,
                                dataProvider.detailsData,
                                dataProvider.stateData,
                                dataProvider.changesData,
                                dataProvider.reviewData)

  fun update(details: GHPullRequest) {
    detailsState.value = details
  }

  suspend fun destroy() = cs.cancelAndJoinSilently()
}