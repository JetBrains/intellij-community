// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.asResultFlow
import com.intellij.collaboration.async.collectBatches
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.api.loadUpdatableJsonList
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabETagUpdatableListLoader
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<GitLabMergeRequest>()

interface GitLabMergeRequest : GitLabMergeRequestDiscussionsContainer {
  val glProject: GitLabProjectCoordinates

  val id: GitLabMergeRequestId
  val gid: String

  val number: String
  val url: String
  val author: GitLabUserDTO

  val isLoading: SharedFlow<Boolean>

  val details: StateFlow<GitLabMergeRequestFullDetails>
  val changes: SharedFlow<GitLabMergeRequestChanges>

  val labelEvents: Flow<Result<List<GitLabResourceLabelEventDTO>>>
  val stateEvents: Flow<Result<List<GitLabResourceStateEventDTO>>>
  val milestoneEvents: Flow<Result<List<GitLabResourceMilestoneEventDTO>>>

  // NOT a great place for it, but placing it in VM layer is a pain in the neck
  val draftReviewText: MutableStateFlow<String>

  fun refreshData()

  suspend fun merge(commitMessage: String)

  suspend fun squashAndMerge(commitMessage: String)

  suspend fun rebase()

  suspend fun approve()

  suspend fun unApprove()

  suspend fun close()

  suspend fun reopen()

  suspend fun postReview()

  suspend fun setReviewers(reviewers: List<GitLabUserDTO>)

  suspend fun reviewerRereview(reviewers: Collection<GitLabReviewerDTO>)
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

  override val glProject: GitLabProjectCoordinates = projectMapping.repository

  override val id: GitLabMergeRequestId = mergeRequest
  override val gid: String = mergeRequest.id

  override val number: String = mergeRequest.iid
  override val url: String = mergeRequest.webUrl
  override val author: GitLabUserDTO = mergeRequest.author

  private val mergeRequestRefreshRequest = MutableSharedFlow<Unit>()

  private val mergeRequestDetailsState: MutableStateFlow<GitLabMergeRequestFullDetails> =
    MutableStateFlow(GitLabMergeRequestFullDetails.fromGraphQL(mergeRequest))
  override val details: StateFlow<GitLabMergeRequestFullDetails> = mergeRequestDetailsState.asStateFlow()

  override val changes: SharedFlow<GitLabMergeRequestChanges> = mergeRequestDetailsState
    .distinctUntilChangedBy(GitLabMergeRequestFullDetails::diffRefs).map {
      GitLabMergeRequestChangesImpl(project, cs, api, projectMapping, it)
    }.modelFlow(cs, LOG)

  private val stateEventsLoader =
    GitLabETagUpdatableListLoader<GitLabResourceStateEventDTO>(getMergeRequestStateEventsUri(glProject, mergeRequest)
    ) { uri, eTag ->
      api.rest.loadUpdatableJsonList<GitLabResourceStateEventDTO>(
        glProject.serverPath, GitLabApiRequestName.REST_GET_MERGE_REQUEST_STATE_EVENTS, uri, eTag
      )
    }
  override val stateEvents = stateEventsLoader.batches.collectBatches()
    .asResultFlow()
    .modelFlow(cs, LOG)

  private val labelEventsLoader =
    GitLabETagUpdatableListLoader<GitLabResourceLabelEventDTO>(getMergeRequestLabelEventsUri(glProject, mergeRequest)
    ) { uri, eTag ->
      api.rest.loadUpdatableJsonList<GitLabResourceLabelEventDTO>(
        glProject.serverPath, GitLabApiRequestName.REST_GET_MERGE_REQUEST_LABEL_EVENTS, uri, eTag
      )
    }
  override val labelEvents = labelEventsLoader.batches.collectBatches()
    .asResultFlow()
    .modelFlow(cs, LOG)

  private val milestoneEventsLoader =
    GitLabETagUpdatableListLoader<GitLabResourceMilestoneEventDTO>(getMergeRequestMilestoneEventsUri(glProject, mergeRequest)
    ) { uri, eTag ->
      api.rest.loadUpdatableJsonList<GitLabResourceMilestoneEventDTO>(
        glProject.serverPath, GitLabApiRequestName.REST_GET_MERGE_REQUEST_MILESTONE_EVENTS, uri, eTag
      )
    }
  override val milestoneEvents = milestoneEventsLoader.batches.collectBatches()
    .asResultFlow()
    .modelFlow(cs, LOG)

  private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isLoading: SharedFlow<Boolean> = _isLoading.asSharedFlow()

  override val draftReviewText: MutableStateFlow<String> = MutableStateFlow("")

