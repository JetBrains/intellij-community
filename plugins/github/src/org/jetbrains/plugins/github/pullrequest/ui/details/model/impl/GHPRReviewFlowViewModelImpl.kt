// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.asStateFlow
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.*
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataOperationsListener
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

internal class GHPRReviewFlowViewModelImpl(
  parentCs: CoroutineScope,
  detailsModel: SingleValueModel<GHPullRequest>,
  private val stateModel: GHPRStateModel,
  private val repositoryDataService: GHPRRepositoryDataService,
  private val securityService: GHPRSecurityService,
  private val avatarIconsProvider: GHAvatarIconsProvider,
  private val dataProvider: GHPRDetailsDataProvider,
  reviewDataProvider: GHPRReviewDataProvider,
  disposable: Disposable
) : GHPRReviewFlowViewModel {
  private val cs = parentCs.childScope()

  private val currentUser = securityService.currentUser
  private val ghostUser = securityService.ghostUser

  private val _isBusy: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isBusy: Flow<Boolean> = _isBusy.asStateFlow()

  private val detailsState: StateFlow<GHPullRequest> = detailsModel.asStateFlow()

  private val mergeabilityState: MutableStateFlow<GHPRMergeabilityState?> = MutableStateFlow(stateModel.mergeabilityState)

  override val requestedReviewers: StateFlow<List<GHPullRequestRequestedReviewer>> =
    detailsState.mapState(cs) { it.reviewRequests.mapNotNull(GHPullRequestReviewRequest::requestedReviewer) }

  override val reviewerReviews: StateFlow<Map<GHPullRequestRequestedReviewer, ReviewState>> = detailsState.mapState(cs) { details ->
    val author = details.author
    val reviews = details.reviews
    val reviewers = details.reviewRequests.mapNotNull(GHPullRequestReviewRequest::requestedReviewer)
    getReviewsByReviewers(author, reviews, reviewers)
  }

  override val reviewState: Flow<ReviewState> = detailsState.mapState(cs) {
    when (it.reviewDecision) {
      GHPullRequestReviewDecision.APPROVED -> ReviewState.ACCEPTED
      GHPullRequestReviewDecision.CHANGES_REQUESTED -> ReviewState.WAIT_FOR_UPDATES
      GHPullRequestReviewDecision.REVIEW_REQUIRED -> ReviewState.NEED_REVIEW
      null -> ReviewState.NEED_REVIEW
    }
  }

  override val role: Flow<ReviewRole> = detailsState.mapState(cs) { details ->
    when {
      details.isAuthor(currentUser) -> ReviewRole.AUTHOR
      details.isReviewer(currentUser) -> ReviewRole.REVIEWER
      else -> ReviewRole.GUEST
    }
  }

  private val _pendingCommentsState: MutableStateFlow<Int> = MutableStateFlow(0)
  override val pendingComments: Flow<Int> = _pendingCommentsState.asSharedFlow()

  override val userCanManageReview: Boolean = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) ||
                                              stateModel.viewerDidAuthor

  override val userCanMergeReview: Boolean = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) &&
                                             !securityService.isMergeForbiddenForProject()

  override val isMergeAllowed: Flow<Boolean> = mergeabilityState.map { mergeabilityState ->
    mergeabilityState?.canBeMerged == true && securityService.isMergeAllowed()
  }

  override val isRebaseAllowed: Flow<Boolean> = mergeabilityState.map { mergeabilityState ->
    mergeabilityState?.canBeRebased == true && securityService.isRebaseMergeAllowed()
  }

  override val isSquashMergeAllowed: Flow<Boolean> = mergeabilityState.map { mergeabilityState ->
    mergeabilityState?.canBeMerged == true && securityService.isSquashMergeAllowed()
  }

  override fun mergeReview() = stateModel.submitMergeTask()

  override fun rebaseReview() = stateModel.submitRebaseMergeTask()

  override fun squashAndMergeReview() = stateModel.submitSquashMergeTask()

  override fun closeReview() = stateModel.submitCloseTask()

  override fun reopenReview() = stateModel.submitReopenTask()

  override fun postDraftedReview() = stateModel.submitMarkReadyForReviewTask()

  override fun removeReviewer(reviewer: GHPullRequestRequestedReviewer) = stateModel.submitTask {
    val reviewers = requestedReviewers.value
    val delta = CollectionDelta(reviewers, reviewers - reviewer)
    dataProvider.adjustReviewers(EmptyProgressIndicator(), delta)
  }

  override fun requestReview(parentComponent: JComponent) = stateModel.submitTask {
    val reviewers = requestedReviewers.value + reviewerReviews.value.keys
    GHUIUtil.showChooserPopup(
      parentComponent,
      GHUIUtil.SelectionPresenters.PRReviewers(avatarIconsProvider),
      reviewers,
      loadPotentialReviewers()
    ).thenAccept { selectedReviewers ->
      dataProvider.adjustReviewers(EmptyProgressIndicator(), selectedReviewers)
    }
  }

  private fun loadPotentialReviewers(): CompletableFuture<List<GHPullRequestRequestedReviewer>> {
    val author = detailsState.value.author
    return repositoryDataService.potentialReviewers.thenApply { reviewers ->
      reviewers.mapNotNull { if (it == author) null else it }
    }
  }

  override fun reRequestReview() = stateModel.submitTask {
    val reviewers = requestedReviewers.value
    val delta = CollectionDelta(reviewers, reviewers + reviewerReviews.value.keys)
    dataProvider.adjustReviewers(EmptyProgressIndicator(), delta)
  }

  override fun setMyselfAsReviewer() = stateModel.submitTask {
    val reviewers = requestedReviewers.value
    val delta = CollectionDelta(reviewers, reviewers + securityService.currentUser)
    dataProvider.adjustReviewers(EmptyProgressIndicator(), delta)
  }

  init {
    stateModel.addAndInvokeBusyStateChangedListener {
      _isBusy.value = stateModel.isBusy
    }

    stateModel.addAndInvokeMergeabilityStateLoadingResultListener {
      mergeabilityState.value = stateModel.mergeabilityState
    }

    reviewDataProvider.addPendingReviewListener(disposable) {
      reviewDataProvider.loadPendingReview().thenAccept { pendingComments ->
        _pendingCommentsState.value = pendingComments?.commentsCount ?: 0
      }
    }
    reviewDataProvider.resetPendingReview()

    reviewDataProvider.messageBus
      .connect(parentCs)
      .subscribe(GHPRDataOperationsListener.TOPIC, object : GHPRDataOperationsListener {
        override fun onReviewsChanged() {
          dataProvider.reloadDetails()
        }
      })
  }

  private fun getReviewsByReviewers(author: GHActor?,
                                    reviews: List<GHPullRequestReview>,
                                    reviewers: List<GHPullRequestRequestedReviewer>): MutableMap<GHPullRequestRequestedReviewer, ReviewState> {
    val result = mutableMapOf<GHPullRequestRequestedReviewer, ReviewState>()
    reviews.associate { (it.author as? GHUser ?: ghostUser) to it.state } // latest review state by reviewer
      .forEach { (reviewer, reviewState) ->
        if (reviewer != author) {
          if (reviewState == GHPullRequestReviewState.APPROVED) {
            result[reviewer] = ReviewState.ACCEPTED
          }
          if (reviewState == GHPullRequestReviewState.CHANGES_REQUESTED) {
            result[reviewer] = ReviewState.WAIT_FOR_UPDATES
          }
        }
      }

    reviewers.forEach { requestedReviewer ->
      result[requestedReviewer] = ReviewState.NEED_REVIEW
    }
    return result
  }
}

private fun GHPullRequest.isAuthor(user: GHUser): Boolean =
  author?.id == user.id

private fun GHPullRequest.isReviewer(user: GHUser): Boolean =
  reviewRequests.any { it.requestedReviewer?.id == user.id }
  ||
  reviews.any { it.author?.id == user.id }