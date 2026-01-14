// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.*
import com.intellij.collaboration.util.CodeReviewDomainEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addDraftReplyNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.changeMergeRequestDiscussionResolve
import org.jetbrains.plugins.gitlab.mergerequest.api.request.createReplyNote
import java.util.*

@CodeReviewDomainEntity
interface GitLabDiscussion {
  val id: GitLabId

  val createdAt: Date
  val notes: StateFlow<List<GitLabNote>>
  val canAddDraftNotes: Boolean

  val resolvable: StateFlow<Boolean>
  val resolveAllowed: Boolean
  val canAddNotes: StateFlow<Boolean>
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

@OptIn(ExperimentalCoroutinesApi::class)
class LoadedGitLabDiscussion(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  glMetadata: GitLabServerMetadata?,
  private val glProject: GitLabProjectCoordinates,
  private val currentUser: GitLabUserDTO,
  private val eventSink: suspend (Change<GitLabDiscussionRestDTO>) -> Unit,
  private val draftNotesEventSink: suspend (Change<GitLabMergeRequestDraftNoteRestDTO>) -> Unit,
  private val mr: GitLabMergeRequest,
  discussionData: GitLabDiscussionRestDTO,
  draftNotes: Flow<List<GitLabMergeRequestDraftNote>>,
) : GitLabMergeRequestDiscussion {
  init {
    require(discussionData.notes.isNotEmpty()) { "Discussion with empty notes" }
  }

  private val dataState = MutableStateFlow(discussionData)

  override val id: GitLabRestId = discussionData.id
  override val createdAt: Date = discussionData.createdAt

  private val cs = parentCs.childScope(this::class)
  private val operationsGuard = Mutex()

  private val noteEvents = MutableSharedFlow<GitLabNoteEvent<GitLabNoteRestDTO>>()
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
            eventSink(Deleted { it.id == discussionData.id })
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
        GitLabNoteRestDTO::id,
        { note ->
          MutableGitLabMergeRequestNote(this, api, glProject, mr, id, currentUser, noteEvents::emit, note)
        },
        MutableGitLabMergeRequestNote::update
      ).combine(draftNotes) { notes, draftNotes ->
        notes + draftNotes
      }.stateInNow(cs, emptyList())

  override val canAddNotes: StateFlow<Boolean> = draftNotes
    .map { it.isEmpty() && mr.details.value.userPermissions.createNote }
    .stateIn(cs, SharingStarted.Lazily, false)
  override val canAddDraftNotes: Boolean =
    mr.details.value.userPermissions.createNote &&
    (glMetadata?.let { GitLabVersion(16, 3) <= it.version } ?: false)


  override val resolvable: StateFlow<Boolean> =
    loadedNotes.mapState { it.firstOrNull()?.resolvable ?: false }

  // a little cheat that simplifies the implementation - we're assuming that if a user can create notes, they can also resolve discussions
  override val resolveAllowed: Boolean = mr.details.value.userPermissions.createNote

  override val resolved: StateFlow<Boolean> =
    loadedNotes.mapState { it.firstOrNull()?.resolved ?: false }

  override suspend fun changeResolvedState() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        val resolved = resolved.first()
        val result = withContext(Dispatchers.IO) {
          api.rest.changeMergeRequestDiscussionResolve(glProject, mr.iid, id.restId, !resolved).body()
        }
        noteEvents.emit(GitLabNoteEvent.Changed(result.notes))
        if (mr.details.value.targetProject.onlyAllowMergeIfAllDiscussionsAreResolved) {
          mr.refreshData()
        }
      }
    }
  }

  override suspend fun addNote(body: String) {
    withContext(cs.coroutineContext) {
      val note = withContext(Dispatchers.IO) {
        api.rest.createReplyNote(glProject, mr.iid, id.restId, body).body()
      }

      withContext(NonCancellable) {
        noteEvents.emit(GitLabNoteEvent.Added(note))
      }
    }
  }

  override suspend fun addDraftNote(body: String) {
    withContext(cs.coroutineContext) {
      withContext(Dispatchers.IO) {
        api.rest.addDraftReplyNote(glProject, mr.iid, id.restId, body).body()
      }?.also {
        withContext(NonCancellable) {
          draftNotesEventSink(AddedLast(it))
        }
      }
    }
  }

  fun update(data: GitLabDiscussionRestDTO) {
    dataState.value = data
  }

  override fun toString(): String =
    "LoadedGitLabDiscussion(id='$id', createdAt=$createdAt)"
}