// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.ReviewRole
import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher

internal interface GitLabMergeRequestReviewFlowViewModel {
  val isBusy: StateFlow<Boolean>

  val currentUser: GitLabUserDTO
  val author: GitLabUserDTO

  val approvedBy: Flow<List<GitLabUserDTO>>
  val reviewers: Flow<List<GitLabUserDTO>>
  val role: Flow<ReviewRole>
  val reviewState: StateFlow<GitLabMergeRequestState>
  val isApproved: StateFlow<Boolean>

  fun merge()

  fun approve()

  fun unApprove()

  fun close()

  fun reopen()

  fun setReviewers(reviewers: List<GitLabUserDTO>)

  fun setMyselfAsReviewer()

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
  override val reviewers: Flow<List<GitLabUserDTO>> = mergeRequest.reviewers
  override val role: Flow<ReviewRole> = reviewers.map { reviewers ->
    when {
      author == currentUser -> ReviewRole.AUTHOR
      currentUser in reviewers -> ReviewRole.REVIEWER
      else -> ReviewRole.GUEST
    }
  }

  override val reviewState: StateFlow<GitLabMergeRequestState> = mergeRequest.state
    .stateIn(scope, SharingStarted.Lazily, GitLabMergeRequestState.OPENED)

  override val isApproved: StateFlow<Boolean> = approvedBy
    .map { it.isNotEmpty() }
    .stateIn(scope, SharingStarted.Lazily, false)

  override fun merge() = runAction {
    mergeRequest.merge()
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

  override fun setReviewers(reviewers: List<GitLabUserDTO>) = runAction {
    mergeRequest.setReviewers(reviewers) // TODO: implement via CollectionDelta
  }

  override fun setMyselfAsReviewer() = runAction {
    mergeRequest.setReviewers(listOf(currentUser)) // TODO: implement via CollectionDelta
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