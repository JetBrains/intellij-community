// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.RequestState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceLabelEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceMilestoneEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceStateEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

private val LOG = logger<GitLabMergeRequest>()

interface GitLabMergeRequest : GitLabMergeRequestDiscussionsContainer {
  val number: String
  val url: String
  val author: GitLabUserDTO

  val title: Flow<String>
  val description: Flow<String>
  val targetBranch: Flow<String>
  val sourceBranch: Flow<String>
  val hasConflicts: Flow<Boolean>
  val isDraft: Flow<Boolean>
  val requestState: Flow<RequestState>
  val approvedBy: Flow<List<GitLabUserDTO>>
  val reviewers: Flow<List<GitLabUserDTO>>

  val changes: Flow<GitLabMergeRequestChanges>

  suspend fun merge()

  suspend fun approve()

  suspend fun unApprove()

  suspend fun close()

  suspend fun reopen()

  suspend fun postReview()

  suspend fun setReviewers(reviewers: List<GitLabUserDTO>)

  suspend fun getLabelEvents(): List<GitLabResourceLabelEventDTO>

  suspend fun getStateEvents(): List<GitLabResourceStateEventDTO>

  suspend fun getMilestoneEvents(): List<GitLabResourceMilestoneEventDTO>
}

internal class LoadedGitLabMergeRequest(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val projectMapping: GitLabProjectMapping,
  mergeRequest: GitLabMergeRequestDTO
) : GitLabMergeRequest,
    GitLabMergeRequestDiscussionsContainer
    by GitLabMergeRequestDiscussionsContainerImpl(parentCs, api, projectMapping.repository, mergeRequest) {
  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val glProject = projectMapping.repository

  override val number: String = mergeRequest.iid
  override val url: String = mergeRequest.webUrl
  override val author: GitLabUserDTO = mergeRequest.author

  private val mergeRequestDetailsState: MutableStateFlow<GitLabMergeRequestFullDetails> =
    MutableStateFlow(GitLabMergeRequestFullDetails.fromGraphQL(mergeRequest))

  override val title: Flow<String> = mergeRequestDetailsState.map { it.title }
  override val description: Flow<String> = mergeRequestDetailsState.map { it.description }
  override val targetBranch: Flow<String> = mergeRequestDetailsState.map { it.targetBranch }
  override val sourceBranch: Flow<String> = mergeRequestDetailsState.map { it.sourceBranch }
  override val hasConflicts: Flow<Boolean> = mergeRequestDetailsState.map { it.conflicts }
  override val isDraft: Flow<Boolean> = mergeRequestDetailsState.map { it.draft }
  override val requestState: Flow<RequestState> = combine(isDraft, mergeRequestDetailsState.map { it.state }) { isDraft, requestState ->
    if (isDraft) return@combine RequestState.DRAFT
    return@combine when (requestState) {
      GitLabMergeRequestState.CLOSED -> RequestState.CLOSED
      GitLabMergeRequestState.MERGED -> RequestState.MERGED
      GitLabMergeRequestState.OPENED -> RequestState.OPENED
      else -> RequestState.OPENED // to avoid null state
    }
  }
  override val approvedBy: Flow<List<GitLabUserDTO>> = mergeRequestDetailsState.map { it.approvedBy }
  override val reviewers: Flow<List<GitLabUserDTO>> = mergeRequestDetailsState.map { it.reviewers }

  override val changes: Flow<GitLabMergeRequestChanges> = mergeRequestDetailsState.map {
    GitLabMergeRequestChangesImpl(project, cs, api, projectMapping, it)
  }.modelFlow(cs, LOG)

  private val stateEvents by lazy {
    cs.async(Dispatchers.IO) { api.loadMergeRequestStateEvents(glProject, mergeRequest).body().orEmpty() }
  }

  private val labelEvents by lazy {
    cs.async(Dispatchers.IO) { api.loadMergeRequestLabelEvents(glProject, mergeRequest).body().orEmpty() }
  }

  private val milestoneEvents by lazy {
    cs.async(Dispatchers.IO) { api.loadMergeRequestMilestoneEvents(glProject, mergeRequest).body().orEmpty() }
  }

  override suspend fun merge() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val mergeRequest = mergeRequestDetailsState.value
      val updatedMergeRequest = api.mergeRequestAccept(glProject, mergeRequest, mergeRequest.commits.last().sha)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
  }

  override suspend fun approve() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val response = api.mergeRequestApprove(glProject, mergeRequestDetailsState.value)
      val statusCode = response.statusCode()
      val mergeRequest = response.body()
      if (statusCode != 201) {
        throw HttpStatusErrorException("Unable to approve Merge Request", statusCode, mergeRequest.toString())
      }

      mergeRequestDetailsState.value = mergeRequestDetailsState.value.copy(approvedBy = mergeRequest.approvedBy)
    }
  }

  override suspend fun unApprove() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val response = api.mergeRequestUnApprove(glProject, mergeRequestDetailsState.value)
      val statusCode = response.statusCode()
      val mergeRequest = response.body()
      if (statusCode != 201) {
        throw HttpStatusErrorException("Unable to unapprove Merge Request", statusCode, mergeRequest.toString())
      }

      mergeRequestDetailsState.value = mergeRequestDetailsState.value.copy(approvedBy = mergeRequest.approvedBy)
    }
  }

  override suspend fun close() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestUpdate(glProject, mergeRequestDetailsState.value, GitLabMergeRequestNewState.CLOSED)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
  }

  override suspend fun reopen() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestUpdate(glProject, mergeRequestDetailsState.value, GitLabMergeRequestNewState.OPEN)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
  }

  override suspend fun postReview() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestSetDraft(glProject, mergeRequestDetailsState.value, isDraft = false)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
  }

  override suspend fun setReviewers(reviewers: List<GitLabUserDTO>) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestSetReviewers(glProject, mergeRequestDetailsState.value, reviewers)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
  }

  override suspend fun getLabelEvents(): List<GitLabResourceLabelEventDTO> = labelEvents.await()

  override suspend fun getStateEvents(): List<GitLabResourceStateEventDTO> = stateEvents.await()

  override suspend fun getMilestoneEvents(): List<GitLabResourceMilestoneEventDTO> = milestoneEvents.await()
}
