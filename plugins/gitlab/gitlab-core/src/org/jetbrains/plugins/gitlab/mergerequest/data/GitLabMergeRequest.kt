// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.BatchesLoader
import com.intellij.collaboration.async.LoaderWithMutableCache
import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.CodeReviewDomainEntity
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.collaboration.util.asFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.GitStandardRemoteBranch
import git4idea.changes.GitBranchComparisonResult
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.changesSignalFlow
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceLabelEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceMilestoneEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceStateEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabReviewerDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.GitLabMergeRequestNewState
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestLabelEventsSequence
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestMilestoneEventsSequence
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestParticipants
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestStateEventsSequence
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestAccept
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestAcceptSquash
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestApprove
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestRebase
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestReviewerRereview
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestSetDraft
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestSetReviewers
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestUnApprove
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestUpdate
import org.jetbrains.plugins.gitlab.util.GitLabRegistry
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

private val LOG = logger<GitLabMergeRequest>()

@CodeReviewDomainEntity
interface GitLabMergeRequest : GitLabMergeRequestDiscussionsContainer {
  val serverPath: GitLabServerPath
  val gitRemote: GitRemoteUrlCoordinates
  val projectId: String

  val iid: String
  val gid: String

  val url: String
  val author: GitLabUserDTO

  val isLoading: SharedFlow<Boolean>
  val mergeRequestReloadSignal: SharedFlow<Unit>

  val details: StateFlow<GitLabMergeRequestFullDetails>
  val changes: SharedFlow<GitLabMergeRequestChanges>

  val labelEventsChangedSignal: Flow<Unit>
  val stateEventsChangedSignal: Flow<Unit>
  val milestoneEventsChangedSignal: Flow<Unit>

  suspend fun loadLabelEvents(): List<GitLabResourceLabelEventDTO>
  suspend fun loadStateEvents(): List<GitLabResourceStateEventDTO>
  suspend fun loadMilestoneEvents(): List<GitLabResourceMilestoneEventDTO>

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

  fun getParticipantsBatches(): Flow<List<GitLabUserDTO>>

  suspend fun merge(commitMessage: String?, removeSourceBranch: Boolean)

  suspend fun squashAndMerge(commitMessage: String?, removeSourceBranch: Boolean, squashCommitMessage: String?)

  suspend fun rebase()

  suspend fun approve()

  suspend fun unApprove()

  suspend fun close()

  suspend fun reopen()

  suspend fun postReview()

  suspend fun setReviewers(reviewers: List<GitLabUserDTO>)

  suspend fun reviewerRereview(reviewers: Collection<GitLabReviewerDTO>)
}

internal fun GitLabMergeRequest.changesComputationState(): Flow<ComputedResult<GitBranchComparisonResult>> =
  computationStateFlow(changes) { it.getParsedChanges() }

