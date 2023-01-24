// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.mergerequest.api.request.GitLabMergeRequestNewState
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestApprove
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestUnApprove
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestUpdate

private val LOG = logger<GitLabMergeRequest>()

internal interface GitLabMergeRequest {
  val title: Flow<String>
  val description: Flow<String>
  val targetBranch: Flow<String>
  val sourceBranch: Flow<String>
  val hasConflicts: Flow<Boolean>
  val state: Flow<GitLabMergeRequestState>
  val approvedBy: Flow<List<GitLabUserDTO>>
  val reviewers: Flow<List<GitLabUserDTO>>

  val number: String
  val url: String
  val author: GitLabUserDTO

  suspend fun approve()

  suspend fun unApprove()

  suspend fun close()

  suspend fun reopen()
}

internal class LoadedGitLabMergeRequest(
  parentScope: CoroutineScope,
  connection: GitLabProjectConnection,
  mergeRequest: GitLabMergeRequestDTO
) : GitLabMergeRequest {
  private val scope = parentScope.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })
  private val api = connection.apiClient
  private val project = connection.repo.repository

  private val mergeRequestState: MutableStateFlow<GitLabMergeRequestDTO> = MutableStateFlow(mergeRequest)

  override val title: Flow<String> = mergeRequestState.map { it.title }
  override val description: Flow<String> = mergeRequestState.map { it.description }
  override val targetBranch: Flow<String> = mergeRequestState.map { it.targetBranch }
  override val sourceBranch: Flow<String> = mergeRequestState.map { it.sourceBranch }
  override val hasConflicts: Flow<Boolean> = mergeRequestState.map { it.conflicts }
  override val state: Flow<GitLabMergeRequestState> = mergeRequestState.map { it.state }
  override val approvedBy: Flow<List<GitLabUserDTO>> = mergeRequestState.map { it.approvedBy }
  override val reviewers: Flow<List<GitLabUserDTO>> = mergeRequestState.map { it.reviewers }

  override val number: String = mergeRequest.iid
  override val url: String = mergeRequest.webUrl
  override val author: GitLabUserDTO = mergeRequest.author

  override suspend fun approve() {
    withContext(scope.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestApprove(project, mergeRequestState.value)
      mergeRequestState.value = updatedMergeRequest.body() // TODO: check status code?
    }
  }

  override suspend fun unApprove() {
    withContext(scope.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestUnApprove(project, mergeRequestState.value)
      mergeRequestState.value = updatedMergeRequest.body() // TODO: check status code?
    }
  }

  override suspend fun close() {
    withContext(scope.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestUpdate(project, mergeRequestState.value, GitLabMergeRequestNewState.CLOSED)
        .getResultOrThrow()
      mergeRequestState.value = updatedMergeRequest
    }
  }

  override suspend fun reopen() {
    withContext(scope.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestUpdate(project, mergeRequestState.value, GitLabMergeRequestNewState.OPEN)
        .getResultOrThrow()
      mergeRequestState.value = updatedMergeRequest
    }
  }
}