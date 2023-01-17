// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.mergerequest.api.request.deleteNote
import java.util.*

interface GitLabNote {
  val author: GitLabUserDTO
  val createdAt: Date
  val canAdmin: Boolean

  val body: Flow<String>

  suspend fun delete()
}

class LoadedGitLabNote(
  parentCs: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val eventSink: suspend (GitLabNoteEvent) -> Unit,
  private val note: GitLabNoteDTO
) : GitLabNote {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e ->
    logger<GitLabDiscussion>().info(e.localizedMessage)
  })

  private val operationsGuard = Mutex()

  override val author: GitLabUserDTO = note.author
  override val createdAt: Date = note.createdAt
  override val canAdmin: Boolean = note.userPermissions.adminNote

  override val body: Flow<String> = flowOf(note.body)

  override suspend fun delete() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          connection.apiClient.deleteNote(connection.repo.repository, note.id).getResultOrThrow()
        }
      }
      withContext(NonCancellable) {
        eventSink(GitLabNoteEvent.NoteDeleted(note.id))
      }
    }
  }

  suspend fun destroy() {
    cs.coroutineContext[Job]!!.cancelAndJoin()
  }
}
