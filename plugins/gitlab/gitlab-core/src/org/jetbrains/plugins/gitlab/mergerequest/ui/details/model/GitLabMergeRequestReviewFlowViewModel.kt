// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewFlowViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.collaboration.util.collectIncrementallyTo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.nullize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabReviewerDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCiJobStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestFullDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.data.reviewState
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel.MergeDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestSubmitReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestSubmitReviewViewModel.SubmittableReview
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestSubmitReviewViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.getSubmittableReview

@ApiStatus.Internal
interface GitLabMergeRequestReviewFlowViewModel : CodeReviewFlowViewModel<GitLabReviewerDTO> {
  val project: Project

  val number: @NlsSafe String

  val isBusy: StateFlow<Boolean>

  val allowsMultipleReviewers: Flow<Boolean>

  val currentUser: GitLabUserDTO
  val author: GitLabUserDTO
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  val reviewRequestState: SharedFlow<ReviewRequestState>
  val reviewers: StateFlow<List<GitLabReviewerDTO>>
  val reviewState: SharedFlow<ReviewState>
  val role: SharedFlow<ReviewRole>

  val userCanApprove: SharedFlow<Boolean>
  val userCanManage: SharedFlow<Boolean>

  val isRebaseEnabled: SharedFlow<Boolean>

  val submittableReview: SharedFlow<SubmittableReview?>
  var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)?

  val projectMembers: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>>

  val mergeDetails: StateFlow<MergeDetails?>

  /**
   * Request the start of a submission process
   */
  fun submitReview()

  fun merge(mergeCommitMessage: String, removeSourceBranch: Boolean)

  fun squashAndMerge(mergeCommitMessage: String, removeSourceBranch: Boolean, squashCommitMessage: String)

  fun rebase()

  fun close()

  fun reopen()

  fun postReview()

  fun setMyselfAsReviewer()

  fun setReviewers(reviewers: List<GitLabUserDTO>)

  @SinceGitLab("13.8")
  fun removeReviewer(reviewer: GitLabUserDTO)

  fun reviewerRereview()

  data class MergeDetails(
    val canMerge: Boolean,
    val ffOnlyMerge: Boolean,
    val targetBranch: String,
    val sourceBranch: String,
    val mergeCommitMessageDefault: String?,
    val removeSourceBranch: Boolean,
    val squashCommits: Boolean,
    val squashCommitsReadonly: Boolean,
    val squashCommitMessageDefault: String?,
  )
}

