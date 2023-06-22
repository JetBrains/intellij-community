// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewDecision
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataOperationsListener
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import javax.swing.JComponent

internal class GHPRReviewFlowViewModelImpl(
  scope: CoroutineScope,
  private val metadataModel: GHPRMetadataModel,
  private val stateModel: GHPRStateModel,
  private val securityService: GHPRSecurityService,
  private val avatarIconsProvider: GHAvatarIconsProvider,
  detailsDataProvider: GHPRDetailsDataProvider,
  reviewDataProvider: GHPRReviewDataProvider,
  disposable: Disposable
) : GHPRReviewFlowViewModel {
  private val currentUser = securityService.currentUser
  private val ghostUser = securityService.ghostUser

  private val _isBusy: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isBusy: Flow<Boolean> = _isBusy.asStateFlow()

  private val mergeabilityState: MutableStateFlow<GHPRMergeabilityState?> = MutableStateFlow(stateModel.mergeabilityState)

  private val _requestedReviewersState: MutableStateFlow<List<GHPullRequestRequestedReviewer>> = MutableStateFlow(metadataModel.reviewers)
  override val requestedReviewers: Flow<List<GHPullRequestRequestedReviewer>> = _requestedReviewersState.asSharedFlow()

  private val pullRequestReviewState: MutableStateFlow<Map<GHPullRequestRequestedReviewer, GHPullRequestReviewState>> = MutableStateFlow(
    metadataModel.reviews.associate { (it.author as? GHUser ?: ghostUser) to it.state } // Collect latest review state by reviewer
  )

  override val reviewerReviews: StateFlow<Map<GHPullRequestRequestedReviewer, ReviewState>> =
    combineState(scope, pullRequestReviewState, _requestedReviewersState) { reviews, requestedReviewers ->
      mutableMapOf<GHPullRequestRequestedReviewer, ReviewState>().apply {
        reviews
          .filter { (reviewer, pullRequestReviewState) ->
            reviewer != metadataModel.getAuthor() && (pullRequestReviewState == GHPullRequestReviewState.APPROVED ||
                                                      pullRequestReviewState == GHPullRequestReviewState.CHANGES_REQUESTED)
          }
          .forEach { (reviewer, pullRequestReviewState) ->
            val reviewState = if (pullRequestReviewState == GHPullRequestReviewState.APPROVED) ReviewState.ACCEPTED
            else ReviewState.WAIT_FOR_UPDATES
            put(reviewer, reviewState)
          }

        requestedReviewers.forEach { requestedReviewer ->
          put(requestedReviewer, ReviewState.NEED_REVIEW)
        }
      }
    }

  private val reviewDecision: MutableStateFlow<GHPullRequestReviewDecision?> = MutableStateFlow(null)
  override val reviewState: Flow<ReviewState> = reviewDecision.map { reviewDecision ->
    when (reviewDecision) {
      GHPullRequestReviewDecision.APPROVED -> ReviewState.ACCEPTED
      GHPullRequestReviewDecision.CHANGES_REQUESTED -> ReviewState.WAIT_FOR_UPDATES
      GHPullRequestReviewDecision.REVIEW_REQUIRED -> ReviewState.NEED_REVIEW
      null -> ReviewState.NEED_REVIEW
    }
  }

  override val role: Flow<ReviewRole> = reviewerReviews.map { reviewerAndReview ->
    when {
      currentUser == metadataModel.getAuthor() -> ReviewRole.AUTHOR
      reviewerAndReview.containsKey(currentUser) -> ReviewRole.REVIEWER
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
    val newReviewers = metadataModel.reviewers.toMutableList().apply {
      remove(reviewer)
    }
    val delta = CollectionDelta(metadataModel.reviewers, newReviewers)
    metadataModel.adjustReviewers(EmptyProgressIndicator(), delta)
  }

  override fun requestReview(parentComponent: JComponent) = stateModel.submitTask {
    val reviewers = (reviewerReviews.value.keys + metadataModel.reviewers).toList()
    GHUIUtil.showChooserPopup(
      parentComponent,
      GHUIUtil.SelectionPresenters.PRReviewers(avatarIconsProvider),
      reviewers,
      metadataModel.loadPotentialReviewers()
    ).thenAccept { selectedReviewers ->
      metadataModel.adjustReviewers(EmptyProgressIndicator(), selectedReviewers)
    }
  }

  override fun reRequestReview() = stateModel.submitTask {
    val delta = CollectionDelta(metadataModel.reviewers, reviewerReviews.value.keys)
    metadataModel.adjustReviewers(EmptyProgressIndicator(), delta)
  }

  override fun setMyselfAsReviewer() = stateModel.submitTask {
    val newReviewer = securityService.currentUser as GHPullRequestRequestedReviewer
    val newReviewers = metadataModel.reviewers.toMutableList().apply {
      add(newReviewer)
    }
    val delta = CollectionDelta(metadataModel.reviewers, newReviewers)
    metadataModel.adjustReviewers(EmptyProgressIndicator(), delta)
  }

  init {
    metadataModel.addAndInvokeChangesListener {
      _requestedReviewersState.value = metadataModel.reviewers
      pullRequestReviewState.value = metadataModel.reviews.associate { (it.author as? GHUser ?: ghostUser) to it.state }
    }

    stateModel.addAndInvokeBusyStateChangedListener {
      _isBusy.value = stateModel.isBusy
    }

    stateModel.addAndInvokeMergeabilityStateLoadingResultListener {
      mergeabilityState.value = stateModel.mergeabilityState
    }

    detailsDataProvider.addDetailsLoadedListener(disposable) {
      val pullRequest = detailsDataProvider.loadedDetails!!
      _requestedReviewersState.value = pullRequest.reviewRequests.mapNotNull { it.requestedReviewer }
      pullRequestReviewState.value = pullRequest.reviews.associate { (it.author as? GHUser ?: ghostUser) to it.state }
      reviewDecision.value = pullRequest.reviewDecision
    }

    reviewDataProvider.addPendingReviewListener(disposable) {
      reviewDataProvider.loadPendingReview().thenAccept { pendingComments ->
        _pendingCommentsState.value = pendingComments?.commentsCount ?: 0
      }
    }
    reviewDataProvider.resetPendingReview()

    reviewDataProvider.messageBus
      .connect(scope)
      .subscribe(GHPRDataOperationsListener.TOPIC, object : GHPRDataOperationsListener {
        override fun onReviewsChanged() {
          detailsDataProvider.reloadDetails()
        }
      })
  }
}