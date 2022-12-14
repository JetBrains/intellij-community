// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.combineState
import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel.ReviewState

class GHPRReviewFlowViewModelImpl(
  scope: CoroutineScope,
  metadataModel: GHPRMetadataModel,
  securityService: GHPRSecurityService,
  detailsDataProvider: GHPRDetailsDataProvider,
  disposable: Disposable
) : GHPRReviewFlowViewModel {
  private val currentUser = securityService.currentUser
  private val ghostUser = securityService.ghostUser

  private val _requestedReviewersState: MutableStateFlow<List<GHPullRequestRequestedReviewer>> = MutableStateFlow(metadataModel.reviewers)
  override val requestedReviewersState: StateFlow<List<GHPullRequestRequestedReviewer>> = _requestedReviewersState.asStateFlow()

  private val pullRequestReviewState: MutableStateFlow<Map<GHPullRequestRequestedReviewer, GHPullRequestReviewState>> = MutableStateFlow(
    metadataModel.reviews.associate { (it.author as? GHUser ?: ghostUser) to it.state } // Collect latest review state by reviewer
  )

  override val reviewerAndReviewState: StateFlow<Map<GHPullRequestRequestedReviewer, ReviewState>> =
    combineState(scope, pullRequestReviewState, requestedReviewersState) { reviews, requestedReviewers ->
      mutableMapOf<GHPullRequestRequestedReviewer, ReviewState>().apply {
        requestedReviewers.forEach { requestedReviewer ->
          put(requestedReviewer, ReviewState.NEED_REVIEW)
        }

        reviews
          .filter { (reviewer, _) -> reviewer != metadataModel.getAuthor() }
          .forEach { (reviewer, pullRequestReviewState) ->
            put(reviewer, convertPullRequestReviewState(pullRequestReviewState))
          }
      }
    }

  private fun convertPullRequestReviewState(pullRequestReviewState: GHPullRequestReviewState): ReviewState = when (pullRequestReviewState) {
    GHPullRequestReviewState.APPROVED -> ReviewState.ACCEPTED
    GHPullRequestReviewState.CHANGES_REQUESTED,
    GHPullRequestReviewState.COMMENTED,
    GHPullRequestReviewState.DISMISSED,
    GHPullRequestReviewState.PENDING -> ReviewState.WAIT_FOR_UPDATES
  }

  init {
    metadataModel.addAndInvokeChangesListener {
      _requestedReviewersState.value = metadataModel.reviewers
      pullRequestReviewState.value = metadataModel.reviews.associate { it.author!! as GHUser to it.state }
    }

    with(detailsDataProvider) {
      addDetailsLoadedListener(disposable) {
        _requestedReviewersState.value = loadedDetails!!.reviewRequests.mapNotNull { it.requestedReviewer }
        pullRequestReviewState.value = loadedDetails!!.reviews.associate { it.author!! as GHUser to it.state }
      }
    }
  }
}