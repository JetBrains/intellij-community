// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.mapScoped
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestDiscussions

interface GitLabMergeRequestDiscussionsModel {
  val userDiscussions: Flow<List<GitLabDiscussion>>
  val systemDiscussions: Flow<List<GitLabDiscussionDTO>>
}

class GitLabMergeRequestDiscussionsModelImpl(
  parentCs: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val mr: GitLabMergeRequestId
) : GitLabMergeRequestDiscussionsModel {

  private val cs = parentCs.childScope(Dispatchers.Default)

  private val nonEmptyDiscussionsData = flow {
    ApiPageUtil.createGQLPagesFlow {
      connection.apiClient.loadMergeRequestDiscussions(connection.repo.repository, mr, it)
    }.map { discussions ->
      discussions.filter { it.notes.isNotEmpty() }
    }.foldToList()
      .let { emit(it) }
  }.shareIn(cs, SharingStarted.Lazily, 1)

  override val userDiscussions: Flow<List<GitLabDiscussion>> =
    nonEmptyDiscussionsData.mapScoped { discussions ->
      val cs = this
      discussions.filter { !it.notes.first().system }
        .map { LoadedGitLabDiscussion(cs, connection, it) }
    }

  override val systemDiscussions: Flow<List<GitLabDiscussionDTO>> =
    nonEmptyDiscussionsData.map { discussions ->
      discussions.filter { it.notes.first().system }
    }
}