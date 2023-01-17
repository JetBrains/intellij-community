// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.changeMergeRequestDiscussionResolve
import java.util.*

interface GitLabDiscussion {
  val id: String

  val createdAt: Date
  val notes: Flow<List<GitLabNote>>

  val canResolve: Boolean
  val resolved: Flow<Boolean>

  suspend fun changeResolvedState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoadedGitLabDiscussion(
  parentCs: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val eventSink: suspend (GitLabDiscussionEvent) -> Unit,
  private val discussion: GitLabDiscussionDTO
) : GitLabDiscussion {
  init {
    require(discussion.notes.isNotEmpty()) { "Discussion with empty notes" }
  }

  override val id: String = discussion.id
  override val createdAt: Date = discussion.createdAt

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e ->
    logger<GitLabDiscussion>().info(e.localizedMessage)
  })

  private val operationsGuard = Mutex()
  private val events = MutableSharedFlow<GitLabNoteEvent>()

  private val loadedNotes = MutableSharedFlow<List<GitLabNoteDTO>>(1).apply {
    tryEmit(discussion.notes)
  }

  override val notes: Flow<List<GitLabNote>> = loadedNotes.transformLatest<List<GitLabNoteDTO>, List<GitLabNote>> { loadedNotes ->
    coroutineScope {
      val notesCs = this
      val notes = Collections.synchronizedMap(LinkedHashMap<String, LoadedGitLabNote>())
      loadedNotes.associateByTo(notes, GitLabNoteDTO::id) { noteData ->
        LoadedGitLabNote(notesCs, connection, { events.emit(it) }, noteData)
      }
      emit(notes.values.toList())

      events.collectLatest {
        when (it) {
          is GitLabNoteEvent.NoteDeleted -> {
            notes.remove(it.noteId)?.destroy()
          }
        }

        emit(notes.values.toList())
      }
      awaitCancellation()
    }
  }.transform {
    if (it.isEmpty()) {
      eventSink(GitLabDiscussionEvent.DiscussionDeleted(id))
      currentCoroutineContext().cancel()
    }
    emit(it)
  }.shareIn(cs, SharingStarted.Lazily, 1)

  private val firstNote = loadedNotes.map { it.first() }

  // a little cheat that greatly simplifies the implementation
  override val canResolve: Boolean = discussion.notes.first().let { it.resolvable && it.userPermissions.resolveNote }
  override val resolved: Flow<Boolean> = firstNote.map { it.resolved }

  override suspend fun changeResolvedState() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        val resolved = resolved.first()
        val result = withContext(Dispatchers.IO) {
          connection.apiClient
            .changeMergeRequestDiscussionResolve(connection.repo.repository, discussion.id, !resolved).body()!!
        }
        updateNotes(result.notes)
      }
    }
  }

  private suspend fun updateNotes(notes: List<GitLabNoteDTO>) {
    loadedNotes.emit(notes)
  }

  suspend fun destroy() {
    cs.coroutineContext[Job]!!.cancelAndJoin()
  }
}