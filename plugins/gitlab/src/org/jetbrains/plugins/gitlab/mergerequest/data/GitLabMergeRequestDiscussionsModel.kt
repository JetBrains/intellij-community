// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestDiscussions
import java.util.concurrent.ConcurrentHashMap

//TODO: granualar collection changes notifications
interface GitLabMergeRequestDiscussionsModel {
  val userDiscussions: Flow<Collection<GitLabDiscussion>>
  val systemDiscussions: Flow<Collection<GitLabDiscussionDTO>>
}

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestDiscussionsModelImpl(
  parentCs: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val mr: GitLabMergeRequestId
) : GitLabMergeRequestDiscussionsModel {

  private val cs = parentCs.childScope(Dispatchers.Default)
  private val events = MutableSharedFlow<GitLabDiscussionEvent>(extraBufferCapacity = 64)

  private val nonEmptyDiscussionsData = flow {
    emit(loadNonEmptyDiscussions())
  }.shareIn(cs, SharingStarted.Lazily, 1)

  override val userDiscussions: Flow<Collection<GitLabDiscussion>> = nonEmptyDiscussionsData.transformLatest { loadedDiscussions ->
    coroutineScope {
      val discussionsCs = this
      val discussions = ConcurrentHashMap<String, LoadedGitLabDiscussion>()
      loadedDiscussions.associateByTo(discussions, GitLabDiscussionDTO::id) { noteData ->
        LoadedGitLabDiscussion(discussionsCs, connection, { events.emit(it) }, noteData)
      }
      emit(discussions.values.toList())

      events.collectLatest {
        when (it) {
          is GitLabDiscussionEvent.DiscussionDeleted -> {
            discussions.remove(it.discussionId)?.destroy()
          }
        }
        emit(discussions.values.toList())
      }

      awaitCancellation()
    }
  }.shareIn(cs, SharingStarted.Lazily, 1)

  override val systemDiscussions: Flow<List<GitLabDiscussionDTO>> =
    nonEmptyDiscussionsData.map { discussions ->
      discussions.filter { it.notes.first().system }
    }.shareIn(cs, SharingStarted.Lazily, 1)

  private suspend fun loadNonEmptyDiscussions(): List<GitLabDiscussionDTO> =
    ApiPageUtil.createGQLPagesFlow {
      connection.apiClient.loadMergeRequestDiscussions(connection.repo.repository, mr, it)
    }.map { discussions ->
      discussions.filter { it.notes.isNotEmpty() }
    }.foldToList()
}