  init {
    cs.launch(Dispatchers.IO) {
      mergeRequestRefreshRequest.collectLatest {
        try {
          _isLoading.value = true
          val updatedMergeRequest = api.graphQL.loadMergeRequest(glProject, mergeRequestDetailsState.value).body()!!
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

      stateEventsLoader.checkForUpdates()
      labelEventsLoader.checkForUpdates()
      milestoneEventsLoader.checkForUpdates()
    }
    // TODO: make suspending
    discussionsContainer.requestReload()
  }

  private suspend fun updateData() {
    mergeRequestRefreshRequest.emit(Unit)
    stateEventsLoader.checkForUpdates()
    labelEventsLoader.checkForUpdates()
    milestoneEventsLoader.checkForUpdates()
    discussionsContainer.checkUpdates()
  }

  override suspend fun merge(commitMessage: String) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val mergeRequest = mergeRequestDetailsState.value
      val updatedMergeRequest = api.graphQL.mergeRequestAccept(glProject,
                                                               mergeRequest,
                                                               commitMessage,
                                                               mergeRequest.commits.first().sha, // First from the list -- last commit from review
                                                               withSquash = false).getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.MERGE)
  }

  override suspend fun squashAndMerge(commitMessage: String) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val mergeRequest = mergeRequestDetailsState.value
      val updatedMergeRequest = api.graphQL.mergeRequestAccept(glProject,
                                                               mergeRequest,
                                                               commitMessage,
                                                               mergeRequest.commits.first().sha, // First from the list -- last commit from review
                                                               withSquash = true).getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.SQUASH_MERGE)
  }

  override suspend fun rebase() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      api.rest.mergeRequestRebase(glProject, mergeRequestDetailsState.value)
      do {
        val updatedMergeRequest = api.graphQL.loadMergeRequest(glProject, mergeRequestDetailsState.value).body()!!
        mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
        delay(1.seconds)
      }
      while (updatedMergeRequest.rebaseInProgress)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.REBASE)
  }

  override suspend fun approve() {
    try {
      withContext(cs.coroutineContext + Dispatchers.IO) {
        api.rest.mergeRequestApprove(glProject, mergeRequestDetailsState.value)
      }
    }
    finally {
      updateData()
      GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.APPROVE)
    }
  }

  override suspend fun unApprove() {
    try {
      withContext(cs.coroutineContext + Dispatchers.IO) {
        api.rest.mergeRequestUnApprove(glProject, mergeRequestDetailsState.value)
      }
    }
    finally {
      updateData()
      GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UNAPPROVE)
    }
  }

  override suspend fun close() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.graphQL.mergeRequestUpdate(glProject, mergeRequestDetailsState.value, GitLabMergeRequestNewState.CLOSED)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
      stateEventsLoader.checkForUpdates()
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.CLOSE)
  }

  override suspend fun reopen() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.graphQL.mergeRequestUpdate(glProject, mergeRequestDetailsState.value, GitLabMergeRequestNewState.OPEN)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
      stateEventsLoader.checkForUpdates()
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.REOPEN)
  }

  override suspend fun postReview() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.graphQL.mergeRequestSetDraft(glProject, mergeRequestDetailsState.value, isDraft = false)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.POST_REVIEW)
  }

  override suspend fun setReviewers(reviewers: List<GitLabUserDTO>) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.graphQL.mergeRequestSetReviewers(glProject, mergeRequestDetailsState.value, reviewers)
        .getResultOrThrow()
      mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.SET_REVIEWERS)
  }

  override suspend fun reviewerRereview(reviewers: Collection<GitLabReviewerDTO>) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      reviewers.forEach { reviewer ->
        val updatedMergeRequest = api.graphQL.mergeRequestReviewerRereview(glProject, mergeRequestDetailsState.value, reviewer)
          .getResultOrThrow()
        mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest)
      }
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.REVIEWER_REREVIEW)
  }

  private val discussionsContainer =
    GitLabMergeRequestDiscussionsContainerImpl(parentCs, project, api, projectMapping.repository, this)

  override val discussions: Flow<Result<Collection<GitLabMergeRequestDiscussion>>> = discussionsContainer.discussions
  override val systemNotes: Flow<Result<Collection<GitLabNote>>> = discussionsContainer.systemNotes
  override val draftNotes: Flow<Result<Collection<GitLabMergeRequestDraftNote>>> = discussionsContainer.draftNotes
  override val canAddNotes: Boolean = discussionsContainer.canAddNotes

  override suspend fun addNote(body: String) = discussionsContainer.addNote(body)

  override suspend fun addNote(position: GitLabDiffPositionInput, body: String) = discussionsContainer.addNote(position, body)

  override suspend fun submitDraftNotes() = discussionsContainer.submitDraftNotes()
}
