// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.changeMergeRequestDiscussionResolve
import java.util.*

interface GitLabDiscussion {
  val createdAt: Date
  val notes: Flow<List<GitLabNote>>

  val canResolve: Boolean
  val resolved: Flow<Boolean>

  suspend fun changeResolvedState()
}

class LoadedGitLabDiscussion(
  parentCs: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val discussion: GitLabDiscussionDTO
) : GitLabDiscussion {
  init {
    require(discussion.notes.isNotEmpty()) { "Discussion with empty notes" }
  }

  override val createdAt: Date = discussion.createdAt

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e ->
    logger<GitLabDiscussion>().info(e.localizedMessage)
  })

  private val operationsGuard = Mutex()

  private val loadedNotes = MutableStateFlow(discussion.notes)
  override val notes: Flow<List<GitLabNote>> = loadedNotes.map { notes ->
    notes.map { LoadedGitLabNote(it) }
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
        loadedNotes.value = result.notes
      }
    }
  }
}