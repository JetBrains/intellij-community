// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.async.*
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

interface GitLabMergeRequestDiscussionsContainer {
  val nonEmptyDiscussionsData: SharedFlow<Result<List<GitLabDiscussionDTO>>>
  val draftNotesData: SharedFlow<Result<List<GitLabMergeRequestDraftNoteRestDTO>>>

  val discussions: Flow<Result<Collection<GitLabMergeRequestDiscussion>>>
  val systemNotes: Flow<Result<Collection<GitLabNote>>>
  val draftNotes: Flow<Result<Collection<GitLabMergeRequestDraftNote>>>

  val canAddNotes: Boolean
  val canAddDraftNotes: Boolean
  val canAddPositionalDraftNotes: Boolean

  suspend fun addNote(body: String)

  suspend fun addNote(position: GitLabMergeRequestNewDiscussionPosition, body: String)

  suspend fun addDraftNote(body: String)

  suspend fun addDraftNote(position: GitLabMergeRequestNewDiscussionPosition, body: String)

  suspend fun submitDraftNotes()
}

private val LOG = logger<GitLabMergeRequestDiscussionsContainer>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestDiscussionsContainerImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val api: GitLabApi,
  private val glMetadata: GitLabServerMetadata?,
  private val glProject: GitLabProjectCoordinates,
  private val mr: GitLabMergeRequest
) : GitLabMergeRequestDiscussionsContainer {

  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val canAddNotes: Boolean = mr.details.value.userPermissions.createNote
  override val canAddDraftNotes: Boolean =
    canAddNotes && (glMetadata != null && GitLabVersion(15, 10) <= glMetadata.version)
  override val canAddPositionalDraftNotes: Boolean =
    canAddNotes && (glMetadata != null && GitLabVersion(16, 3) <= glMetadata.version)

  private val reloadRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
    tryEmit(Unit)
  }
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val discussionEvents = MutableSharedFlow<GitLabDiscussionEvent>()
  override val nonEmptyDiscussionsData: SharedFlow<Result<List<GitLabDiscussionDTO>>> =
    reloadRequests.transformLatest { collectNonEmptyDiscussions() }.modelFlow(cs, LOG)

  private suspend fun FlowCollector<Result<List<GitLabDiscussionDTO>>>.collectNonEmptyDiscussions() {
    runCatchingUser {
      supervisorScope {
        val discussions = LinkedHashMap<GitLabId, GitLabDiscussionDTO>()
        val discussionsGuard = Mutex()
        var lastCursor: String? = null
        ApiPageUtil.createGQLPagesFlow {
          api.graphQL.loadMergeRequestDiscussions(glProject, mr.iid, it)
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
              val page = api.graphQL.loadMergeRequestDiscussions(glProject, mr.iid, GraphQLRequestPagination(lastCursor!!))
              val newDiscussions = page?.nodes
              if (newDiscussions != null) {
                discussionsGuard.withLock {
                  for (dto in newDiscussions) {
                    discussions[dto.id] = dto
                  }
                  emit(Result.success(discussions.values.toList()))
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
              emit(Result.success(discussions.values.toList()))
            }
          }
        }
        discussionsGuard.withLock {
          emit(Result.success(discussions.values.toList()))
        }
      }
    }.onFailure { emit(Result.failure(it)) }
  }

  override val discussions: Flow<Result<List<GitLabMergeRequestDiscussion>>> =
    nonEmptyDiscussionsData
      .transformConsecutiveSuccesses {
        mapFiltered { !it.notes.first().system }
          .mapDataToModel(
            GitLabDiscussionDTO::id,
            { disc ->
              LoadedGitLabDiscussion(this,
                                     project, api, glMetadata, glProject,
                                     { discussionEvents.emit(it) }, { draftNotesEvents.emit(it) },
                                     mr, disc, getDiscussionDraftNotes(disc.id).throwFailure())
            },
            LoadedGitLabDiscussion::update
          )
      }
      .modelFlow(cs, LOG)

  override val systemNotes: Flow<Result<List<GitLabNote>>> =
    nonEmptyDiscussionsData
      .transformConsecutiveSuccesses {
        // When one note in a discussion is a system note, all are, so we check the first.
        mapFiltered { it.notes.first().system }
          .map { discussions -> discussions.map { it.notes.first() } }
          .mapDataToModel(
            GitLabNoteDTO::id,
            { note -> GitLabSystemNote(note) },
            { } //constant
          )
      }
      .modelFlow(cs, LOG)

  private val draftNotesEvents = MutableSharedFlow<GitLabNoteEvent<GitLabMergeRequestDraftNoteRestDTO>>()

  override val draftNotesData: SharedFlow<Result<List<GitLabMergeRequestDraftNoteRestDTO>>> =
    reloadRequests.transformLatest { collectDraftNotes() }.modelFlow(cs, LOG)

  private suspend fun FlowCollector<Result<List<GitLabMergeRequestDraftNoteRestDTO>>>.collectDraftNotes() {
    runCatching {
      supervisorScope {
        if (glMetadata == null || glMetadata.version < GitLabVersion(15, 9)) {
          emit(Result.success(listOf()))
          currentCoroutineContext().cancel()
        }

        val notesGuard = Mutex()
        val draftNotes = LinkedHashMap<GitLabId, GitLabMergeRequestDraftNoteRestDTO>()

        var lastETag: String? = null
        val uri = getMergeRequestDraftNotesUri(glProject, mr.iid)
        ApiPageUtil.createPagesFlowByLinkHeader(uri) {
          api.rest.loadUpdatableJsonList<GitLabMergeRequestDraftNoteRestDTO>(
            GitLabApiRequestName.REST_GET_DRAFT_NOTES, it
          )
        }.collect {
          val newNotes = it.body() ?: error("Empty response")
          notesGuard.withLock {
            for (note in newNotes) {
              draftNotes[note.id] = note
            }
          }
          lastETag = it.headers().firstValue("ETag").orElse(null)
        }

        if (lastETag != null) {
          launchNow {
            updateRequests.collect {
              val response = api.rest.loadUpdatableJsonList<GitLabMergeRequestDraftNoteRestDTO>(
                GitLabApiRequestName.REST_GET_DRAFT_NOTES, uri, lastETag
              )
              val newNotes = response.body()
              if (newNotes != null) {
                notesGuard.withLock {
                  for (note in newNotes) {
                    draftNotes[note.id] = note
                  }
                  emit(Result.success(draftNotes.values.toList()))
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
                is GitLabNoteEvent.Added -> draftNotes[e.note.id] = e.note
                is GitLabNoteEvent.Deleted -> draftNotes.remove(e.noteId)
                is GitLabNoteEvent.AllDeleted -> draftNotes.clear()
                else -> Unit
              }
              emit(Result.success(draftNotes.values.toList()))
            }
          }
        }
        notesGuard.withLock {
          emit(Result.success(draftNotes.values.toList()))
        }
      }
    }
  }

  override val draftNotes: Flow<Result<Collection<GitLabMergeRequestDraftNote>>> = flow {
    // we shouldn't get another user's draft notes
    val currentUser = api.graphQL.getCurrentUser()
    draftNotesData
      .transformConsecutiveSuccesses {
        mapDataToModel(
          GitLabMergeRequestDraftNoteRestDTO::id,
          { GitLabMergeRequestDraftNoteImpl(this, api, glMetadata, glProject, mr, { draftNotesEvents::emit }, it, currentUser) },
          { update(it) }
        )
      }.collect(this)
  }.modelFlow(cs, LOG)

  private fun getDiscussionDraftNotes(discussionId: GitLabId): Flow<Result<List<GitLabMergeRequestDraftNote>>> {
    // Convert discussion ID down to REST ID as it's safer than converting from REST to GQL
    val discussionRestId = discussionId.guessRestId()
    return draftNotes
      .map { result ->
        result.map { notes ->
          notes.filter { it.discussionId?.guessRestId() == discussionRestId }
        }
      }
  }

  override suspend fun addNote(body: String) {
    withContext(cs.coroutineContext) {
      val newDiscussion = withContext(Dispatchers.IO) {
        api.graphQL.addNote(mr.gid, body).getResultOrThrow()
      }

      withContext(NonCancellable) {
        discussionEvents.emit(GitLabDiscussionEvent.Added(newDiscussion))
      }
    }
  }

  override suspend fun addNote(position: GitLabMergeRequestNewDiscussionPosition, body: String) {
    withContext(cs.coroutineContext) {
      val newDiscussion = withContext(Dispatchers.IO) {
        api.graphQL.addDiffNote(mr.gid, GitLabDiffPositionInput.from(position), body).getResultOrThrow()
      }

      withContext(NonCancellable) {
        discussionEvents.emit(GitLabDiscussionEvent.Added(newDiscussion))
      }
    }
  }

  override suspend fun addDraftNote(body: String) {
    withContext(cs.coroutineContext) {
      val newNote = withContext(Dispatchers.IO) {
        api.rest.addDraftNote(glProject, mr.iid, null, body).body()
      }

      withContext(NonCancellable) {
        draftNotesEvents.emit(GitLabNoteEvent.Added(newNote))
      }
    }
  }

  override suspend fun addDraftNote(position: GitLabMergeRequestNewDiscussionPosition, body: String) {
    withContext(cs.coroutineContext) {
      val newNote = withContext(Dispatchers.IO) {
        api.rest.addDraftNote(glProject, mr.iid, GitLabDiffPositionInput.from(position), body).body()
      }

      withContext(NonCancellable) {
        draftNotesEvents.emit(GitLabNoteEvent.Added(newNote))
      }
    }
  }

  override suspend fun submitDraftNotes() {
    withContext(cs.coroutineContext) {
      // Don't do anything if the endpoint is not implemented
      if (glMetadata == null || glMetadata.version < GitLabVersion(15, 11)) {
        return@withContext
      }

      withContext(Dispatchers.IO) {
        api.rest.submitDraftNotes(glProject, mr.iid)
      }
      withContext(NonCancellable) {
        draftNotesEvents.emit(GitLabNoteEvent.AllDeleted())
        reloadRequests.emit(Unit)
      }
    }
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.SUBMIT_DRAFT_NOTES)
  }

  suspend fun requestDiscussionsReload() {
    reloadRequests.emit(Unit)
  }

  suspend fun checkUpdates() {
    updateRequests.emit(Unit)
  }
}