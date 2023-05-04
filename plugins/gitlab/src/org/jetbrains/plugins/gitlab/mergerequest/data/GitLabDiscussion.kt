// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.mergerequest.api.request.changeMergeRequestDiscussionResolve
import org.jetbrains.plugins.gitlab.mergerequest.api.request.createReplyNote
import java.util.*

interface GitLabDiscussion {
  val id: String

  val createdAt: Date
  val notes: Flow<List<GitLabNote>>
  val canAddNotes: Boolean

  val resolvable: Boolean
  val canResolve: Boolean
  val resolved: Flow<Boolean>

  suspend fun changeResolvedState()

  suspend fun addNote(body: String)
}

interface GitLabMergeRequestDiscussion : GitLabDiscussion {
  override val notes: Flow<List<GitLabMergeRequestNote>>
}

private val LOG = logger<GitLabDiscussion>()

@OptIn(ExperimentalCoroutinesApi::class)
class LoadedGitLabDiscussion(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
  private val eventSink: suspend (GitLabDiscussionEvent) -> Unit,
  private val mr: GitLabMergeRequest,
  discussionData: GitLabDiscussionDTO,
  draftNotes: Flow<List<GitLabMergeRequestDraftNote>>
) : GitLabMergeRequestDiscussion {
  init {
    require(discussionData.notes.isNotEmpty()) { "Discussion with empty notes" }
  }

  override val id: String = discussionData.id
  private val apiId = discussionData.id
  override val createdAt: Date = discussionData.createdAt

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val operationsGuard = Mutex()

  private val noteEvents = MutableSharedFlow<GitLabNoteEvent<GitLabNoteDTO>>()
  private val loadedNotes = channelFlow {
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
        }

        if (notesData.isEmpty()) {
          eventSink(GitLabDiscussionEvent.Deleted(discussionData.id))
          return@collectLatest
        }

        send(Collections.unmodifiableList(notesData))
      }
    }
    send(Collections.unmodifiableList(notesData))
  }.modelFlow(cs, LOG)

  override val notes: Flow<List<GitLabMergeRequestNote>> =
    loadedNotes
      .mapCaching(
        GitLabNoteDTO::id,
        { cs, note -> MutableGitLabMergeRequestNote(cs, api, project, mr, noteEvents::emit, note) },
        MutableGitLabMergeRequestNote::destroy,
        MutableGitLabMergeRequestNote::update
      ).combine(draftNotes) { notes, draftNotes ->
        notes + draftNotes
      }
      .modelFlow(cs, LOG)

  override val canAddNotes: Boolean = mr.userPermissions.value.createNote

  // a little cheat that greatly simplifies the implementation
  override val resolvable: Boolean = discussionData.notes.first().resolvable
  override val canResolve: Boolean = discussionData.notes.first().userPermissions.resolveNote

  override val resolved: Flow<Boolean> =
    notes.flatMapLatest { it.first().resolved }.distinctUntilChanged().modelFlow(cs, LOG)

  override suspend fun changeResolvedState() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        val resolved = resolved.first()
        val result = withContext(Dispatchers.IO) {
          api.changeMergeRequestDiscussionResolve(project, apiId, !resolved).getResultOrThrow()
        }
        noteEvents.emit(GitLabNoteEvent.Changed(result.notes))
      }
    }
  }

  override suspend fun addNote(body: String) {
    withContext(cs.coroutineContext) {
      withContext(Dispatchers.IO) {
        api.createReplyNote(project, mr.gid, id, body).getResultOrThrow()
      }.also {
        withContext(NonCancellable) {
          noteEvents.emit(GitLabNoteEvent.Added(it))
        }
      }
    }
  }

  suspend fun destroy() {
    try {
      cs.coroutineContext[Job]!!.cancelAndJoin()
    }
    catch (e: CancellationException) {
      // ignore, cuz we don't want to cancel the invoker
    }
  }

  override fun toString(): String =
    "LoadedGitLabDiscussion(id='$id', createdAt=$createdAt, canAddNotes=$canAddNotes, resolvable=$resolvable, canResolve=$canResolve)"
}