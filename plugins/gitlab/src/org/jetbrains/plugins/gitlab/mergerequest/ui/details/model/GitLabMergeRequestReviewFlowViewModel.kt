// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.ReviewMergeCommitMessageDialog
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewFlowViewModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModel.SubmittableReview
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.getSubmittableReview
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher

internal interface GitLabMergeRequestReviewFlowViewModel : CodeReviewFlowViewModel<GitLabUserDTO> {
  val isBusy: StateFlow<Boolean>

  val currentUser: GitLabUserDTO
  val author: GitLabUserDTO

  val approvedBy: Flow<List<GitLabUserDTO>>
  val reviewers: StateFlow<List<GitLabUserDTO>>
  val role: Flow<ReviewRole>
  val reviewRequestState: Flow<ReviewRequestState>
  val isMergeable: Flow<Boolean>
  val isApproved: StateFlow<Boolean>
  val shouldBeRebased: Flow<Boolean>

  val reviewState: Flow<ReviewState>

  val userCanApprove: Flow<Boolean>
  val userCanManage: Flow<Boolean>
  val userCanMerge: Flow<Boolean>

  val submittableReview: Flow<SubmittableReview?>
  var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)?

  /**
   * Request the start of a submission process
   */
  fun submitReview()

  fun merge()

  fun squashAndMerge()

  fun rebase()

  fun approve()

  fun unApprove()

  fun close()

  fun reopen()

  fun postReview()

  fun setReviewers(reviewers: List<GitLabUserDTO>)

  fun setMyselfAsReviewer()

  fun removeReviewer(reviewer: GitLabUserDTO)

  //TODO: extract reviewers update VM
  suspend fun getPotentialReviewers(): List<GitLabUserDTO>
}

private val LOG = logger<GitLabMergeRequestReviewFlowViewModel>()

internal class GitLabMergeRequestReviewFlowViewModelImpl(
  private val project: Project,
  parentScope: CoroutineScope,
  override val currentUser: GitLabUserDTO,
  private val projectData: GitLabProject,
  private val mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestReviewFlowViewModel {
  private val scope = parentScope.childScope()
  private val taskLauncher = SingleCoroutineLauncher(scope)

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy.stateIn(scope, SharingStarted.Lazily, false)

  override val author: GitLabUserDTO = mergeRequest.author
  override val approvedBy: Flow<List<GitLabUserDTO>> = mergeRequest.approvedBy
  override val reviewers: StateFlow<List<GitLabUserDTO>> = mergeRequest.reviewers.stateIn(scope, SharingStarted.Lazily, emptyList())
  override val role: Flow<ReviewRole> = reviewers.map { reviewers ->
    when {
      author == currentUser -> ReviewRole.AUTHOR
      currentUser in reviewers -> ReviewRole.REVIEWER
      else -> ReviewRole.GUEST
    }
  }

  override val reviewRequestState: Flow<ReviewRequestState> = mergeRequest.reviewRequestState
  override val isMergeable: Flow<Boolean> = mergeRequest.isMergeable
  override val isApproved: StateFlow<Boolean> = approvedBy
    .map { it.isNotEmpty() }
    .stateIn(scope, SharingStarted.Lazily, false)
  override val shouldBeRebased: Flow<Boolean> = mergeRequest.shouldBeRebased

  override val reviewState: Flow<ReviewState> = isApproved.map { isApproved ->
    // TODO: add ReviewState.WAIT_FOR_UPDATES state
    if (isApproved) ReviewState.ACCEPTED else ReviewState.NEED_REVIEW
  }

  override val reviewerReviews: Flow<Map<GitLabUserDTO, ReviewState>> = combine(reviewers, approvedBy) { reviewers, approvedBy ->
    mutableMapOf<GitLabUserDTO, ReviewState>().apply {
      reviewers.forEach { reviewer -> put(reviewer, ReviewState.NEED_REVIEW) }
      approvedBy.forEach { reviewer -> put(reviewer, ReviewState.ACCEPTED) }
      // TODO: implement ReviewState.WAIT_FOR_UPDATES
    }
  }

  override val userCanApprove: Flow<Boolean> = mergeRequest.userPermissions.map { it.canApprove }
  override val userCanManage: Flow<Boolean> = mergeRequest.userPermissions.map { it.canMerge }
  override val userCanMerge: Flow<Boolean> = mergeRequest.userPermissions.map { it.canMerge }

  override val submittableReview: Flow<SubmittableReview?> = mergeRequest.getSubmittableReview(currentUser).modelFlow(scope, LOG)
  override var submitReviewInputHandler: (suspend (GitLabMergeRequestSubmitReviewViewModel) -> Unit)? = null

  override fun submitReview() {
    scope.launchNow {
      check(submittableReview.first() != null)
      val ctx = currentCoroutineContext()
      val vm = GitLabMergeRequestSubmitReviewViewModelImpl(this, mergeRequest, currentUser) {
        ctx.cancel()
      }
      submitReviewInputHandler?.invoke(vm)
    }
  }

  override fun merge() = runAction {
    val title = mergeRequest.title.stateIn(scope).value
    val sourceBranch = mergeRequest.sourceBranch.stateIn(scope).value
    val targetBranch = mergeRequest.targetBranch.stateIn(scope).value
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
    val sourceBranch = mergeRequest.sourceBranch.stateIn(scope).value
    val targetBranch = mergeRequest.targetBranch.stateIn(scope).value
    val changesState = mergeRequest.changes.stateIn(scope).value
    val commitMessage: String? = withContext(scope.coroutineContext + Dispatchers.EDT) {
      val body = "* " + StringUtil.join(changesState.commits, { it.fullTitle }, "\n\n* ")
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

  override fun approve() = runAction {
    mergeRequest.approve()
  }

  override fun unApprove() = runAction {
    mergeRequest.unApprove()
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

  override fun setReviewers(reviewers: List<GitLabUserDTO>) = runAction {
    mergeRequest.setReviewers(reviewers) // TODO: implement via CollectionDelta
  }

  override fun setMyselfAsReviewer() = runAction {
    mergeRequest.setReviewers(listOf(currentUser)) // TODO: implement via CollectionDelta
  }

  override fun removeReviewer(reviewer: GitLabUserDTO) = runAction {
    val newReviewers = mutableListOf<GitLabUserDTO>().apply {
      addAll(reviewers.value)
      remove(reviewer)
    }

    mergeRequest.setReviewers(newReviewers) // TODO: implement via CollectionDelta
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

  override suspend fun getPotentialReviewers(): List<GitLabUserDTO> = projectData.getMembers()
}