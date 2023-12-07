// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.ReviewMergeCommitMessageDialog
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewFlowViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabCiJobDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabReviewerDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCiJobStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.data.reviewState
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestSubmitReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestSubmitReviewViewModel.SubmittableReview
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestSubmitReviewViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.getSubmittableReview
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestReviewersUtil
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal interface GitLabMergeRequestReviewFlowViewModel : CodeReviewFlowViewModel<GitLabReviewerDTO> {
  val isBusy: Flow<Boolean>

  val allowsMultipleReviewers: Flow<Boolean>

  val currentUser: GitLabUserDTO
  val author: GitLabUserDTO

  val reviewRequestState: SharedFlow<ReviewRequestState>
  val reviewers: StateFlow<List<GitLabReviewerDTO>>
  val reviewState: SharedFlow<ReviewState>
  val role: SharedFlow<ReviewRole>

  val userCanApprove: SharedFlow<Boolean>
  val userCanManage: SharedFlow<Boolean>
  val userCanMerge: SharedFlow<Boolean>

  val isMergeEnabled: SharedFlow<Boolean>
  val isRebaseEnabled: SharedFlow<Boolean>

  val submittableReview: SharedFlow<SubmittableReview?>
  var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)?

  //TODO: extract reviewers update VM
  val potentialReviewers: Flow<Result<List<GitLabUserDTO>>>

  /**
   * Request the start of a submission process
   */
  fun submitReview()

  fun merge()

  fun squashAndMerge()

  fun rebase()

  fun close()

  fun reopen()

  fun postReview()

  fun adjustReviewers(point: RelativePoint)

  @SinceGitLab("13.8")
  fun setMyselfAsReviewer()

  @SinceGitLab("13.8")
  fun removeReviewer(reviewer: GitLabUserDTO)

  fun reviewerRereview()
}

private val LOG = logger<GitLabMergeRequestReviewFlowViewModel>()

