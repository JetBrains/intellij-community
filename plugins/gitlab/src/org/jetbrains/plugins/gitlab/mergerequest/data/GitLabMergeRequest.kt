// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabETagUpdatableListLoader
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

private val LOG = logger<GitLabMergeRequest>()

interface GitLabMergeRequest : GitLabMergeRequestDiscussionsContainer {

  val id: GitLabMergeRequestId
  val gid: String

  val number: String
  val url: String
  val author: GitLabUserDTO

  val targetProject: StateFlow<GitLabProjectDTO>
  val sourceProject: StateFlow<GitLabProjectDTO>
  val title: Flow<String>
  val description: Flow<String>
  val descriptionHtml: Flow<String>
  val targetBranch: Flow<String>
  val sourceBranch: StateFlow<String>
  val hasConflicts: Flow<Boolean>
  val isDraft: Flow<Boolean>
  val reviewRequestState: Flow<ReviewRequestState>
  val approvedBy: Flow<List<GitLabUserDTO>>
  val reviewers: Flow<List<GitLabUserDTO>>
  val pipeline: Flow<GitLabPipelineDTO?>
  val userPermissions: StateFlow<GitLabMergeRequestPermissionsDTO>
  val changes: Flow<GitLabMergeRequestChanges>

  val isLoading: Flow<Boolean>

  val labelEvents: Flow<List<GitLabResourceLabelEventDTO>>
  val stateEvents: Flow<List<GitLabResourceStateEventDTO>>
  val milestoneEvents: Flow<List<GitLabResourceMilestoneEventDTO>>

  fun refreshData()

  suspend fun merge(commitMessage: String)

  suspend fun squashAndMerge(commitMessage: String)

  suspend fun approve()

  suspend fun unApprove()

  suspend fun close()

  suspend fun reopen()

  suspend fun postReview()

  suspend fun setReviewers(reviewers: List<GitLabUserDTO>)
}