private val LOG = logger<GitLabMergeRequestReviewFlowViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestReviewFlowViewModelImpl(
  override val project: Project,
  parentScope: CoroutineScope,
  override val currentUser: GitLabUserDTO,
  projectData: GitLabProject,
  private val mergeRequest: GitLabMergeRequest,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
) : GitLabMergeRequestReviewFlowViewModel {
  private val scope = parentScope.childScope(this::class)
  private val taskLauncher = SingleCoroutineLauncher(scope)

  override val number: @NlsSafe String = "!${mergeRequest.iid}"

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val allowsMultipleReviewers: Flow<Boolean> = suspend {
    try {
      projectData.isMultipleReviewersAllowed()
    }
    catch (e: Exception) {
      false
    }
  }.asFlow()
  override val author: GitLabUserDTO = mergeRequest.author

  override val reviewRequestState: SharedFlow<ReviewRequestState> = mergeRequest.details.map { it.reviewState }
    .modelFlow(scope, LOG)
  override val reviewers: StateFlow<List<GitLabReviewerDTO>> = mergeRequest.details.mapState(scope) {
    it.reviewers
  }
  override val reviewerReviews: Flow<Map<GitLabReviewerDTO, ReviewState>> = reviewers.map { reviewers ->
    reviewers.associateWith { it.mergeRequestInteraction.toReviewState() }
  }

  private val isApproved: SharedFlow<Boolean> = combine(mergeRequest.details, reviewerReviews) { details, reviews ->
    val approvalsRequired = details.approvalsRequired
    val approvedReviews = reviews.count { it.value == ReviewState.ACCEPTED }
    return@combine if (approvalsRequired == 0) approvedReviews > 0 else approvedReviews >= approvalsRequired
  }.modelFlow(scope, LOG)

  override val reviewState: SharedFlow<ReviewState> = combine(reviewerReviews, isApproved) { reviewerReviews, isApproved ->
    val reviewStates = reviewerReviews.values
    when {
      isApproved -> ReviewState.ACCEPTED
      reviewStates.any { it == ReviewState.WAIT_FOR_UPDATES } -> ReviewState.WAIT_FOR_UPDATES
      else -> ReviewState.NEED_REVIEW
    }
  }.modelFlow(scope, LOG)
  override val role: SharedFlow<ReviewRole> = reviewers.map { reviewers ->
    when {
      author.username == currentUser.username -> ReviewRole.AUTHOR
      reviewers.any { it.username == currentUser.username } -> ReviewRole.REVIEWER
      else -> ReviewRole.GUEST
    }
  }.modelFlow(scope, LOG)

  override val userCanApprove: SharedFlow<Boolean> = mergeRequest.details.map { it.userPermissions.canApprove ?: true }
    .modelFlow(scope, LOG)
  override val userCanManage: SharedFlow<Boolean> = mergeRequest.details.map { it.userPermissions.updateMergeRequest }
    .modelFlow(scope, LOG)

  override val isRebaseEnabled: SharedFlow<Boolean> = mergeRequest.details.map {
    it.userPermissions.canMerge && it.shouldBeRebased
  }.modelFlow(scope, LOG)

  override val submittableReview: SharedFlow<SubmittableReview?> = mergeRequest.getSubmittableReview(currentUser).modelFlow(scope, LOG)
  override var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)? = null

  override val projectMembers: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>> =
    projectData.dataReloadSignal.withInitial(Unit).transformLatest {
      projectData.getMembersBatches().collectIncrementallyTo(this)
    }.stateIn(scope, SharingStarted.Lazily, IncrementallyComputedValue.loading())

  override val mergeDetails = mergeRequest.details.map { details ->
    if (!details.userPermissions.canMerge) return@map null
    MergeDetails(
      canMerge = details.isMergeable && !details.isMergeBlockedByPipeline,
      ffOnlyMerge = details.ffOnlyMerge,
      targetBranch = details.targetBranch,
      sourceBranch = details.sourceBranch,
      mergeCommitMessageDefault = details.defaultMergeCommitMessage,
      removeSourceBranch = details.removeSourceBranch,
      squashCommits = details.shouldSquashWithProject,
      squashCommitsReadonly = details.shouldSquashReadOnly,
      squashCommitMessageDefault = details.defaultSquashCommitMessage
    )
  }.stateInNow(scope, null)

  override fun submitReview() {
    scope.launch {
      val review = submittableReview.first()
      check(review != null)
      val handler = submitReviewInputHandler
      check(handler != null)
      val ctx = currentCoroutineContext()
      val vm = GitLabMergeRequestSubmitReviewViewModelImpl(this, mergeRequest, currentUser, review) {
        ctx.cancel()
      }
      handler.invoke(vm)
    }
  }

  override fun merge(mergeCommitMessage: String, removeSourceBranch: Boolean) = runAction {
    mergeRequest.merge(mergeCommitMessage.nullize(), removeSourceBranch)
  }

  override fun squashAndMerge(mergeCommitMessage: String, removeSourceBranch: Boolean, squashCommitMessage: String) = runAction {
    mergeRequest.squashAndMerge(mergeCommitMessage.nullize(), removeSourceBranch, squashCommitMessage.nullize())
  }

  override fun rebase() = runAction {
    mergeRequest.rebase()
  }

  override fun close() = runAction {
    mergeRequest.close()
  }

  override fun reopen() = runAction {
    mergeRequest.reopen()
  }

  override fun postReview() = runAction {
    mergeRequest.postReview()
  }

  override fun setMyselfAsReviewer() = runAction {
    val allowsMultipleReviewers = allowsMultipleReviewers.first()
    if (allowsMultipleReviewers) {
      mergeRequest.setReviewers(listOf(currentUser) + reviewers.value)
    }
    else {
      mergeRequest.setReviewers(listOf(currentUser))
    }
  }

  @SinceGitLab("13.8")
  override fun removeReviewer(reviewer: GitLabUserDTO) = runAction {
    val newReviewers = reviewers.first().toMutableList()
    newReviewers.removeIf { it.id == reviewer.id }
    mergeRequest.setReviewers(newReviewers)
  }

  override fun reviewerRereview() = runAction {
    val requestedReviewers = reviewerReviews.first().filterValues { it == ReviewState.WAIT_FOR_UPDATES }.keys
    mergeRequest.reviewerRereview(requestedReviewers)
  }

  override fun setReviewers(reviewers: List<GitLabUserDTO>) = runAction {
    mergeRequest.setReviewers(reviewers)
  }

  private fun runAction(action: suspend () -> Unit) {
    taskLauncher.launch {
      try {
        action()
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        LOG.warn("Failed to run action", e)
      }
    }
  }

  companion object {
    fun GitLabReviewerDTO.MergeRequestInteraction?.toReviewState(): ReviewState {
      if (this == null) return ReviewState.NEED_REVIEW
      return when {
        approved -> ReviewState.ACCEPTED
        reviewed -> ReviewState.WAIT_FOR_UPDATES
        else -> ReviewState.NEED_REVIEW
      }
    }
  }
}

private val GitLabMergeRequestFullDetails.isMergeBlockedByPipeline: Boolean
  get() {
    if (!onlyAllowMergeIfPipelineSucceeds) return false

    return headPipeline?.jobs?.any {
      val mandatory = it.allowFailure ?: true
      if (!mandatory) return@any false

      val skippedWhenNotAllowed = it.status == GitLabCiJobStatus.SKIPPED && !allowMergeOnSkippedPipeline
      val notSuccessful = it.status != GitLabCiJobStatus.SUCCESS
      skippedWhenNotAllowed || notSuccessful
    } ?: false
  }