// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.AddedLast
import com.intellij.collaboration.async.AllDeleted
import com.intellij.collaboration.async.Change
import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.mapCatching
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.mapFiltered
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.resultOrErrorFlow
import com.intellij.collaboration.async.throwFailure
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.loadUpdatableJsonList
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addDiffNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addDraftNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestDiscussionsUri
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestDraftNotesUri
import org.jetbrains.plugins.gitlab.mergerequest.api.request.submitDraftNotes
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.startGitLabRestETagListLoaderIn
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

interface GitLabMergeRequestDiscussionsContainer {
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

  private val cs = parentCs.childScope(this::class, Dispatchers.Default)

  override val canAddNotes: Boolean = mr.details.value.userPermissions.createNote
  override val canAddDraftNotes: Boolean =
    canAddNotes && (glMetadata != null && GitLabVersion(15, 10) <= glMetadata.version)
  override val canAddPositionalDraftNotes: Boolean =
    canAddNotes && (glMetadata != null && GitLabVersion(16, 3) <= glMetadata.version)

  private val reloadRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
    tryEmit(Unit)
  }
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val discussionEvents = MutableSharedFlow<Change<GitLabDiscussionRestDTO>>()

  private val discussionsDataHolder =
    startGitLabRestETagListLoaderIn(
      cs,
      getMergeRequestDiscussionsUri(glProject, mr.iid),
      { it.id },

      requestReloadFlow = reloadRequests,
      requestRefreshFlow = updateRequests,
      requestChangeFlow = discussionEvents
    ) { uri, eTag ->
      api.rest.loadUpdatableJsonList<GitLabDiscussionRestDTO>(
        GitLabApiRequestName.REST_GET_MERGE_REQUEST_DISCUSSIONS, uri, eTag
      )
    }

  private val nonEmptyDiscussionsData: SharedFlow<Result<List<GitLabDiscussionRestDTO>>> =
    discussionsDataHolder.resultOrErrorFlow
      .mapCatching { discussions -> discussions.filter { it.notes.isNotEmpty() } }
      .modelFlow(cs, LOG)

  override val discussions: Flow<Result<List<GitLabMergeRequestDiscussion>>> =
    nonEmptyDiscussionsData
      .transformConsecutiveSuccesses {
        mapFiltered { !it.notes.first().system }
          .mapDataToModel(
            GitLabDiscussionRestDTO::id,
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
            GitLabNoteRestDTO::id,
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

  private val draftNotesData =
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
        api.rest.addNote(glProject, mr.iid, body).body()
      }

      withContext(NonCancellable) {
        discussionEvents.emit(AddedLast(newDiscussion))
      }
    }
  }

  override suspend fun addNote(position: GitLabMergeRequestNewDiscussionPosition, body: String) {
    withContext(cs.coroutineContext) {
      val newDiscussion = withContext(Dispatchers.IO) {
        api.rest.addDiffNote(glProject, mr.iid, GitLabDiffPositionInput.from(position), body).body()
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