internal class GitLabMergeRequestReviewFlowViewModelImpl(
  private val project: Project,
  parentScope: CoroutineScope,
  override val currentUser: GitLabUserDTO,
  projectData: GitLabProject,
  private val mergeRequest: GitLabMergeRequest,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestReviewFlowViewModel {
  private val scope = parentScope.childScope()
  private val taskLauncher = SingleCoroutineLauncher(scope)

  override val isBusy: Flow<Boolean> = taskLauncher.busy

  override val allowsMultipleReviewers: Flow<Boolean> = projectData.allowsMultipleReviewers

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

  override val userCanApprove: SharedFlow<Boolean> = mergeRequest.details.map { it.userPermissions.canApprove == true }
    .modelFlow(scope, LOG)
  override val userCanManage: SharedFlow<Boolean> = mergeRequest.details.map { it.userPermissions.updateMergeRequest }
    .modelFlow(scope, LOG)
  override val userCanMerge: SharedFlow<Boolean> = mergeRequest.details.map { it.userPermissions.canMerge }
    .modelFlow(scope, LOG)

  private val isMergeable: SharedFlow<Boolean> = mergeRequest.details.map { it.isMergeable }
    .modelFlow(scope, LOG)
  private val shouldBeRebased: SharedFlow<Boolean> = mergeRequest.details.map { it.shouldBeRebased }
    .modelFlow(scope, LOG)
  private val isPipelineSucceeds: SharedFlow<Boolean> = mergeRequest.details.map { details ->
    val ciJobs = details.headPipeline?.jobs ?: return@map true
    if (!details.targetProject.onlyAllowMergeIfPipelineSucceeds) return@map true
    ciJobs.areAllJobsSuccessful(details.targetProject.allowMergeOnSkippedPipeline)
  }.modelFlow(scope, LOG)

  override val isMergeEnabled: SharedFlow<Boolean> =
    combine(userCanMerge, isMergeable, isPipelineSucceeds) { userCanMerge, isMergeable, isPipelineSucceeds ->
      userCanMerge && isMergeable && isPipelineSucceeds
    }.modelFlow(scope, LOG)
  override val isRebaseEnabled: SharedFlow<Boolean> =
    combine(userCanMerge, shouldBeRebased, isPipelineSucceeds) { userCanMerge, shouldBeRebased, isPipelineSucceeds ->
      userCanMerge && shouldBeRebased && isPipelineSucceeds
    }.modelFlow(scope, LOG)

  override val submittableReview: SharedFlow<SubmittableReview?> = mergeRequest.getSubmittableReview(currentUser).modelFlow(scope, LOG)
  override var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)? = null

  override val potentialReviewers: Flow<Result<List<GitLabUserDTO>>> = projectData.members

  override fun submitReview() {
    scope.launch {
      check(submittableReview.first() != null)
      val handler = submitReviewInputHandler
      check(handler != null)
      val ctx = currentCoroutineContext()
      val vm = GitLabMergeRequestSubmitReviewViewModelImpl(this, mergeRequest, currentUser) {
        ctx.cancel()
      }
      handler.invoke(vm)
    }
  }

  override fun merge() = runAction {
    val details = mergeRequest.details.first()
    val title = details.title
    val sourceBranch = details.sourceBranch
    val targetBranch = details.targetBranch
    val commitMessage: String? = withContext(scope.coroutineContext + Dispatchers.EDT) {
      val dialog = ReviewMergeCommitMessageDialog(
        project,
        CollaborationToolsBundle.message("dialog.review.merge.commit.title"),
        GitLabBundle.message("dialog.review.merge.commit.message.placeholder", sourceBranch, targetBranch),
        title
      )

      if (!dialog.showAndGet()) {
        return@withContext null
      }

      return@withContext dialog.message
    }

    if (commitMessage == null) return@runAction
    mergeRequest.merge(commitMessage)
  }

  override fun squashAndMerge() = runAction {
    val details = mergeRequest.details.first()
    val sourceBranch = details.sourceBranch
    val targetBranch = details.targetBranch
    val commits = mergeRequest.changes.first().commits.await()
    val commitMessage: String? = withContext(scope.coroutineContext + Dispatchers.EDT) {
      val body = "* " + StringUtil.join(commits, { it.fullTitle }, "\n\n* ")
      val dialog = ReviewMergeCommitMessageDialog(
        project,
        CollaborationToolsBundle.message("dialog.review.merge.commit.title.with.squash"),
        GitLabBundle.message("dialog.review.merge.commit.message.placeholder", sourceBranch, targetBranch),
        body
      )

      if (!dialog.showAndGet()) {
        return@withContext null
      }

      return@withContext dialog.message
    }

    if (commitMessage == null) return@runAction
    mergeRequest.squashAndMerge(commitMessage)
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

  override fun adjustReviewers(point: RelativePoint) {
    scope.launchNow(Dispatchers.Main) {
      val allowsMultipleReviewers = allowsMultipleReviewers.first()
      val originalReviewersIds = reviewers.value.mapTo(mutableSetOf<String>(), GitLabUserDTO::id)
      val updatedReviewers = if (allowsMultipleReviewers == true)
        GitLabMergeRequestReviewersUtil.selectReviewers(point, originalReviewersIds, potentialReviewers, avatarIconsProvider)
      else
        GitLabMergeRequestReviewersUtil.selectReviewer(point, originalReviewersIds, potentialReviewers, avatarIconsProvider)

      updatedReviewers ?: return@launchNow
      setReviewers(updatedReviewers)
    }
  }

  @SinceGitLab("13.8")
  override fun setMyselfAsReviewer() = runAction {
    val allowsMultipleReviewers = allowsMultipleReviewers.first()
    if (allowsMultipleReviewers == true) {
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

  @SinceGitLab("13.8")
  private fun setReviewers(reviewers: List<GitLabUserDTO>) = runAction {
    mergeRequest.setReviewers(reviewers)
  }

  private fun runAction(action: suspend () -> Unit) {
    taskLauncher.launch {
      try {
        action()
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        //TODO: handle???
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

    private fun List<GitLabCiJobDTO>.areAllJobsSuccessful(allowSkippedJob: Boolean): Boolean {
      val jobs = this
      val requiredJobs = jobs.filterNot { it.allowFailure ?: false }.let { jobs ->
        if (allowSkippedJob) jobs.filterNot { it.status == GitLabCiJobStatus.SKIPPED } else jobs
      }
      return requiredJobs.all { it.status == GitLabCiJobStatus.SUCCESS }
    }
  }
}