internal class LoadedGitLabMergeRequest(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val glMetadata: GitLabServerMetadata?,
  private val projectCoordinates: GitLabProjectCoordinates,
  override val projectId: String,
  override val gitRemote: GitRemoteUrlCoordinates,
  currentUser: GitLabUserDTO,
  mergeRequest: GitLabMergeRequestDTO,
) : GitLabMergeRequest {
  private val cs = parentCs.childScope(this::class, Dispatchers.Default)

  override val serverPath: GitLabServerPath = projectCoordinates.serverPath

  override val iid: String = mergeRequest.iid
  override val gid: String = mergeRequest.id

  override val url: String = mergeRequest.webUrl
  override val author: GitLabUserDTO = mergeRequest.author

  private val mergeRequestRefreshRequest = MutableSharedFlow<Unit>(1)
  private val mergeRequestReloadRequest = MutableSharedFlow<Unit>(1)
  override val mergeRequestReloadSignal = mergeRequestReloadRequest.asSharedFlow()
  private val stateEventsRefreshRequest = MutableSharedFlow<Unit>(1)

  private val mergeRequestDetailsState: MutableStateFlow<GitLabMergeRequestFullDetails> =
    MutableStateFlow(GitLabMergeRequestFullDetails.fromGraphQL(mergeRequest))
  override val details: StateFlow<GitLabMergeRequestFullDetails> = mergeRequestDetailsState.asStateFlow()

  override val changes: SharedFlow<GitLabMergeRequestChanges> = mergeRequestDetailsState
    .distinctUntilChangedBy(GitLabMergeRequestFullDetails::diffRefs)
    .mapScoped { details ->
      GitLabMergeRequestChangesImpl(this, project, projectId, projectCoordinates.projectPath, gitRemote,
                                    api, glMetadata, details)
    }
    .modelFlow(cs, LOG)

  // The sequence is created once and re-walked on reload/refresh, so its per-URI ETag cache is reused.
  private val stateEventsSequence = api.rest.getMergeRequestStateEventsSequence(projectId, iid)
  private val stateEventsLoader = LoaderWithMutableCache(cs) { stateEventsSequence.asFlow().foldToList() }
  override val stateEventsChangedSignal: Flow<Unit> = stateEventsLoader.updatedSignal
  override suspend fun loadStateEvents(): List<GitLabResourceStateEventDTO> = stateEventsLoader.load()

  private val labelEventsSequence = api.rest.getMergeRequestLabelEventsSequence(projectId, iid)
  private val labelEventsLoader = LoaderWithMutableCache(cs) { labelEventsSequence.asFlow().foldToList() }
  override val labelEventsChangedSignal: Flow<Unit> = labelEventsLoader.updatedSignal
  override suspend fun loadLabelEvents(): List<GitLabResourceLabelEventDTO> = labelEventsLoader.load()

  private val milestoneEventsSequence = api.rest.getMergeRequestMilestoneEventsSequence(projectId, iid)
  private val milestoneEventsLoader = LoaderWithMutableCache(cs) { milestoneEventsSequence.asFlow().foldToList() }
  override val milestoneEventsChangedSignal: Flow<Unit> = milestoneEventsLoader.updatedSignal
  override suspend fun loadMilestoneEvents(): List<GitLabResourceMilestoneEventDTO> = milestoneEventsLoader.load()

  init {
    cs.launch {
      merge(mergeRequestReloadRequest, mergeRequestRefreshRequest).collect {
        stateEventsLoader.clearCache()
        labelEventsLoader.clearCache()
        milestoneEventsLoader.clearCache()
      }
    }
    cs.launch {
      stateEventsRefreshRequest.collect {
        stateEventsLoader.clearCache()
      }
    }
  }

  private val participantLoader =
    BatchesLoader(cs, api.rest.getMergeRequestParticipants(projectId, iid).map { users -> users.map(GitLabUserDTO::fromRestDTO) })

  private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isLoading: SharedFlow<Boolean> = _isLoading.asSharedFlow()
  private val detailsLoadingGuard = Mutex()

  override val draftReviewText: MutableStateFlow<String> = MutableStateFlow("")

  private val discussionsContainer =
    GitLabMergeRequestDiscussionsContainerImpl(parentCs, project, api, glMetadata, projectId, currentUser, this)

  init {
    cs.launch {
      mergeRequestRefreshRequest
        .collect {
          runCatchingUser { refreshDataNow() }
            .onFailure { LOG.info("Error occurred while loading merge request data", it) }
        }
    }

    cs.launch {
      val repository = gitRemote.repository
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
        api.graphQL.loadMergeRequest(projectCoordinates.projectPath, iid)!!
      }
      return updateMergeRequestData(updatedMergeRequest)
    }
    finally {
      _isLoading.value = false
      detailsLoadingGuard.unlock()
    }
  }

  private fun isCurrentDataInSyncWithRepository(details: GitLabMergeRequestFullDetails, repository: GitRepository): Boolean? {
    val remoteMrBranchHash = details.getSourceRemoteDescriptor(serverPath)?.let {
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
    }
  }

  override fun reloadDiscussions() {
    cs.launch {
      discussionsContainer.requestDiscussionsReload()
    }
  }

  private suspend fun updateData() {
    mergeRequestRefreshRequest.emit(Unit)
    discussionsContainer.requestDiscussionsRefresh()
  }

  override suspend fun merge(commitMessage: String?, removeSourceBranch: Boolean) {
    val sha = mergeRequestDetailsState.value.diffRefs?.headSha ?: return
    cs.async(Dispatchers.IO) {
      api.graphQL.mergeRequestAccept(projectCoordinates.projectPath, iid, commitMessage, sha, removeSourceBranch)
        .getResultOrThrow()
      awaitMerged()
    }.await()
    discussionsContainer.requestDiscussionsRefresh()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.MERGE)
  }

  override suspend fun squashAndMerge(commitMessage: String?, removeSourceBranch: Boolean, squashCommitMessage: String?) {
    val sha = mergeRequestDetailsState.value.diffRefs?.headSha ?: return
    cs.async(Dispatchers.IO) {
      api.graphQL.mergeRequestAcceptSquash(projectCoordinates.projectPath, iid, commitMessage, squashCommitMessage, sha, removeSourceBranch)
        .getResultOrThrow()
      awaitMerged()
    }.await()
    discussionsContainer.requestDiscussionsRefresh()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.SQUASH_MERGE)
  }

  override suspend fun rebase() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      runRebase()
    }
    discussionsContainer.requestDiscussionsRefresh()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.REBASE)
  }

  override suspend fun approve() {
    try {
      withContext(cs.coroutineContext + Dispatchers.IO) {
        api.rest.mergeRequestApprove(projectId, iid)
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
        api.rest.mergeRequestUnApprove(projectId, iid)
      }
    }
    finally {
      updateData()
      GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UNAPPROVE)
    }
  }

  override suspend fun close() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest =
        api.graphQL.mergeRequestUpdate(projectCoordinates.projectPath, iid, GitLabMergeRequestNewState.CLOSED)
          .getResultOrThrow()
      updateMergeRequestData(updatedMergeRequest)
      stateEventsRefreshRequest.emit(Unit)
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.CLOSE)
  }

  override suspend fun reopen() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest =
        api.graphQL.mergeRequestUpdate(projectCoordinates.projectPath, iid, GitLabMergeRequestNewState.OPEN)
          .getResultOrThrow()
      updateMergeRequestData(updatedMergeRequest)
      stateEventsRefreshRequest.emit(Unit)
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.REOPEN)
  }

  override suspend fun postReview() {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest =
        api.graphQL.mergeRequestSetDraft(projectCoordinates.projectPath, iid, isDraft = false)
          .getResultOrThrow()
      updateMergeRequestData(updatedMergeRequest)
    }
    discussionsContainer.requestDiscussionsRefresh()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.POST_REVIEW)
  }

  override suspend fun setReviewers(reviewers: List<GitLabUserDTO>) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      val updatedMergeRequest = if (glMetadata != null && GitLabVersion(15, 3) <= glMetadata.version) {
        api.graphQL.mergeRequestSetReviewers(projectCoordinates.projectPath, iid, reviewers).getResultOrThrow()
      }
      else {
        api.rest.mergeRequestSetReviewers(projectId, iid, reviewers)
        api.graphQL.loadMergeRequest(projectCoordinates.projectPath, iid) ?: error("Merge request could not be loaded")
      }

      updateMergeRequestData(updatedMergeRequest)
    }
    discussionsContainer.requestDiscussionsRefresh()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.SET_REVIEWERS)
  }

  override suspend fun reviewerRereview(reviewers: Collection<GitLabReviewerDTO>) {
    withContext(cs.coroutineContext + Dispatchers.IO) {
      reviewers.forEach { reviewer ->
        val updatedMergeRequest =
          api.graphQL.mergeRequestReviewerRereview(projectCoordinates.projectPath, iid, reviewer)
            .getResultOrThrow()
        updateMergeRequestData(updatedMergeRequest)
      }
    }
    discussionsContainer.requestDiscussionsRefresh()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.REVIEWER_REREVIEW)
  }

  override val discussions: Flow<Result<Collection<GitLabMergeRequestDiscussion>>> = discussionsContainer.discussions
  override val systemNotes: Flow<Result<Collection<GitLabNote>>> = discussionsContainer.systemNotes
  override val draftNotes: Flow<Result<Collection<GitLabMergeRequestDraftNote>>> = discussionsContainer.draftNotes

  override fun getParticipantsBatches(): Flow<List<GitLabUserDTO>> = participantLoader.getBatches()

  override val canAddNotes: Boolean = discussionsContainer.canAddNotes
  override val canAddDraftNotes: Boolean = discussionsContainer.canAddDraftNotes
  override val canAddPositionalDraftNotes: Boolean = discussionsContainer.canAddPositionalDraftNotes
  override val canAddMultilinePositionalNotes: Boolean = discussionsContainer.canAddMultilinePositionalNotes

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

  private suspend fun awaitMerged() {
    var attempts = 0
    do {
      val updatedMergeRequest = api.graphQL.loadMergeRequest(projectCoordinates.projectPath, iid)!!
      updateMergeRequestData(updatedMergeRequest)
      delay(GitLabRegistry.getRequestPollingIntervalMillis().toLong())
      attempts++
    }
    while (updatedMergeRequest.state != GitLabMergeRequestState.MERGED && attempts < REQUEST_ATTEMPTS_LIMIT_NUMBER)
  }

  private suspend fun runRebase() {
    var attempts = 0
    api.rest.mergeRequestRebase(projectId, iid)
    do {
      val updatedMergeRequest = api.graphQL.loadMergeRequest(projectCoordinates.projectPath, iid)!!
      updateMergeRequestData(updatedMergeRequest)
      delay(GitLabRegistry.getRequestPollingIntervalMillis().toLong())
      attempts++
    }
    while (updatedMergeRequest.rebaseInProgress && attempts < REQUEST_ATTEMPTS_LIMIT_NUMBER)
  }

  companion object {
    private val REQUEST_ATTEMPTS_LIMIT_NUMBER = GitLabRegistry.getRequestPollingAttempts()
  }
}
