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
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestDiscussions

interface GitLabMergeRequestDiscussionsModel {
  val userDiscussions: Flow<Collection<GitLabDiscussion>>
  val systemDiscussions: Flow<Collection<GitLabDiscussionDTO>>
}

private val LOG = logger<GitLabMergeRequestDiscussionsModel>()

class GitLabMergeRequestDiscussionsModelImpl(
  parentCs: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val mr: GitLabMergeRequestId
) : GitLabMergeRequestDiscussionsModel {

  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  private val discussionEvents = MutableSharedFlow<GitLabDiscussionEvent>()
  private val nonEmptyDiscussionsData: Flow<List<GitLabDiscussionDTO>> =
    channelFlow {
      val discussions = loadNonEmptyDiscussions().toMutableList()
      launch(start = CoroutineStart.UNDISPATCHED) {
        discussionEvents.collectLatest { e ->
          when (e) {
            is GitLabDiscussionEvent.Deleted -> discussions.removeIf { it.id == e.discussionId }
          }
          send(discussions)
        }
      }
      send(discussions)
    }.modelFlow(cs, LOG)

  override val userDiscussions: Flow<List<GitLabDiscussion>> =
    nonEmptyDiscussionsData
      .mapFiltered { !it.notes.first().system }
      .mapCaching(
        GitLabDiscussionDTO::id,
        { LoadedGitLabDiscussion(cs, connection, { discussionEvents.emit(it) }, it) },
        LoadedGitLabDiscussion::destroy
      )
      .modelFlow(cs, LOG)

  override val systemDiscussions: Flow<List<GitLabDiscussionDTO>> =
    nonEmptyDiscussionsData
      .mapFiltered { it.notes.first().system }
      .modelFlow(cs, LOG)

  private suspend fun loadNonEmptyDiscussions(): List<GitLabDiscussionDTO> =
    ApiPageUtil.createGQLPagesFlow {
      connection.apiClient.loadMergeRequestDiscussions(connection.repo.repository, mr, it)
    }.map { discussions ->
      discussions.filter { it.notes.isNotEmpty() }
    }.foldToList()

  private fun <T> Flow<List<T>>.mapFiltered(predicate: (T) -> Boolean): Flow<List<T>> = map { it.filter(predicate) }
}