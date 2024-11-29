// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.resultOrErrorFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.GitStandardRemoteBranch
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.changesSignalFlow
import git4idea.repo.GitRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.startGitLabRestETagListLoaderIn
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabRegistry
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

private val LOG = logger<GitLabMergeRequest>()

interface GitLabMergeRequest : GitLabMergeRequestDiscussionsContainer {
  val glProject: GitLabProjectCoordinates
  val gitRepository: GitRepository

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

  /**
   * Sends a signal to fully reload the details and timeline of the merge request.
   */
  fun reloadData()

  /**
   * Sends a signal to reload the details and check for other data changes
   */
  fun refreshData()

  /**
   * Reloads the details without a debounce
   */
  suspend fun refreshDataNow(): GitLabMergeRequestFullDetails

  /**
   * Sends a signal to reload data on all submitted discussions within the container.
   * This should be used after a draft note is submitted, as there is no surefire way to turn a draft note
   * into a fully featured discussion without this.
   */
  fun reloadDiscussions()

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
  private val glMetadata: GitLabServerMetadata?,
  private val projectMapping: GitLabProjectMapping,
  private val currentUser: GitLabUserDTO,
  mergeRequest: GitLabMergeRequestDTO
) : GitLabMergeRequest {
  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val glProject: GitLabProjectCoordinates = projectMapping.repository
  override val gitRepository: GitRepository = projectMapping.gitRepository

  override val iid: String = mergeRequest.iid
  override val gid: String = mergeRequest.id

  override val url: String = mergeRequest.webUrl
  override val author: GitLabUserDTO = mergeRequest.author

  private val mergeRequestRefreshRequest = MutableSharedFlow<Unit>(1)
  private val mergeRequestReloadRequest = MutableSharedFlow<Unit>(1)
  private val stateEventsRefreshRequest = MutableSharedFlow<Unit>(1)

  private val mergeRequestDetailsState: MutableStateFlow<GitLabMergeRequestFullDetails> =
    MutableStateFlow(GitLabMergeRequestFullDetails.fromGraphQL(mergeRequest))
  override val details: StateFlow<GitLabMergeRequestFullDetails> = mergeRequestDetailsState.asStateFlow()

  override val changes: SharedFlow<GitLabMergeRequestChanges> = mergeRequestDetailsState
    .distinctUntilChangedBy(GitLabMergeRequestFullDetails::diffRefs)
    .mapScoped { details -> GitLabMergeRequestChangesImpl(this, api, glMetadata, projectMapping, details) }
    .modelFlow(cs, LOG)

  private val stateEventsHolder =
    startGitLabRestETagListLoaderIn(
      cs,
      getMergeRequestStateEventsUri(glProject, iid),
      { it.id },

      requestReloadFlow = mergeRequestReloadRequest.withInitial(Unit),
      requestRefreshFlow = mergeRequestRefreshRequest.combine(stateEventsRefreshRequest.withInitial(Unit)) { _, _ -> }
    ) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabResourceStateEventDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_STATE_EVENTS, uri, eTag
        )
    }
  override val stateEvents =
    stateEventsHolder.resultOrErrorFlow.modelFlow(cs, LOG)

  private val labelEventsHolder =
    startGitLabRestETagListLoaderIn(
      cs,
      getMergeRequestLabelEventsUri(glProject, iid),
      { it.id },

      requestReloadFlow = mergeRequestReloadRequest.withInitial(Unit),
      requestRefreshFlow = mergeRequestRefreshRequest
    ) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabResourceLabelEventDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_LABEL_EVENTS, uri, eTag
        )
    }
  override val labelEvents =
    labelEventsHolder.resultOrErrorFlow.modelFlow(cs, LOG)

  private val milestoneEventsHolder =
    startGitLabRestETagListLoaderIn(
      cs,
      getMergeRequestMilestoneEventsUri(glProject, iid),
      { it.id },

      requestReloadFlow = mergeRequestReloadRequest.withInitial(Unit),
      requestRefreshFlow = mergeRequestRefreshRequest
    ) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabResourceMilestoneEventDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_MILESTONE_EVENTS, uri, eTag
        )
    }
  override val milestoneEvents =
    milestoneEventsHolder.resultOrErrorFlow.modelFlow(cs, LOG)

  private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isLoading: SharedFlow<Boolean> = _isLoading.asSharedFlow()
  private val detailsLoadingGuard = Mutex()

  override val draftReviewText: MutableStateFlow<String> = MutableStateFlow("")

  private val discussionsContainer =
    GitLabMergeRequestDiscussionsContainerImpl(parentCs, project, api, glMetadata, projectMapping.repository, currentUser, this)

  init {
    cs.launch {
      mergeRequestRefreshRequest
        .collect {
          runCatchingUser { refreshDataNow() }
            .onFailure { LOG.info("Error occurred while loading merge request data", it) }
        }
    }

    cs.launch {
      val repository = projectMapping.gitRepository
      repository.changesSignalFlow().withInitial(Unit).combine(details) { _, currentDetails ->
        isCurrentDataInSyncWithRepository(currentDetails, repository)
      }.distinctUntilChanged().filterNotNull().collectLatest {
        if (!it) {
          mergeRequestRefreshRequest.emit(Unit)
          // hashes in discussions will change on push
          discussionsContainer.requestDiscussionsReload()
        }
      }
    }
  }

  override suspend fun refreshDataNow(): GitLabMergeRequestFullDetails {
    try {
      detailsLoadingGuard.lock()
      _isLoading.value = true
      val updatedMergeRequest = withContext(Dispatchers.IO) {
        api.graphQL.loadMergeRequest(glProject, iid).body()!!
      }
      return updateMergeRequestData(updatedMergeRequest)
    }
    finally {
      _isLoading.value = false
      detailsLoadingGuard.unlock()
    }
  }

  private fun isCurrentDataInSyncWithRepository(details: GitLabMergeRequestFullDetails, repository: GitRepository): Boolean? {
    val remoteMrBranchHash = details.getSourceRemoteDescriptor(projectMapping.repository.serverPath)?.let {
      GitRemoteBranchesUtil.findRemote(repository, it)
    }?.let {
      val branch = GitStandardRemoteBranch(it, details.sourceBranch)
      repository.branches.getHash(branch)
    } ?: return null
    val knownHead = details.diffRefs?.headSha ?: return null
    return remoteMrBranchHash.asString() == knownHead
  }

  override fun reloadData() {
    cs.launch {
      mergeRequestReloadRequest.emit(Unit)
      mergeRequestRefreshRequest.emit(Unit)

      discussionsContainer.requestDiscussionsReload()
    }
  }

  override fun refreshData() {
    cs.launch {
      updateData()
      startRefreshCycle()
    }
  }

  override fun reloadDiscussions() {
    cs.launch {
      discussionsContainer.requestDiscussionsReload()
    }
  }

  private suspend fun updateData() {
    mergeRequestRefreshRequest.emit(Unit)
    discussionsContainer.checkUpdates()
  }

  private suspend fun startRefreshCycle() {
    stateEventsHolder.loadAll()
    labelEventsHolder.loadAll()
    milestoneEventsHolder.loadAll()
  }

  override suspend fun merge(commitMessage: String) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      runMerge(commitMessage, withSquash = false)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.MERGE)
  }

  override suspend fun squashAndMerge(commitMessage: String) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      runMerge(commitMessage, withSquash = true)
    }
    discussionsContainer.checkUpdates()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.SQUASH_MERGE)
  }

  override suspend fun rebase() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      runRebase()
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
      stateEventsRefreshRequest.emit(Unit)
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.CLOSE)
  }

  override suspend fun reopen() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = api.graphQL.mergeRequestUpdate(glProject, iid, GitLabMergeRequestNewState.OPEN)
        .getResultOrThrow()
      updateMergeRequestData(updatedMergeRequest)
      stateEventsRefreshRequest.emit(Unit)
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
      val updatedMergeRequest = if (glMetadata != null && GitLabVersion(15, 3) <= glMetadata.version) {
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

  override val discussions: Flow<Result<Collection<GitLabMergeRequestDiscussion>>> = discussionsContainer.discussions
  override val systemNotes: Flow<Result<Collection<GitLabNote>>> = discussionsContainer.systemNotes
  override val draftNotes: Flow<Result<Collection<GitLabMergeRequestDraftNote>>> = discussionsContainer.draftNotes
  override val nonEmptyDiscussionsData: SharedFlow<Result<List<GitLabDiscussionDTO>>> = discussionsContainer.nonEmptyDiscussionsData
  override val draftNotesData: SharedFlow<Result<List<GitLabMergeRequestDraftNoteRestDTO>>> = discussionsContainer.draftNotesData

  override val canAddNotes: Boolean = discussionsContainer.canAddNotes
  override val canAddDraftNotes: Boolean = discussionsContainer.canAddDraftNotes
  override val canAddPositionalDraftNotes: Boolean = discussionsContainer.canAddPositionalDraftNotes

  override suspend fun addNote(body: String) =
    discussionsContainer.addNote(body)

  override suspend fun addNote(position: GitLabMergeRequestNewDiscussionPosition, body: String) =
    discussionsContainer.addNote(position, body)

  override suspend fun addDraftNote(body: String) =
    discussionsContainer.addDraftNote(body)

  override suspend fun addDraftNote(position: GitLabMergeRequestNewDiscussionPosition, body: String) =
    discussionsContainer.addDraftNote(position, body)

  override suspend fun submitDraftNotes() = discussionsContainer.submitDraftNotes()

  // Compatibility fix to make sure commits are loaded
  private suspend fun updateMergeRequestData(updatedMergeRequest: GitLabMergeRequestDTO): GitLabMergeRequestFullDetails {
    return GitLabMergeRequestFullDetails.fromGraphQL(updatedMergeRequest).also {
      mergeRequestDetailsState.value = it
    }
  }

  private suspend fun runMerge(commitMessage: String, withSquash: Boolean) {
    var attempts = 0
    val sha = mergeRequestDetailsState.value.diffRefs?.headSha ?: return
    val shouldRemoveSourceBranch = mergeRequestDetailsState.value.shouldRemoveSourceBranch
                                   ?: mergeRequestDetailsState.value.targetProject.removeSourceBranchAfterMerge
    api.graphQL.mergeRequestAccept(glProject, iid, commitMessage, sha, withSquash, shouldRemoveSourceBranch).getResultOrThrow()
    do {
      val updatedMergeRequest = api.graphQL.loadMergeRequest(glProject, iid).body()!!
      updateMergeRequestData(updatedMergeRequest)
      delay(GitLabRegistry.getRequestPollingIntervalMillis().toLong())
      attempts++
    }
    while (updatedMergeRequest.state != GitLabMergeRequestState.MERGED || attempts == REQUEST_ATTEMPTS_LIMIT_NUMBER)
  }

  private suspend fun runRebase() {
    var attempts = 0
    api.rest.mergeRequestRebase(glProject, iid)
    do {
      val updatedMergeRequest = api.graphQL.loadMergeRequest(glProject, iid).body()!!
      updateMergeRequestData(updatedMergeRequest)
      delay(GitLabRegistry.getRequestPollingIntervalMillis().toLong())
      attempts++
    }
    while (updatedMergeRequest.rebaseInProgress || attempts == REQUEST_ATTEMPTS_LIMIT_NUMBER)
  }

  companion object {
    private val REQUEST_ATTEMPTS_LIMIT_NUMBER = GitLabRegistry.getRequestPollingAttempts()
  }
}
