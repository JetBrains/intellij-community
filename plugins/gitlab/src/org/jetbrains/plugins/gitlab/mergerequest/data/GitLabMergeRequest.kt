// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.*
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
  val state: Flow<GitLabMergeRequestState>
  val approvedBy: Flow<List<GitLabUserDTO>>
  val reviewers: Flow<List<GitLabUserDTO>>

  val changes: Flow<GitLabMergeRequestChanges>

  suspend fun merge()

  suspend fun approve()

  suspend fun unApprove()

  suspend fun close()

  suspend fun reopen()

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
  private val mergeRequest: GitLabMergeRequestDTO
) : GitLabMergeRequest,
    GitLabMergeRequestDiscussionsContainer
    by GitLabMergeRequestDiscussionsContainerImpl(parentCs, api, projectMapping.repository, mergeRequest) {
  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val glProject = projectMapping.repository

  override val number: String = mergeRequest.iid
  override val url: String = mergeRequest.webUrl
  override val author: GitLabUserDTO = mergeRequest.author

  private val mergeRequestState: MutableStateFlow<GitLabMergeRequestDTO> = MutableStateFlow(mergeRequest)

  override val title: Flow<String> = mergeRequestState.map { it.title }
  override val description: Flow<String> = mergeRequestState.map { it.description }
  override val targetBranch: Flow<String> = mergeRequestState.map { it.targetBranch }
  override val sourceBranch: Flow<String> = mergeRequestState.map { it.sourceBranch }
  override val hasConflicts: Flow<Boolean> = mergeRequestState.map { it.conflicts }
  override val state: Flow<GitLabMergeRequestState> = mergeRequestState.map { it.state }
  override val approvedBy: Flow<List<GitLabUserDTO>> = mergeRequestState.map { it.approvedBy }
  override val reviewers: Flow<List<GitLabUserDTO>> = mergeRequestState.map { it.reviewers }

  override val changes: Flow<GitLabMergeRequestChanges> = mergeRequestState.map {
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
      val updatedMergeRequest = api.mergeRequestAccept(glProject, mergeRequestState.value)
        .getResultOrThrow()
      mergeRequestState.value = updatedMergeRequest
    }
  }

  override suspend fun approve() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      api.mergeRequestApprove(glProject, mergeRequestState.value)
      // TODO: update `approvedBy`
    }
  }

  override suspend fun unApprove() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      api.mergeRequestUnApprove(glProject, mergeRequestState.value)
      // TODO: update `approvedBy`
    }
  }

  override suspend fun close() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestUpdate(glProject, mergeRequestState.value, GitLabMergeRequestNewState.CLOSED)
        .getResultOrThrow()
      mergeRequestState.value = updatedMergeRequest
    }
  }

  override suspend fun reopen() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestUpdate(glProject, mergeRequestState.value, GitLabMergeRequestNewState.OPEN)
        .getResultOrThrow()
      mergeRequestState.value = updatedMergeRequest
    }
  }

  override suspend fun setReviewers(reviewers: List<GitLabUserDTO>) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestSetReviewers(glProject, mergeRequestState.value, reviewers)
        .getResultOrThrow()
      mergeRequestState.value = updatedMergeRequest
    }
  }

  override suspend fun getLabelEvents(): List<GitLabResourceLabelEventDTO> = labelEvents.await()

  override suspend fun getStateEvents(): List<GitLabResourceStateEventDTO> = stateEvents.await()

  override suspend fun getMilestoneEvents(): List<GitLabResourceMilestoneEventDTO> = milestoneEvents.await()
}
