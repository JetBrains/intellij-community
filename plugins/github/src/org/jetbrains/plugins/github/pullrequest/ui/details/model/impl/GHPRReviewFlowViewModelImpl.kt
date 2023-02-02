// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.details.ReviewRole
import com.intellij.collaboration.ui.codereview.details.ReviewState
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataOperationsListener
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel

internal class GHPRReviewFlowViewModelImpl(
  scope: CoroutineScope,
  private val metadataModel: GHPRMetadataModel,
  private val stateModel: GHPRStateModel,
  securityService: GHPRSecurityService,
  detailsDataProvider: GHPRDetailsDataProvider,
  reviewDataProvider: GHPRReviewDataProvider,
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
        reviews
          .filter { (reviewer, _) -> reviewer != metadataModel.getAuthor() }
          .forEach { (reviewer, pullRequestReviewState) ->
            put(reviewer, convertPullRequestReviewState(pullRequestReviewState))
          }

        requestedReviewers.forEach { requestedReviewer ->
          put(requestedReviewer, ReviewState.NEED_REVIEW)
        }
      }
    }

  override val reviewState: StateFlow<ReviewState> = reviewerAndReviewState.mapState(scope) { reviews ->
    when {
      reviews.values.all { it == ReviewState.ACCEPTED } -> ReviewState.ACCEPTED
      reviews.values.any { it == ReviewState.WAIT_FOR_UPDATES } -> ReviewState.WAIT_FOR_UPDATES
      else -> ReviewState.NEED_REVIEW
    }
  }

  override val roleState: StateFlow<ReviewRole> = reviewerAndReviewState.mapState(scope) {
    when {
      currentUser == metadataModel.getAuthor() -> ReviewRole.AUTHOR
      reviewerAndReviewState.value.containsKey(currentUser) -> ReviewRole.REVIEWER
      else -> ReviewRole.GUEST
    }
  }

  private val _pendingCommentsState: MutableStateFlow<Int> = MutableStateFlow(0)
  override val pendingCommentsState: StateFlow<Int> = _pendingCommentsState.asStateFlow()

  override fun removeReviewer(reviewer: GHPullRequestRequestedReviewer) = stateModel.submitTask {
    val newReviewers = metadataModel.reviewers.toMutableList().apply {
      remove(reviewer)
    }
    val delta = CollectionDelta(metadataModel.reviewers, newReviewers)
    metadataModel.adjustReviewers(EmptyProgressIndicator(), delta)
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
      pullRequestReviewState.value = metadataModel.reviews.associate { (it.author as? GHUser ?: ghostUser) to it.state }
    }

    with(detailsDataProvider) {
      addDetailsLoadedListener(disposable) {
        _requestedReviewersState.value = loadedDetails!!.reviewRequests.mapNotNull { it.requestedReviewer }
        pullRequestReviewState.value = loadedDetails!!.reviews.associate { (it.author as? GHUser ?: ghostUser) to it.state }
      }
    }

    reviewDataProvider.addPendingReviewListener(disposable) {
      reviewDataProvider.loadPendingReview().thenAccept { pendingComments ->
        _pendingCommentsState.value = pendingComments?.comments?.totalCount ?: 0
      }
    }

    reviewDataProvider.messageBus
      .connect(scope)
      .subscribe(GHPRDataOperationsListener.TOPIC, object : GHPRDataOperationsListener {
        override fun onReviewsChanged() {
          detailsDataProvider.reloadDetails()
        }
      })
  }
}