internal class LoadedGitLabMergeRequest(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val projectMapping: GitLabProjectMapping,
  mergeRequest: GitLabMergeRequestDTO
) : GitLabMergeRequest,
    GitLabMergeRequestDiscussionsContainer {
  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val glProject = projectMapping.repository

  override val id: GitLabMergeRequestId = mergeRequest
  override val gid: String = mergeRequest.id

  override val number: String = mergeRequest.iid
  override val url: String = mergeRequest.webUrl
  override val author: GitLabUserDTO = mergeRequest.author

  private val mergeRequestRefreshRequest = MutableSharedFlow<Unit>()

  private val mergeRequestDetailsState: MutableStateFlow<GitLabMergeRequestFullDetails> =
    MutableStateFlow(GitLabMergeRequestFullDetails.fromGraphQL(mergeRequest))

  override val targetProject: StateFlow<GitLabProjectDTO> = mergeRequestDetailsState.mapState(cs) { it.targetProject }
  override val sourceProject: StateFlow<GitLabProjectDTO> = mergeRequestDetailsState.mapState(cs) { it.sourceProject }
  override val title: Flow<String> = mergeRequestDetailsState.map { it.title }
  override val description: Flow<String> = mergeRequestDetailsState.map { it.description }
  override val descriptionHtml: Flow<String> = description.map { if(it.isNotBlank()) GitLabUIUtil.convertToHtml(it) else it }
  override val targetBranch: Flow<String> = mergeRequestDetailsState.map { it.targetBranch }
  override val sourceBranch: StateFlow<String> = mergeRequestDetailsState.mapState(cs) { it.sourceBranch }
  override val hasConflicts: Flow<Boolean> = mergeRequestDetailsState.map { it.conflicts }
  override val isDraft: Flow<Boolean> = mergeRequestDetailsState.map { it.draft }
  override val reviewRequestState: Flow<ReviewRequestState> =
    combine(isDraft, mergeRequestDetailsState.map { it.state }) { isDraft, requestState ->
      if (isDraft) return@combine ReviewRequestState.DRAFT
      return@combine when (requestState) {
        GitLabMergeRequestState.CLOSED -> ReviewRequestState.CLOSED
        GitLabMergeRequestState.MERGED -> ReviewRequestState.MERGED
        GitLabMergeRequestState.OPENED -> ReviewRequestState.OPENED
        else -> ReviewRequestState.OPENED // to avoid null state
      }
    }
  override val approvedBy: Flow<List<GitLabUserDTO>> = mergeRequestDetailsState.map { it.approvedBy }
  override val reviewers: Flow<List<GitLabUserDTO>> = mergeRequestDetailsState.map { it.reviewers }
  override val pipeline: Flow<GitLabPipelineDTO?> = mergeRequestDetailsState.map { it.headPipeline }
  override val userPermissions: StateFlow<GitLabMergeRequestPermissionsDTO> = mergeRequestDetailsState.mapState(cs) { it.userPermissions }
  override val changes: Flow<GitLabMergeRequestChanges> = mergeRequestDetailsState
    .distinctUntilChangedBy(GitLabMergeRequestFullDetails::diffRefs).map {
      GitLabMergeRequestChangesImpl(project, cs, api, projectMapping, it)
    }.modelFlow(cs, LOG)

  private val stateEventsLoader =
    GitLabETagUpdatableListLoader<GitLabResourceStateEventDTO>(cs, getMergeRequestStateEventsUri(glProject, mergeRequest),
                                                               api::loadUpdatableJsonList)
  override val stateEvents = stateEventsLoader.batches.collectBatches().modelFlow(cs, LOG)

  private val labelEventsLoader =
    GitLabETagUpdatableListLoader<GitLabResourceLabelEventDTO>(cs, getMergeRequestLabelEventsUri(glProject, mergeRequest),
                                                               api::loadUpdatableJsonList)
  override val labelEvents = labelEventsLoader.batches.collectBatches().modelFlow(cs, LOG)

  private val milestoneEventsLoader =
    GitLabETagUpdatableListLoader<GitLabResourceMilestoneEventDTO>(cs, getMergeRequestMilestoneEventsUri(glProject, mergeRequest),
                                                                   api::loadUpdatableJsonList)
  override val milestoneEvents = milestoneEventsLoader.batches.collectBatches().modelFlow(cs, LOG)

  private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isLoading: Flow<Boolean> = _isLoading.asSharedFlow()

  init {
    cs.launch(Dispatchers.IO) {
      mergeRequestRefreshRequest.collectLatest {
        try {
          _isLoading.value = true
          val updatedMergeRequest = api.loadMergeRequest(glProject, mergeRequestDetailsState.value).body()!!
          mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
        }
        finally {
          _isLoading.value = false
        }
      }
    }
  }

  override fun refreshData() {
    cs.launch {
      mergeRequestRefreshRequest.emit(Unit)
    }
    stateEventsLoader.checkForUpdates()
    labelEventsLoader.checkForUpdates()
    milestoneEventsLoader.checkForUpdates()
    discussionsContainer.requestReload()
  }

  override suspend fun merge(commitMessage: String) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val mergeRequest = mergeRequestDetailsState.value
      val updatedMergeRequest = api.mergeRequestAccept(glProject,
                                                       mergeRequest,
                                                       commitMessage,
                                                       mergeRequest.commits.first().sha, // First from the list -- last commit from review
                                                       withSquash = false).getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
  }

  override suspend fun squashAndMerge(commitMessage: String) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val mergeRequest = mergeRequestDetailsState.value
      val updatedMergeRequest = api.mergeRequestAccept(glProject,
                                                       mergeRequest,
                                                       commitMessage,
                                                       mergeRequest.commits.first().sha, // First from the list -- last commit from review
                                                       withSquash = true).getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
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
    discussionsContainer.checkUpdates()
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
    discussionsContainer.checkUpdates()
  }

  override suspend fun close() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestUpdate(glProject, mergeRequestDetailsState.value, GitLabMergeRequestNewState.CLOSED)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
      stateEventsLoader.checkForUpdates()
    }
  }

  override suspend fun reopen() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestUpdate(glProject, mergeRequestDetailsState.value, GitLabMergeRequestNewState.OPEN)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
      stateEventsLoader.checkForUpdates()
    }
  }

  override suspend fun postReview() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestSetDraft(glProject, mergeRequestDetailsState.value, isDraft = false)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
  }

  override suspend fun setReviewers(reviewers: List<GitLabUserDTO>) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.mergeRequestSetReviewers(glProject, mergeRequestDetailsState.value, reviewers)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
  }

  private val discussionsContainer = GitLabMergeRequestDiscussionsContainerImpl(parentCs, api, projectMapping.repository, this)

  override val discussions: Flow<Collection<GitLabMergeRequestDiscussion>> = discussionsContainer.discussions
  override val systemNotes: Flow<Collection<GitLabNote>> = discussionsContainer.systemNotes
  override val standaloneDraftNotes: Flow<Collection<GitLabMergeRequestDraftNote>> = discussionsContainer.standaloneDraftNotes
  override val canAddNotes: Boolean = discussionsContainer.canAddNotes

  override suspend fun addNote(body: String) = discussionsContainer.addNote(body)

  override suspend fun addNote(position: GitLabDiffPositionInput, body: String) = discussionsContainer.addNote(position, body)
}

private fun <T> Flow<List<T>>.collectBatches(): Flow<List<T>> =
  transform {
    val result = mutableListOf<T>()
    collect {
      result.addAll(it)
      emit(result)
    }
  }
