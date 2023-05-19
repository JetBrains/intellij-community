// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.collaboration.async.channelWithInitial
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.request.getCurrentUserRest
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabMergeRequestsListLoader
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.util.concurrent.ConcurrentHashMap

interface GitLabProjectMergeRequestsStore {

  fun getListLoader(searchQuery: String): SequentialListLoader<GitLabMergeRequestDetails>

  /**
   * @return a handle for result of loading a shared MR model
   */
  fun getShared(id: GitLabMergeRequestId): SharedFlow<Result<GitLabMergeRequest>>

  /**
   * @return cached short MR details
   */
  fun findCachedDetails(id: GitLabMergeRequestId): GitLabMergeRequestDetails?

  /**
   * Update shared merge request
   */
  suspend fun reloadMergeRequest(id: GitLabMergeRequestId)
}

class CachingGitLabProjectMergeRequestsStore(private val project: Project,
                                             parentCs: CoroutineScope,
                                             private val api: GitLabApi,
                                             private val projectMapping: GitLabProjectMapping,
                                             private val tokenRefreshFlow: Flow<Unit>) : GitLabProjectMergeRequestsStore {

  private val cs = parentCs.childScope()

  private val glProject: GitLabProjectCoordinates = projectMapping.repository

  private val detailsCache = Caffeine.newBuilder()
    .weakValues()
    .build<GitLabMergeRequestId, GitLabMergeRequestDetails>()

  private val models = ConcurrentHashMap<GitLabMergeRequestId, SharedFlow<Result<GitLabMergeRequest>>>()

  private val reloadMergeRequest: MutableSharedFlow<GitLabMergeRequestId> = MutableSharedFlow(1)

  override fun getListLoader(searchQuery: String): SequentialListLoader<GitLabMergeRequestDetails> = CachingListLoader(searchQuery)

  init {
    cs.launch {
      tokenRefreshFlow.collect {
        models.keys.forEach { mrId -> reloadMergeRequest(mrId) }
      }
    }
  }

  override fun getShared(id: GitLabMergeRequestId): SharedFlow<Result<GitLabMergeRequest>> {
    val simpleId = GitLabMergeRequestId.Simple(id)
    return models.getOrPut(simpleId) {
      reloadMergeRequest
        .filter { requestedId -> requestedId == id }
        .channelWithInitial(id)
        .mapScoped { mrId ->
          runCatching {
            // TODO: create from cached details
            val cs = this
            val mrData = loadMergeRequest(mrId)
            LoadedGitLabMergeRequest(project, cs, api, projectMapping, mrData)
          }
        }.shareIn(cs, SharingStarted.WhileSubscribed(0, 0), 1)
      // this the model will only be alive while it's needed
    }
  }

  override fun findCachedDetails(id: GitLabMergeRequestId): GitLabMergeRequestDetails? = detailsCache.getIfPresent(id)

  override suspend fun reloadMergeRequest(id: GitLabMergeRequestId) {
    reloadMergeRequest.emit(id)
  }

  @Throws(HttpStatusErrorException::class, IllegalStateException::class)
  private suspend fun loadMergeRequest(id: GitLabMergeRequestId): GitLabMergeRequestDTO {
    return withContext(Dispatchers.IO) {
      val body = api.loadMergeRequest(glProject, id).body()
      if (body == null) {
        api.getCurrentUserRest(glProject.serverPath) // Exception is generated automatically if status code >= 400
        error(CollaborationToolsBundle.message("http.status.error.unknown"))
      }
      body
    }
  }

  private inner class CachingListLoader(searchQuery: String)
    : SequentialListLoader<GitLabMergeRequestDetails> {
    private val actualLoader = GitLabMergeRequestsListLoader(api, glProject, searchQuery)

    override suspend fun loadNext(): SequentialListLoader.ListBatch<GitLabMergeRequestDetails> {
      return actualLoader.loadNext().also { (data, _) ->
        data.forEach {
          detailsCache.put(GitLabMergeRequestId.Simple(it), it)
        }
      }
    }
  }
}