// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.asResultFlow
import com.intellij.collaboration.async.collectBatches
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import com.intellij.vcsUtil.VcsFileUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.*
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

  val iid: String
  val gid: String

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

  // not the best place for it
  fun getRelativeFilePath(virtualFile: VirtualFile): FilePath?
}

internal class LoadedGitLabMergeRequest(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val projectMapping: GitLabProjectMapping,
  mergeRequest: GitLabMergeRequestDTO,
  backupCommits: List<GitLabCommitRestDTO>
) : GitLabMergeRequest,
    GitLabMergeRequestDiscussionsContainer {
  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val glProject: GitLabProjectCoordinates = projectMapping.repository

  override val iid: String = mergeRequest.iid
  override val gid: String = mergeRequest.id

  override val url: String = mergeRequest.webUrl
  override val author: GitLabUserDTO = mergeRequest.author

  private val mergeRequestRefreshRequest = MutableSharedFlow<Unit>()

  private val mergeRequestDetailsState: MutableStateFlow<GitLabMergeRequestFullDetails> =
    MutableStateFlow(GitLabMergeRequestFullDetails.fromGraphQL(mergeRequest, backupCommits))
  override val details: StateFlow<GitLabMergeRequestFullDetails> = mergeRequestDetailsState.asStateFlow()

  override val changes: SharedFlow<GitLabMergeRequestChanges> = mergeRequestDetailsState
    .distinctUntilChangedBy(GitLabMergeRequestFullDetails::diffRefs).map {
      GitLabMergeRequestChangesImpl(project, cs, api, projectMapping, it)
    }.modelFlow(cs, LOG)

  private val stateEventsLoader =
    GitLabETagUpdatableListLoader<GitLabResourceStateEventDTO>(getMergeRequestStateEventsUri(glProject, iid)
    ) { uri, eTag ->
      api.rest.loadUpdatableJsonList<GitLabResourceStateEventDTO>(
        GitLabApiRequestName.REST_GET_MERGE_REQUEST_STATE_EVENTS, uri, eTag
      )
    }
  override val stateEvents = stateEventsLoader.batches.collectBatches()
    .asResultFlow()
    .modelFlow(cs, LOG)

  private val labelEventsLoader =
    GitLabETagUpdatableListLoader<GitLabResourceLabelEventDTO>(getMergeRequestLabelEventsUri(glProject, iid)
    ) { uri, eTag ->
      api.rest.loadUpdatableJsonList<GitLabResourceLabelEventDTO>(
        GitLabApiRequestName.REST_GET_MERGE_REQUEST_LABEL_EVENTS, uri, eTag
      )
    }
  override val labelEvents = labelEventsLoader.batches.collectBatches()
    .asResultFlow()
    .modelFlow(cs, LOG)

  private val milestoneEventsLoader =
    GitLabETagUpdatableListLoader<GitLabResourceMilestoneEventDTO>(getMergeRequestMilestoneEventsUri(glProject, iid)
    ) { uri, eTag ->
      api.rest.loadUpdatableJsonList<GitLabResourceMilestoneEventDTO>(
        GitLabApiRequestName.REST_GET_MERGE_REQUEST_MILESTONE_EVENTS, uri, eTag
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
          val updatedMergeRequest = api.graphQL.loadMergeRequest(glProject, iid).body()!!
          updateMergeRequestData(updatedMergeRequest)
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
                                                               iid,
                                                               commitMessage,
                                                               mergeRequest.commits.first().sha, // First from the list -- last commit from review
                                                               withSquash = false).getResultOrThrow()
      updateMergeRequestData(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.MERGE)
  }

  override suspend fun squashAndMerge(commitMessage: String) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val mergeRequest = mergeRequestDetailsState.value
      val updatedMergeRequest = api.graphQL.mergeRequestAccept(glProject,
                                                               iid,
                                                               commitMessage,
                                                               mergeRequest.commits.first().sha, // First from the list -- last commit from review
                                                               withSquash = true).getResultOrThrow()
      updateMergeRequestData(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.SQUASH_MERGE)
  }

  override suspend fun rebase() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      api.rest.mergeRequestRebase(glProject, iid)
      do {
        val updatedMergeRequest = api.graphQL.loadMergeRequest(glProject, iid).body()!!
        updateMergeRequestData(updatedMergeRequest)
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
        api.rest.mergeRequestApprove(glProject, iid)
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
        api.rest.mergeRequestUnApprove(glProject, iid)
      }
    }
    finally {
      updateData()
      GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UNAPPROVE)
    }
  }

  override suspend fun close() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.graphQL.mergeRequestUpdate(glProject, iid, GitLabMergeRequestNewState.CLOSED)
        .getResultOrThrow()
      updateMergeRequestData(updatedMergeRequest)
      stateEventsLoader.checkForUpdates()
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.CLOSE)
  }

  override suspend fun reopen() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.graphQL.mergeRequestUpdate(glProject, iid, GitLabMergeRequestNewState.OPEN)
        .getResultOrThrow()
      updateMergeRequestData(updatedMergeRequest)
      stateEventsLoader.checkForUpdates()
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.REOPEN)
  }

  override suspend fun postReview() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.graphQL.mergeRequestSetDraft(glProject, iid, isDraft = false)
        .getResultOrThrow()
      updateMergeRequestData(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.POST_REVIEW)
  }

  override suspend fun setReviewers(reviewers: List<GitLabUserDTO>) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = if (GitLabVersion(15, 3) <= api.getMetadata().version) {
        api.graphQL.mergeRequestSetReviewers(glProject, iid, reviewers).getResultOrThrow()
      }
      else {
        api.rest.mergeRequestSetReviewers(glProject, iid, reviewers).body()
        api.graphQL.loadMergeRequest(glProject, iid).body() ?: error("Merge request could not be loaded")
      }

      updateMergeRequestData(updatedMergeRequest)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.SET_REVIEWERS)
  }

  override suspend fun reviewerRereview(reviewers: Collection<GitLabReviewerDTO>) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      reviewers.forEach { reviewer ->
        val updatedMergeRequest = api.graphQL.mergeRequestReviewerRereview(glProject, iid, reviewer)
          .getResultOrThrow()
        updateMergeRequestData(updatedMergeRequest)
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

  // Compatibility fix to make sure commits are loaded
  private suspend fun updateMergeRequestData(updatedMergeRequest: GitLabMergeRequestDTO) {
    val commits =
      if (updatedMergeRequest.commits == null)
        api.rest.getMergeRequestCommits(projectMapping.repository, updatedMergeRequest.iid).body()
      else listOf()

    mergeRequestDetailsState.value = GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest, commits)
  }

  override fun getRelativeFilePath(virtualFile: VirtualFile): FilePath? {
    val path = try {
      VcsFileUtil.relativePath(projectMapping.remote.repository.root, virtualFile)
    }
    catch (iae: IllegalArgumentException) {
      return null
    }
    return VcsContextFactory.getInstance().createFilePath(path, false)
  }
}
