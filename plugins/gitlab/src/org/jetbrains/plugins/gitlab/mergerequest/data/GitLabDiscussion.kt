// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
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
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.changeMergeRequestDiscussionResolve
import org.jetbrains.plugins.gitlab.mergerequest.api.request.createReplyNote
import java.util.*

interface GitLabDiscussion {
  val id: String

  val createdAt: Date
  val notes: Flow<List<GitLabNote>>
  val canAddNotes: Boolean

  val position: Flow<GitLabDiscussionPosition?>

  val canResolve: Boolean
  val resolved: Flow<Boolean>

  suspend fun changeResolvedState()

  suspend fun addNote(body: String)
}

private val LOG = logger<GitLabDiscussion>()

class LoadedGitLabDiscussion(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
  private val eventSink: suspend (GitLabDiscussionEvent) -> Unit,
  private val mr: GitLabMergeRequestDTO,
  private val discussionData: GitLabDiscussionDTO
) : GitLabDiscussion {
  init {
    require(discussionData.notes.isNotEmpty()) { "Discussion with empty notes" }
  }

  override val id: String = discussionData.id
  override val createdAt: Date = discussionData.createdAt

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val operationsGuard = Mutex()

  private val noteEvents = MutableSharedFlow<GitLabNoteEvent>()
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

  override val notes: Flow<List<GitLabNote>> =
    loadedNotes
      .mapCaching(
        GitLabNoteDTO::id,
        { cs, note -> LoadedGitLabNote(cs, api, project, { noteEvents.emit(it) }, note) },
        LoadedGitLabNote::destroy,
        LoadedGitLabNote::update
      )
      .modelFlow(cs, LOG)

  override val canAddNotes: Boolean = mr.userPermissions.createNote

  override val position: Flow<GitLabDiscussionPosition?> = loadedNotes.map { it.first().let(::convertPosition) }

  // a little cheat that greatly simplifies the implementation
  override val canResolve: Boolean = discussionData.notes.first().let { it.resolvable && it.userPermissions.resolveNote }
  override val resolved: Flow<Boolean> =
    loadedNotes
      .map { it.first().resolved }
      .distinctUntilChanged()
      .modelFlow(cs, LOG)

  override suspend fun changeResolvedState() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        val resolved = resolved.first()
        val result = withContext(Dispatchers.IO) {
          api.changeMergeRequestDiscussionResolve(project, discussionData.id, !resolved).getResultOrThrow()
        }
        noteEvents.emit(GitLabNoteEvent.Changed(result.notes))
      }
    }
  }

  override suspend fun addNote(body: String) {
    withContext(cs.coroutineContext) {
      withContext(Dispatchers.IO) {
        api.createReplyNote(project, mr.id, id, body).getResultOrThrow()
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

  override fun toString(): String = "LoadedGitLabDiscussion(id='$id', createdAt=$createdAt, canResolve=$canResolve)"
}

private fun convertPosition(note: GitLabNoteDTO): GitLabDiscussionPosition? {
  val position = note.position ?: return null
  if (position.diffRefs.baseSha == null) {
    LOG.warn("Missing merge base in note position: $note")
    return null
  }

  val parentSha = position.diffRefs.baseSha
  val sha = position.diffRefs.headSha

  return when (position.positionType) {
    "text" -> {
      val location = if (position.oldLine != null) {
        DiffLineLocation(Side.LEFT, position.oldLine - 1)
      }
      else {
        DiffLineLocation(Side.RIGHT, position.newLine!! - 1)
      }
      GitLabDiscussionPosition.Text(parentSha, sha, position.oldPath, position.newPath, location)
    }
    else -> GitLabDiscussionPosition.Image(parentSha, sha, position.oldPath, position.newPath)
  }
}