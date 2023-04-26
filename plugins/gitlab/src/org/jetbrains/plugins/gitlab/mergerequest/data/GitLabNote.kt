// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
  val canAdmin: Boolean

  val body: StateFlow<String>

  suspend fun setBody(newText: String)
  suspend fun delete()

  suspend fun update(item: GitLabNoteDTO)
}

private val LOG = logger<GitLabDiscussion>()

class MutableGitLabNote(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
  private val eventSink: suspend (GitLabNoteEvent) -> Unit,
  private val noteData: GitLabNoteDTO
) : GitLabNote {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val operationsGuard = Mutex()

  override val id: String = noteData.id
  override val author: GitLabUserDTO = noteData.author
  override val createdAt: Date = noteData.createdAt
  override val canAdmin: Boolean = noteData.userPermissions.adminNote

  private val _body = MutableStateFlow(noteData.body)
  override val body: StateFlow<String> = _body.asStateFlow()

  override suspend fun setBody(newText: String) {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          api.updateNote(project, noteData.id, newText).getResultOrThrow()
        }
      }
      _body.value = newText
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

  override suspend fun update(item: GitLabNoteDTO) {
    _body.value = item.body
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
    "LoadedGitLabNote(id='$id', author=$author, createdAt=$createdAt, body=${body.value})"
}

class ImmutableGitLabNote(noteData: GitLabNoteDTO) : GitLabNote {

  override val id: String = noteData.id
  override val author: GitLabUserDTO = noteData.author
  override val createdAt: Date = noteData.createdAt
  override val canAdmin: Boolean = noteData.userPermissions.adminNote

  private val _body = MutableStateFlow(noteData.body)
  override val body: StateFlow<String> = _body.asStateFlow()

  override suspend fun setBody(newText: String) = Unit

  override suspend fun delete() = Unit

  override suspend fun update(item: GitLabNoteDTO) {
    _body.value = item.body
  }

  override fun toString(): String =
    "ImmutableGitLabNote(id='$id', author=$author, createdAt=$createdAt, canAdmin=$canAdmin, body=${_body.value})"
}

