// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.ReviewMergeCommitMessageDialog
import com.intellij.collaboration.ui.codereview.commits.splitCommitMessage
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.codereview.list.search.PopupConfig
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewDecision
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.mergeabilityStateComputationFlow
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.GHReviewersUtils
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.RepositoryRestrictions
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRSubmitReviewViewModel
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import javax.swing.JComponent
import kotlin.coroutines.cancellation.CancellationException

private val LOG: Logger = logger<GHPRReviewFlowViewModel>()

class GHPRReviewFlowViewModelImpl internal constructor(
  parentCs: CoroutineScope,
  private val project: Project,
  private val detailsState: StateFlow<GHPullRequest>,
  private val repositoryDataService: GHPRRepositoryDataService,
  private val securityService: GHPRSecurityService,
  private val avatarIconsProvider: GHAvatarIconsProvider,
  private val detailsData: GHPRDetailsDataProvider,
  private val changesData: GHPRChangesDataProvider,
  private val reviewVmHelper: GHPRReviewViewModelHelper
) : GHPRReviewFlowViewModel {
  private val cs = parentCs.childScope()

  private val taskLauncher = SingleCoroutineLauncher(cs)

  private val currentUser = securityService.currentUser
  private val ghostUser = securityService.ghostUser

  override val isBusy: Flow<Boolean> = taskLauncher.busy

  // TODO: handle error
  private val mergeabilityState: StateFlow<GHPRMergeabilityState?> =
    detailsData.mergeabilityStateComputationFlow.mapNotNull { it.getOrNull() }
    .stateIn(cs, SharingStarted.Eagerly, null)

  override val requestedReviewers: SharedFlow<List<GHPullRequestRequestedReviewer>> =
    detailsState.map { it.reviewRequests.mapNotNull(GHPullRequestReviewRequest::requestedReviewer) }
      .shareIn(cs, SharingStarted.Lazily, 1)

  override val reviewerReviews: SharedFlow<Map<GHPullRequestRequestedReviewer, ReviewState>> = detailsState.map { details ->
    val author = details.author
    val reviews = details.reviews
    val reviewers = details.reviewRequests.mapNotNull(GHPullRequestReviewRequest::requestedReviewer)
    GHReviewersUtils.getReviewsByReviewers(author, reviews, reviewers, ghostUser)
  }.shareIn(cs, SharingStarted.Lazily, 1)

  private val isApproved: SharedFlow<Boolean> = combine(reviewerReviews, mergeabilityState) { reviews, state ->
    val requiredApprovingReviewsCount = state?.requiredApprovingReviewsCount ?: 0
    val approvedReviews = reviews.count { it.value == ReviewState.ACCEPTED }
    return@combine if (requiredApprovingReviewsCount == 0) approvedReviews > 0 else approvedReviews >= requiredApprovingReviewsCount
  }.modelFlow(cs, LOG)

  override val reviewState: SharedFlow<ReviewState> = combine(detailsState, isApproved) { detailsState, isApproved ->
    if (isApproved) return@combine ReviewState.ACCEPTED
    return@combine when (detailsState.reviewDecision) {
      GHPullRequestReviewDecision.APPROVED -> ReviewState.ACCEPTED
      GHPullRequestReviewDecision.CHANGES_REQUESTED -> ReviewState.WAIT_FOR_UPDATES
      GHPullRequestReviewDecision.REVIEW_REQUIRED -> ReviewState.NEED_REVIEW
      null -> ReviewState.NEED_REVIEW
    }
  }.modelFlow(cs, LOG)

  override val role: SharedFlow<ReviewRole> = detailsState.map { details ->
    when {
      details.isAuthor(currentUser) -> ReviewRole.AUTHOR
      details.isReviewer(currentUser) -> ReviewRole.REVIEWER
      else -> ReviewRole.GUEST
    }
  }.shareIn(cs, SharingStarted.Lazily, 1)

  override val pendingReview: StateFlow<ComputedResult<GHPullRequestPendingReview?>> = reviewVmHelper.pendingReviewState
  override val pendingComments: Flow<Int> = pendingReview.map { it.result?.getOrNull()?.commentsCount ?: 0 }

  override val repositoryRestrictions = RepositoryRestrictions(securityService)

  override val userCanManageReview: Boolean = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) ||
                                              detailsState.value.viewerDidAuthor

  override val userCanMergeReview: Boolean = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) &&
                                             !securityService.isMergeForbiddenForProject()

  override val isMergeEnabled: Flow<Boolean> = mergeabilityState.map {
    it?.isRestricted == false && repositoryRestrictions.isMergeAllowed &&
    it.canBeMerged && userCanMergeReview
  }

  override val isSquashMergeEnabled: Flow<Boolean> = mergeabilityState.map {
    it?.isRestricted == false && repositoryRestrictions.isSquashMergeAllowed &&
    it.canBeMerged && userCanMergeReview
  }

  override val isRebaseEnabled: Flow<Boolean> = mergeabilityState.map {
    it?.isRestricted == false && repositoryRestrictions.isRebaseAllowed &&
    it.canBeRebased && userCanMergeReview
  }

  override var submitReviewInputHandler: (suspend (GHPRSubmitReviewViewModel) -> Unit)? = null

  override fun submitReview() {
    val handler = submitReviewInputHandler
    checkNotNull(handler) { "UI handler was not set" }
    reviewVmHelper.submitReview(handler)
  }

  override fun mergeReview() = runAction {
    val details = detailsState.value
    val message = withContext(Dispatchers.Main) {
      val dialog = ReviewMergeCommitMessageDialog(project,
                                                  CollaborationToolsBundle.message("dialog.review.merge.commit.title"),
                                                  GithubBundle.message("pull.request.merge.pull.request", details.number),
                                                  details.title)
      if (dialog.showAndGet()) dialog.message else null
    } ?: return@runAction
    detailsData.merge(splitCommitMessage(message), details.headRefOid)
  }

  override fun rebaseReview() = runAction {
    val details = detailsState.value
    detailsData.rebaseMerge(details.headRefOid)
  }

  override fun squashAndMergeReview() = runAction {
    val details = detailsState.value
    val commits = changesData.loadCommits()
    val body = "* " + StringUtil.join(commits, { it.messageHeadline }, "\n\n* ")
    val message = withContext(Dispatchers.Main) {
      val dialog = ReviewMergeCommitMessageDialog(project,
                                                  CollaborationToolsBundle.message("dialog.review.merge.commit.title.with.squash"),
                                                  GithubBundle.message("pull.request.merge.pull.request", details.number),
                                                  body)
      if (dialog.showAndGet()) dialog.message else null
    } ?: return@runAction
    detailsData.squashMerge(splitCommitMessage(message), details.headRefOid)
  }

  override fun closeReview() = runAction(detailsData::close)

  override fun reopenReview() = runAction(detailsData::reopen)

  override fun postDraftedReview() = runAction(detailsData::markReadyForReview)

  override fun removeReviewer(reviewer: GHPullRequestRequestedReviewer) = runAction {
    val reviewers = requestedReviewers.first()
    val delta = CollectionDelta(reviewers, reviewers - reviewer)
    detailsData.adjustReviewers(delta)
  }

  override fun requestReview(parentComponent: JComponent) = runAction {
    val reviewers = requestedReviewers.combine(reviewerReviews) { reviewers, reviews ->
      reviewers + reviews.keys
    }.first().toHashSet()
    val selectedReviewers = withContext(Dispatchers.Main) {
      val point = RelativePoint.getNorthWestOf(parentComponent)

      val author = detailsState.value.author
      val potentialReviewersLoadingFlow = flow {
        runCatching {
          repositoryDataService.loadPotentialReviewers().filter { it.id != author?.id }
        }.also {
          emit(it)
        }
      }

      ChooserPopupUtil.showAsyncMultipleChooserPopup(point,
                                                     potentialReviewersLoadingFlow,
                                                     GHUIUtil.SelectionPresenters.PRReviewers(avatarIconsProvider),
                                                     { reviewers.contains(it) },
                                                     PopupConfig(showDirection = ShowDirection.ABOVE))
    }
    val delta = CollectionDelta(reviewers, selectedReviewers)
    if (delta.isEmpty) return@runAction
    detailsData.adjustReviewers(delta)
  }

  override fun reRequestReview() = runAction {
    val delta = requestedReviewers.combine(reviewerReviews) { reviewers, reviews ->
      CollectionDelta(reviewers, reviewers + reviews.keys)
    }.first()
    detailsData.adjustReviewers(delta)
  }

  override fun setMyselfAsReviewer() = runAction {
    val reviewers = requestedReviewers.first()
    val delta = CollectionDelta(reviewers, reviewers + securityService.currentUser)
    detailsData.adjustReviewers(delta)
  }

  private fun runAction(action: suspend () -> Unit) {
    taskLauncher.launch {
      try {
        action()
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        //TODO: show error in UI
        LOG.warn(e)
      }
    }
  }
}

private fun GHPullRequest.isAuthor(user: GHUser): Boolean =
  author?.id == user.id

private fun GHPullRequest.isReviewer(user: GHUser): Boolean =
  reviewRequests.any { it.requestedReviewer?.id == user.id }
  ||
  reviews.any { it.author?.id == user.id }