// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.async.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.startGitLabGraphQLListLoaderIn
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.startGitLabRestETagListLoaderIn
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

interface GitLabMergeRequestDiscussionsContainer {
  val nonEmptyDiscussionsData: SharedFlow<Result<List<GitLabDiscussionDTO>>>
  val draftNotesData: SharedFlow<Result<List<GitLabMergeRequestDraftNoteRestDTO>>>

  val discussions: Flow<Result<Collection<GitLabMergeRequestDiscussion>>>
  val systemNotes: Flow<Result<Collection<GitLabNote>>>
  val draftNotes: Flow<Result<Collection<GitLabMergeRequestDraftNote>>>

  val canAddNotes: Boolean
  val canAddDraftNotes: Boolean
  val canAddPositionalDraftNotes: Boolean

  suspend fun addNote(body: String)

  suspend fun addNote(position: GitLabMergeRequestNewDiscussionPosition, body: String)

  suspend fun addDraftNote(body: String)

  suspend fun addDraftNote(position: GitLabMergeRequestNewDiscussionPosition, body: String)

  suspend fun submitDraftNotes()
}

private val LOG = logger<GitLabMergeRequestDiscussionsContainer>()

class GitLabMergeRequestDiscussionsContainerImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val api: GitLabApi,
  private val glMetadata: GitLabServerMetadata?,
  private val glProject: GitLabProjectCoordinates,
  private val currentUser: GitLabUserDTO,
  private val mr: GitLabMergeRequest,
) : GitLabMergeRequestDiscussionsContainer {

  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val canAddNotes: Boolean = mr.details.value.userPermissions.createNote
  override val canAddDraftNotes: Boolean =
    canAddNotes && (glMetadata != null && GitLabVersion(15, 10) <= glMetadata.version)
  override val canAddPositionalDraftNotes: Boolean =
    canAddNotes && (glMetadata != null && GitLabVersion(16, 3) <= glMetadata.version)

  private val reloadRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
    tryEmit(Unit)
  }
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val discussionEvents = MutableSharedFlow<Change<GitLabDiscussionDTO>>()

  private val discussionsDataHolder =
    startGitLabGraphQLListLoaderIn(
      cs,
      { it.id },

      requestReloadFlow = reloadRequests,
      requestRefreshFlow = updateRequests,
      requestChangeFlow = discussionEvents,

      isReversed = true
    ) { cursor ->
      api.graphQL.loadMergeRequestDiscussions(glProject, mr.iid, GraphQLRequestPagination(cursor))
    }

  override val nonEmptyDiscussionsData: SharedFlow<Result<List<GitLabDiscussionDTO>>> =
    discussionsDataHolder.resultOrErrorFlow
      .mapCatching { discussions -> discussions.filter { it.notes.isNotEmpty() } }
      .modelFlow(cs, LOG)

  override val discussions: Flow<Result<List<GitLabMergeRequestDiscussion>>> =
    nonEmptyDiscussionsData
      .transformConsecutiveSuccesses {
        mapFiltered { !it.notes.first().system }
          .mapDataToModel(
            GitLabDiscussionDTO::id,
            { disc ->
              LoadedGitLabDiscussion(this,
                                     api, glMetadata, glProject, currentUser,
                                     { discussionEvents.emit(it) }, { draftNotesEvents.emit(it) },
                                     mr, disc, getDiscussionDraftNotes(disc.id).throwFailure())
            },
            LoadedGitLabDiscussion::update
          )
      }
      .modelFlow(cs, LOG)

  override val systemNotes: Flow<Result<List<GitLabNote>>> =
    nonEmptyDiscussionsData
      .transformConsecutiveSuccesses {
        // When one note in a discussion is a system note, all are, so we check the first.
        mapFiltered { it.notes.first().system }
          .map { discussions -> discussions.map { it.notes.first() } }
          .mapDataToModel(
            GitLabNoteDTO::id,
            { note -> GitLabSystemNote(note) },
            { } //constant
          )
      }
      .modelFlow(cs, LOG)

  private val draftNotesEvents = MutableSharedFlow<Change<GitLabMergeRequestDraftNoteRestDTO>>()

  private val draftNotesDataHolder =
    if (glMetadata == null || glMetadata.version < GitLabVersion(15, 9)) {
      null
    }
    else {
      startGitLabRestETagListLoaderIn(
        cs,
        getMergeRequestDraftNotesUri(glProject, mr.iid),
        { it.id },

        requestReloadFlow = reloadRequests,
        requestRefreshFlow = updateRequests,
        requestChangeFlow = draftNotesEvents
      ) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabMergeRequestDraftNoteRestDTO>(
          GitLabApiRequestName.REST_GET_DRAFT_NOTES, uri, eTag
        )
      }
    }

  override val draftNotesData =
    (draftNotesDataHolder?.resultOrErrorFlow ?: flowOf(Result.success(emptyList())))
      .mapCatching { draftNotes ->
        if (draftNotes.isEmpty()) return@mapCatching emptyList()

        draftNotes.map { it }
      }
      .modelFlow(cs, LOG)

  override val draftNotes: Flow<Result<Collection<GitLabMergeRequestDraftNote>>> = flow {
    draftNotesData
      .transformConsecutiveSuccesses {
        mapDataToModel(
          GitLabMergeRequestDraftNoteRestDTO::id,
          {
            GitLabMergeRequestDraftNoteImpl(this, api, glMetadata, glProject, mr, { draftNotesEvents.emit(it) }, it, currentUser)
          },
          { update(it) }
        )
      }.collect(this)
  }.modelFlow(cs, LOG)

  private fun getDiscussionDraftNotes(discussionId: GitLabId): Flow<Result<List<GitLabMergeRequestDraftNote>>> {
    // Convert discussion ID down to REST ID as it's safer than converting from REST to GQL
    val discussionRestId = discussionId.guessRestId()
    return draftNotes
      .map { result ->
        result.map { notes ->
          notes.filter { it.discussionId?.guessRestId() == discussionRestId }
        }
      }
  }

  override suspend fun addNote(body: String) {
    withContext(cs.coroutineContext) {
      val newDiscussion = withContext(Dispatchers.IO) {
        api.graphQL.addNote(mr.gid, body).getResultOrThrow()
      }

      withContext(NonCancellable) {
        discussionEvents.emit(AddedLast(newDiscussion))
      }
    }
  }

  override suspend fun addNote(position: GitLabMergeRequestNewDiscussionPosition, body: String) {
    withContext(cs.coroutineContext) {
      val newDiscussion = withContext(Dispatchers.IO) {
        api.graphQL.addDiffNote(mr.gid, GitLabDiffPositionInput.from(position), body).getResultOrThrow()
      }

      withContext(NonCancellable) {
        discussionEvents.emit(AddedLast(newDiscussion))
      }
    }
  }

  override suspend fun addDraftNote(body: String) {
    withContext(cs.coroutineContext) {
      val newNote = withContext(Dispatchers.IO) {
        api.rest.addDraftNote(glProject, mr.iid, null, body).body()
      }

      withContext(NonCancellable) {
        draftNotesEvents.emit(AddedLast(newNote))
      }
    }
  }

  override suspend fun addDraftNote(position: GitLabMergeRequestNewDiscussionPosition, body: String) {
    withContext(cs.coroutineContext) {
      val newNote = withContext(Dispatchers.IO) {
        api.rest.addDraftNote(glProject, mr.iid, GitLabDiffPositionInput.from(position), body).body()
      }

      withContext(NonCancellable) {
        draftNotesEvents.emit(AddedLast(newNote))
      }
    }
  }

  override suspend fun submitDraftNotes() {
    withContext(cs.coroutineContext) {
      // Don't do anything if the endpoint is not implemented
      if (glMetadata == null || glMetadata.version < GitLabVersion(15, 11)) {
        return@withContext
      }

      withContext(Dispatchers.IO) {
        api.rest.submitDraftNotes(glProject, mr.iid)
      }
      withContext(NonCancellable) {
        draftNotesEvents.emit(AllDeleted())
        checkUpdates()
      }
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.SUBMIT_DRAFT_NOTES)
  }

  suspend fun requestDiscussionsReload() {
    reloadRequests.emit(Unit)
  }

  suspend fun checkUpdates() {
    updateRequests.emit(Unit)

    draftNotesDataHolder?.loadAll()
    discussionsDataHolder.loadAll()
  }
}