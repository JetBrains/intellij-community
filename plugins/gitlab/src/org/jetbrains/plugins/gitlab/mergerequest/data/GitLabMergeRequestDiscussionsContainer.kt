// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addDiffNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestDiscussions

interface GitLabMergeRequestDiscussionsContainer {
  val discussions: Flow<Collection<GitLabMergeRequestDiscussion>>
  val systemNotes: Flow<Collection<GitLabNote>>

  val canAddNotes: Boolean

  suspend fun addNote(body: String)

  // not a great idea to pass a dto, but otherwise it's a pain in the neck to calc positions
  suspend fun addNote(position: GitLabDiffPositionInput, body: String)
}

private val LOG = logger<GitLabMergeRequestDiscussionsContainer>()

class GitLabMergeRequestDiscussionsContainerImpl(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
  private val mr: GitLabMergeRequest
) : GitLabMergeRequestDiscussionsContainer {

  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val canAddNotes: Boolean = mr.userPermissions.value.createNote

  private val discussionEvents = MutableSharedFlow<GitLabDiscussionEvent>()
  private val nonEmptyDiscussionsData: Flow<List<GitLabDiscussionDTO>> =
    channelFlow {
      val discussions = loadNonEmptyDiscussions().toMutableList()
      launch(start = CoroutineStart.UNDISPATCHED) {
        discussionEvents.collectLatest { e ->
          when (e) {
            is GitLabDiscussionEvent.Deleted -> {
              discussions.removeIf { it.id == e.discussionId }
              LOG.debug("Discussion removed: ${e.discussionId}")
            }
            is GitLabDiscussionEvent.Added -> {
              discussions.add(e.discussion)
              LOG.debug("New discussion added: ${e.discussion}")
            }
          }
          send(discussions)
        }
      }
      send(discussions)
    }.modelFlow(cs, LOG)

  override val discussions: Flow<List<GitLabMergeRequestDiscussion>> =
    nonEmptyDiscussionsData
      .mapFiltered { !it.notes.first().system }
      .mapCaching(
        GitLabDiscussionDTO::id,
        { cs, disc -> LoadedGitLabDiscussion(cs, api, project, { discussionEvents.emit(it) }, mr, disc) },
        LoadedGitLabDiscussion::destroy
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

  private suspend fun loadNonEmptyDiscussions(): List<GitLabDiscussionDTO> =
    ApiPageUtil.createGQLPagesFlow {
      api.loadMergeRequestDiscussions(project, mr.id, it)
    }.map { discussions ->
      discussions.filter { it.notes.isNotEmpty() }
    }.foldToList()

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
}