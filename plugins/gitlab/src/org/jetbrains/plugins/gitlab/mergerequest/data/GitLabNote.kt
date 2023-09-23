// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.deleteDraftNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.deleteNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.updateDraftNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.updateNote
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.util.*

interface GitLabNote {
  val id: String
  val author: GitLabUserDTO
  val createdAt: Date?

  val body: StateFlow<String>
  val resolved: StateFlow<Boolean>
}

interface MutableGitLabNote : GitLabNote {
  val canAdmin: Boolean

  /**
   * Whether the note can be edited.
   */
  suspend fun canEdit(): Boolean

  suspend fun setBody(newText: String)
  suspend fun delete()
}

interface GitLabMergeRequestNote : GitLabNote {
  val position: StateFlow<GitLabNotePosition?>
  val positionMapping: Flow<GitLabMergeRequestNotePositionMapping?>
}

interface GitLabMergeRequestDraftNote : GitLabMergeRequestNote, MutableGitLabNote {
  val discussionId: String?
  override val createdAt: Date? get() = null
  override val canAdmin: Boolean get() = true
  override val resolved: StateFlow<Boolean> get() = MutableStateFlow(false)
}

private val LOG = logger<GitLabDiscussion>()

class MutableGitLabMergeRequestNote(
  parentCs: CoroutineScope,
  private val project: Project,
  private val api: GitLabApi,
  private val glProject: GitLabProjectCoordinates,
  mr: GitLabMergeRequest,
  private val eventSink: suspend (GitLabNoteEvent<GitLabNoteDTO>) -> Unit,
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
  override val positionMapping: Flow<GitLabMergeRequestNotePositionMapping?> = position.mapPosition(mr).modelFlow(cs, LOG)
  override suspend fun canEdit(): Boolean = true

  override suspend fun setBody(newText: String) {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          api.graphQL.updateNote(noteData.id, newText).getResultOrThrow()
        }
      }
      data.update { it.copy(body = newText) }
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UPDATE_NOTE)
  }

  override suspend fun delete() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          api.graphQL.deleteNote(noteData.id).getResultOrThrow()
        }
      }
      eventSink(GitLabNoteEvent.Deleted(noteData.id))
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.DELETE_NOTE)
  }

  fun update(item: GitLabNoteDTO) {
    data.value = item
  }

  suspend fun destroy() = cs.cancelAndJoinSilently()

  override fun toString(): String =
    "MutableGitLabNote(id='$id', author=$author, createdAt=$createdAt, canAdmin=$canAdmin, body=${body.value}, resolved=${resolved.value}, position=${position.value})"
}

class GitLabMergeRequestDraftNoteImpl(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
  private val mr: GitLabMergeRequest,
  private val eventSink: suspend (GitLabNoteEvent<GitLabMergeRequestDraftNoteRestDTO>) -> Unit,
  private val noteData: GitLabMergeRequestDraftNoteRestDTO,
  override val author: GitLabUserDTO
) : GitLabMergeRequestDraftNote, MutableGitLabNote {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val operationsGuard = Mutex()

  override val id: String = noteData.id.toString()
  override val discussionId: String? = noteData.discussionId

  private val data = MutableStateFlow(noteData)
  override val body: StateFlow<String> = data.mapState(cs, GitLabMergeRequestDraftNoteRestDTO::note)

  override val position: StateFlow<GitLabNotePosition?> = data.mapState(cs) { it.position.let(GitLabNotePosition::from) }
  override val positionMapping: Flow<GitLabMergeRequestNotePositionMapping?> = position.mapPosition(mr).modelFlow(cs, LOG)

  override suspend fun canEdit(): Boolean =
    GitLabVersion(15, 10) <= api.getMetadata().version

  @SinceGitLab("15.10")
  override suspend fun setBody(newText: String) {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          // Checked by canEdit
          api.rest.updateDraftNote(project, mr.iid, noteData.id, newText)
        }
      }
      data.update { it.copy(note = newText) }
    }
  }

  override suspend fun delete() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          // Shouldn't require extra check, delete and get draft notes was introduced in
          // the same update
          api.rest.deleteDraftNote(project, mr.iid, noteData.id)
        }
      }
      eventSink(GitLabNoteEvent.Deleted(noteData.id.toString()))
    }
  }

  fun update(item: GitLabMergeRequestDraftNoteRestDTO) {
    data.value = item
  }

  suspend fun destroy() = cs.cancelAndJoinSilently()

  override fun toString(): String =
    "GitLabMergeRequestDraftNoteImpl(id='$id', author=$author, createdAt=$createdAt, body=${body.value})"
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<GitLabNotePosition?>.mapPosition(mr: GitLabMergeRequest): Flow<GitLabMergeRequestNotePositionMapping?> =
  flatMapLatest { position ->
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

