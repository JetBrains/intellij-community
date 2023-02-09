// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.RequestState
import com.intellij.collaboration.ui.codereview.details.ReviewRole
import com.intellij.collaboration.ui.codereview.details.ReviewState
import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher

internal interface GitLabMergeRequestReviewFlowViewModel {
  val isBusy: StateFlow<Boolean>

  val currentUser: GitLabUserDTO
  val author: GitLabUserDTO

  val approvedBy: Flow<List<GitLabUserDTO>>
  val reviewers: StateFlow<List<GitLabUserDTO>>
  val role: Flow<ReviewRole>
  val requestState: Flow<RequestState>
  val isApproved: StateFlow<Boolean>
  val reviewState: Flow<ReviewState>
  val reviewerAndReviewState: Flow<Map<GitLabUserDTO, ReviewState>>

  fun merge()

  fun squashAndMerge()

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

internal class GitLabMergeRequestReviewFlowViewModelImpl(
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

  override val requestState: Flow<RequestState> = mergeRequest.requestState

  override val isApproved: StateFlow<Boolean> = approvedBy
    .map { it.isNotEmpty() }
    .stateIn(scope, SharingStarted.Lazily, false)

  override val reviewState: Flow<ReviewState> = isApproved.map { isApproved ->
    // TODO: add ReviewState.WAIT_FOR_UPDATES state
    if (isApproved) ReviewState.ACCEPTED else ReviewState.NEED_REVIEW
  }

  override val reviewerAndReviewState: Flow<Map<GitLabUserDTO, ReviewState>> = combine(reviewers, approvedBy) { reviewers, approvedBy ->
    mutableMapOf<GitLabUserDTO, ReviewState>().apply {
      reviewers.forEach { reviewer -> put(reviewer, ReviewState.NEED_REVIEW) }
      approvedBy.forEach { reviewer -> put(reviewer, ReviewState.ACCEPTED) }
      // TODO: implement ReviewState.WAIT_FOR_UPDATES
    }
  }

  override fun merge() = runAction {
    mergeRequest.merge()
  }

  override fun squashAndMerge() = runAction {
    mergeRequest.squashAndMerge()
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

  override suspend fun getPotentialReviewers(): List<GitLabUserDTO> {
    return projectData.getMembers()
      .filter { member -> isValidMergeRequestAccessLevel(member.accessLevel) }
      .map { member -> member.user }
  }

  companion object {
    private fun isValidMergeRequestAccessLevel(accessLevel: GitLabAccessLevel): Boolean {
      return accessLevel == GitLabAccessLevel.REPORTER ||
             accessLevel == GitLabAccessLevel.DEVELOPER ||
             accessLevel == GitLabAccessLevel.MAINTAINER ||
             accessLevel == GitLabAccessLevel.OWNER
    }
  }
}