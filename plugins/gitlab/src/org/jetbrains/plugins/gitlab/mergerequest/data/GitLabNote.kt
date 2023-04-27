// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.mergerequest.api.request.deleteNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.updateNote
import java.util.*

interface GitLabNote {
  val id: String
  val author: GitLabUserDTO
  val createdAt: Date

  val body: StateFlow<String>
  val resolved: StateFlow<Boolean>
}

interface MutableGitLabNote : GitLabNote {
  val canAdmin: Boolean

  suspend fun setBody(newText: String)
  suspend fun delete()
}

interface GitLabMergeRequestNote : GitLabNote {
  val position: StateFlow<GitLabNotePosition?>
  val positionMapping: Flow<GitLabMergeRequestNotePositionMapping?>
}

private val LOG = logger<GitLabDiscussion>()

@OptIn(ExperimentalCoroutinesApi::class)
class MutableGitLabMergeRequestNote(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
  private val mr: GitLabMergeRequest,
  private val eventSink: suspend (GitLabNoteEvent) -> Unit,
  private val noteData: GitLabNoteDTO
) : GitLabMergeRequestNote, MutableGitLabNote {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val operationsGuard = Mutex()

  override val id: String = noteData.id
  override val author: GitLabUserDTO = noteData.author
  override val createdAt: Date = noteData.createdAt
  override val canAdmin: Boolean = noteData.userPermissions.adminNote

  private val data = MutableStateFlow(noteData)
  override val body: StateFlow<String> = data.mapState(cs, GitLabNoteDTO::body)
  override val resolved: StateFlow<Boolean> = data.mapState(cs, GitLabNoteDTO::resolved)
  override val position: StateFlow<GitLabNotePosition?> = data.mapState(cs) {
    it.position?.let(GitLabNotePosition::from)
  }
  override val positionMapping: Flow<GitLabMergeRequestNotePositionMapping?> = position.flatMapLatest { position ->
    if (position == null) return@flatMapLatest flowOf(null)

    mr.changes.map {
      try {
        val allChanges = it.getParsedChanges()
        GitLabMergeRequestNotePositionMapping.map(allChanges, position)
      }
      catch (e: Exception) {
        GitLabMergeRequestNotePositionMapping.Error(e)
      }
    }
  }.modelFlow(cs, LOG)


  override suspend fun setBody(newText: String) {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          api.updateNote(project, noteData.id, newText).getResultOrThrow()
        }
      }
      data.update { it.copy(body = newText) }
    }
  }

  override suspend fun delete() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          api.deleteNote(project, noteData.id).getResultOrThrow()
        }
      }
      eventSink(GitLabNoteEvent.Deleted(noteData.id))
    }
  }

  fun update(item: GitLabNoteDTO) {
    data.value = item
  }

  suspend fun destroy() {
    try {
      cs.coroutineContext[Job]!!.cancelAndJoin()
    }
    catch (ce: CancellationException) {
      // ignore, cuz we don't want to cancel the invoker
    }
  }

  override fun toString(): String =
    "MutableGitLabNote(id='$id', author=$author, createdAt=$createdAt, canAdmin=$canAdmin, body=${body.value}, resolved=${resolved.value}, position=${position.value})"
}

class GitLabSystemNote(noteData: GitLabNoteDTO) : GitLabNote {

  override val id: String = noteData.id
  override val author: GitLabUserDTO = noteData.author
  override val createdAt: Date = noteData.createdAt

  private val _body = MutableStateFlow(noteData.body)
  override val body: StateFlow<String> = _body.asStateFlow()
  override val resolved: StateFlow<Boolean> = MutableStateFlow(false)

  override fun toString(): String =
    "ImmutableGitLabNote(id='$id', author=$author, createdAt=$createdAt, body=${_body.value})"
}

