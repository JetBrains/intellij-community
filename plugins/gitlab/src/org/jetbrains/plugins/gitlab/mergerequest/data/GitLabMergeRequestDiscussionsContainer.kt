// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*

interface GitLabMergeRequestDiscussionsContainer {
  val discussions: Flow<Collection<GitLabMergeRequestDiscussion>>
  val systemNotes: Flow<Collection<GitLabNote>>
  val standaloneDraftNotes: Flow<Collection<GitLabMergeRequestDraftNote>>

  val canAddNotes: Boolean

  suspend fun addNote(body: String)

  // not a great idea to pass a dto, but otherwise it's a pain in the neck to calc positions
  suspend fun addNote(position: GitLabDiffPositionInput, body: String)
}

private val LOG = logger<GitLabMergeRequestDiscussionsContainer>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestDiscussionsContainerImpl(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
  private val mr: GitLabMergeRequest
) : GitLabMergeRequestDiscussionsContainer {

  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val canAddNotes: Boolean = mr.userPermissions.value.createNote

  private val reloadRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
    tryEmit(Unit)
  }
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val discussionEvents = MutableSharedFlow<GitLabDiscussionEvent>()
  private val nonEmptyDiscussionsData: Flow<List<GitLabDiscussionDTO>> =
    reloadRequests.transformLatest { collectNonEmptyDiscussions() }.modelFlow(cs, LOG)

  private suspend fun FlowCollector<List<GitLabDiscussionDTO>>.collectNonEmptyDiscussions() {
    supervisorScope {
      val discussions = LinkedHashMap<String, GitLabDiscussionDTO>()
      val discussionsGuard = Mutex()
      var lastCursor: String? = null
      ApiPageUtil.createGQLPagesFlow {
        api.loadMergeRequestDiscussions(project, mr.id, it)
      }.collect { page ->
        discussionsGuard.withLock {
          for (dto in page.nodes.filter { it.notes.isNotEmpty() }) {
            discussions[dto.id] = dto
          }
        }
        lastCursor = page.pageInfo.startCursor
      }
      if (lastCursor != null) {
        launchNow {
          updateRequests.collect {
            val page = api.loadMergeRequestDiscussions(project, mr.id, GraphQLRequestPagination(lastCursor!!))
            val newDiscussions = page?.nodes
            if (newDiscussions != null) {
              discussionsGuard.withLock {
                for (dto in newDiscussions) {
                  discussions[dto.id] = dto
                }
                emit(discussions.values.toList())
              }
            }
            lastCursor = page?.pageInfo?.startCursor
            if (lastCursor == null) {
              currentCoroutineContext().cancel()
            }
          }
        }
      }
      launchNow {
        discussionEvents.collect { e ->
          discussionsGuard.withLock {
            when (e) {
              is GitLabDiscussionEvent.Deleted -> {
                discussions.remove(e.discussionId)
                LOG.debug("Discussion removed: ${e.discussionId}")
              }
              is GitLabDiscussionEvent.Added -> {
                discussions[e.discussion.id] = e.discussion
                LOG.debug("New discussion added: ${e.discussion}")
              }
            }
            emit(discussions.values.toList())
          }
        }
      }
      discussionsGuard.withLock {
        emit(discussions.values.toList())
      }
    }
  }

  override val discussions: Flow<List<GitLabMergeRequestDiscussion>> =
    nonEmptyDiscussionsData
      .mapFiltered { !it.notes.first().system }
      .mapCaching(
        GitLabDiscussionDTO::id,
        { cs, disc -> LoadedGitLabDiscussion(cs, api, project, { discussionEvents.emit(it) }, mr, disc, getDiscussionDraftNotes(disc.id)) },
        LoadedGitLabDiscussion::destroy,
        LoadedGitLabDiscussion::update
      )
      .modelFlow(cs, LOG)

  override val systemNotes: Flow<List<GitLabNote>> =
    nonEmptyDiscussionsData
      .mapFiltered { it.notes.first().system }
      .map { discussions -> discussions.map { it.notes.first() } }
      .mapCaching(
        GitLabNoteDTO::id,
        { _, note -> GitLabSystemNote(note) },
        {}
      )
      .modelFlow(cs, LOG)

  private val draftNotesEvents = MutableSharedFlow<GitLabNoteEvent<GitLabMergeRequestDraftNoteRestDTO>>()

  private val draftNotesData = reloadRequests.transformLatest { collectDraftNotes() }.modelFlow(cs, LOG)

  private suspend fun FlowCollector<List<DraftNoteWithAuthor>>.collectDraftNotes() {
    supervisorScope {
      // we shouldn't get another user's draft notes
      val currentUser = api.getCurrentUser(project.serverPath) ?: error("Unable to load current user")

      val notesGuard = Mutex()
      val draftNotes = LinkedHashMap<String, GitLabMergeRequestDraftNoteRestDTO>()

      var lastETag: String? = null
      val uri = getMergeRequestDraftNotesUri(project, mr.id)
      ApiPageUtil.createPagesFlowByLinkHeader(uri) {
        api.loadUpdatableJsonList<GitLabMergeRequestDraftNoteRestDTO>(it)
      }.collect {
        val newNotes = it.body() ?: error("Empty response")
        notesGuard.withLock {
          for (note in newNotes) {
            draftNotes[note.id.toString()] = note
          }
        }
        lastETag = it.headers().firstValue("ETag").orElse(null)
      }

      if (lastETag != null) {
        launchNow {
          updateRequests.collect {
            val response = api.loadUpdatableJsonList<GitLabMergeRequestDraftNoteRestDTO>(uri, lastETag)
            val newNotes = response.body()
            if (newNotes != null) {
              notesGuard.withLock {
                for (note in newNotes) {
                  draftNotes[note.id.toString()] = note
                }
                emit(draftNotes.values.map { DraftNoteWithAuthor(it, currentUser) })
              }
            }
            lastETag = response.headers().firstValue("ETag").orElse(null)
            if (lastETag == null) {
              currentCoroutineContext().cancel()
            }
          }
        }
      }
      launchNow {
        draftNotesEvents.collect { e ->
          notesGuard.withLock {
            when (e) {
              is GitLabNoteEvent.Added -> draftNotes[e.note.id.toString()] = e.note
              is GitLabNoteEvent.Deleted -> draftNotes.remove(e.noteId)
              else -> Unit
            }
            emit(draftNotes.values.map { DraftNoteWithAuthor(it, currentUser) })
          }
        }
      }
      notesGuard.withLock {
        emit(draftNotes.values.map { DraftNoteWithAuthor(it, currentUser) })
      }
    }
  }

  override val standaloneDraftNotes: Flow<Collection<GitLabMergeRequestDraftNote>> =
    draftNotesData.mapFiltered {
      it.note.discussionId == null
    }.mapCaching(
      { it.note.id },
      { cs, (note, author) -> GitLabMergeRequestDraftNoteImpl(cs, api, project, mr, draftNotesEvents::emit, note, author) },
      GitLabMergeRequestDraftNoteImpl::destroy,
      { update(it.note) }
    ).modelFlow(cs, LOG)

  private data class DraftNoteWithAuthor(val note: GitLabMergeRequestDraftNoteRestDTO, val author: GitLabUserDTO)

  private fun getDiscussionDraftNotes(discussionGid: String): Flow<List<GitLabMergeRequestDraftNote>> =
    draftNotesData.mapFiltered {
      it.note.discussionId != null && discussionGid.endsWith(it.note.discussionId)
    }.mapCaching(
      { it.note.id },
      { cs, (note, author) -> GitLabMergeRequestDraftNoteImpl(cs, api, project, mr, draftNotesEvents::emit, note, author) },
      GitLabMergeRequestDraftNoteImpl::destroy
    )

  override suspend fun addNote(body: String) {
    withContext(cs.coroutineContext) {
      withContext(Dispatchers.IO) {
        api.addNote(project, mr.gid, body).getResultOrThrow()
      }.also {
        withContext(NonCancellable) {
          discussionEvents.emit(GitLabDiscussionEvent.Added(it))
        }
      }
    }
  }

  override suspend fun addNote(position: GitLabDiffPositionInput, body: String) {
    withContext(cs.coroutineContext) {
      withContext(Dispatchers.IO) {
        api.addDiffNote(project, mr.gid, position, body).getResultOrThrow()
      }.also {
        withContext(NonCancellable) {
          discussionEvents.emit(GitLabDiscussionEvent.Added(it))
        }
      }
    }
  }

  private fun <T> Flow<List<T>>.mapFiltered(predicate: (T) -> Boolean): Flow<List<T>> = map { it.filter(predicate) }

  fun requestReload() {
    cs.launch {
      reloadRequests.emit(Unit)
    }
  }

  fun checkUpdates() {
    cs.launch {
      updateRequests.emit(Unit)
    }
  }
}