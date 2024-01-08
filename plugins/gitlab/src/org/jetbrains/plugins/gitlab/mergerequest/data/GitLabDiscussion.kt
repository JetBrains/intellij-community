// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.stateInNow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addDraftReplyNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.changeMergeRequestDiscussionResolve
import org.jetbrains.plugins.gitlab.mergerequest.api.request.createReplyNote
import java.util.*

interface GitLabDiscussion {
  val id: GitLabId

  val createdAt: Date
  val notes: StateFlow<List<GitLabNote>>
  val canAddDraftNotes: Boolean

  val canResolve: Boolean
  val canAddNotes: Flow<Boolean>
  val resolved: StateFlow<Boolean>

  suspend fun changeResolvedState()

  suspend fun addNote(body: String)
  suspend fun addDraftNote(body: String)
}

val GitLabMergeRequestDiscussion.firstNote: Flow<GitLabMergeRequestNote?>
  get() = notes.map(List<GitLabMergeRequestNote>::firstOrNull).distinctUntilChangedBy { it?.id }

interface GitLabMergeRequestDiscussion : GitLabDiscussion {
  override val notes: StateFlow<List<GitLabMergeRequestNote>>
}

private val LOG = logger<GitLabDiscussion>()

@OptIn(ExperimentalCoroutinesApi::class)
class LoadedGitLabDiscussion(
  parentCs: CoroutineScope,
  private val project: Project,
  private val api: GitLabApi,
  glMetadata: GitLabServerMetadata?,
  private val glProject: GitLabProjectCoordinates,
  private val eventSink: suspend (GitLabDiscussionEvent) -> Unit,
  private val draftNotesEventSink: suspend (GitLabNoteEvent<GitLabMergeRequestDraftNoteRestDTO>) -> Unit,
  private val mr: GitLabMergeRequest,
  discussionData: GitLabDiscussionDTO,
  draftNotes: Flow<List<GitLabMergeRequestDraftNote>>
) : GitLabMergeRequestDiscussion {
  init {
    require(discussionData.notes.isNotEmpty()) { "Discussion with empty notes" }
  }

  private val dataState = MutableStateFlow(discussionData)

  override val id: GitLabGid = discussionData.id
  override val createdAt: Date = discussionData.createdAt

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val operationsGuard = Mutex()

  private val noteEvents = MutableSharedFlow<GitLabNoteEvent<GitLabNoteDTO>>()
  private val loadedNotes = dataState.transformLatest { discussionData ->
    coroutineScope {
      val notesData = discussionData.notes.toMutableList()

      launch(start = CoroutineStart.UNDISPATCHED) {
        noteEvents.collectLatest { event ->
          when (event) {
            is GitLabNoteEvent.Added -> notesData.add(event.note)
            is GitLabNoteEvent.Deleted -> notesData.removeIf { it.id == event.noteId }
            is GitLabNoteEvent.Changed -> {
              notesData.clear()
              notesData.addAll(event.notes)
            }
            is GitLabNoteEvent.AllDeleted -> notesData.clear()
          }

          if (notesData.isEmpty()) {
            eventSink(GitLabDiscussionEvent.Deleted(discussionData.id))
            return@collectLatest
          }

          emit(notesData.toList())
        }
      }
      emit(notesData.toList())
    }
  }.stateIn(cs, SharingStarted.Eagerly, discussionData.notes)

  override val notes: StateFlow<List<GitLabMergeRequestNote>> =
    loadedNotes
      .mapDataToModel(
        GitLabNoteDTO::id,
        { note -> MutableGitLabMergeRequestNote(this, project, api, mr, noteEvents::emit, note) },
        MutableGitLabMergeRequestNote::update
      ).combine(draftNotes) { notes, draftNotes ->
        notes + draftNotes
      }.stateInNow(cs, emptyList())

  override val canAddNotes: Flow<Boolean> = draftNotes.map { it.isEmpty() && mr.details.value.userPermissions.createNote }
  override val canAddDraftNotes: Boolean =
    mr.details.value.userPermissions.createNote &&
    (glMetadata?.let { GitLabVersion(16, 3) <= it.version } ?: false)

  // a little cheat that greatly simplifies the implementation
  override val canResolve: Boolean = discussionData.notes.first().resolvable && discussionData.notes.first().userPermissions.resolveNote

  override val resolved: StateFlow<Boolean> =
    loadedNotes.mapState { it.firstOrNull()?.resolved ?: false }

  override suspend fun changeResolvedState() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        val resolved = resolved.first()
        val result = withContext(Dispatchers.IO) {
          api.graphQL.changeMergeRequestDiscussionResolve(id.gid, !resolved).getResultOrThrow()
        }
        noteEvents.emit(GitLabNoteEvent.Changed(result.notes))
      }
    }
  }

  override suspend fun addNote(body: String) {
    withContext(cs.coroutineContext) {
      val newDiscussion = withContext(Dispatchers.IO) {
        api.graphQL.createReplyNote(mr.gid, id.gid, body).getResultOrThrow()
      }

      withContext(NonCancellable) {
        noteEvents.emit(GitLabNoteEvent.Added(newDiscussion))
      }
    }
  }

  override suspend fun addDraftNote(body: String) {
    withContext(cs.coroutineContext) {
      withContext(Dispatchers.IO) {
        api.rest.addDraftReplyNote(glProject, mr.iid, id.guessRestId(), body).body()
      }?.also {
        withContext(NonCancellable) {
          draftNotesEventSink(GitLabNoteEvent.Added(it))
        }
      }
    }
  }

  fun update(data: GitLabDiscussionDTO) {
    dataState.value = data
  }

  override fun toString(): String =
    "LoadedGitLabDiscussion(id='$id', createdAt=$createdAt, canAddNotes=$canAddNotes, canResolve=$canResolve)"
}