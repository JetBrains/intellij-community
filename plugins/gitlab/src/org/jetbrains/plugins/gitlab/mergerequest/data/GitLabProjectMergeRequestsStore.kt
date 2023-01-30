// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabMergeRequestsListLoader
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
}

class CachingGitLabProjectMergeRequestsStore(parentCs: CoroutineScope,
                                             private val api: GitLabApi,
                                             private val project: GitLabProjectCoordinates) : GitLabProjectMergeRequestsStore {

  private val cs = parentCs.childScope()

  private val detailsCache = Caffeine.newBuilder()
    .weakValues()
    .build<GitLabMergeRequestId, GitLabMergeRequestDetails>()

  private val models = ConcurrentHashMap<GitLabMergeRequestId, SharedFlow<Result<GitLabMergeRequest>>>()

  override fun getListLoader(searchQuery: String): SequentialListLoader<GitLabMergeRequestDetails> = CachingListLoader(searchQuery)

  override fun getShared(id: GitLabMergeRequestId): SharedFlow<Result<GitLabMergeRequest>> {
    val simpleId = GitLabMergeRequestId.Simple(id)
    return models.getOrPut(simpleId) {
      channelFlow {
        val result = runCatching {
          // TODO: create from cached details
          val mrData = withContext(Dispatchers.IO) {
            api.loadMergeRequest(project, id).body()!!
          }
          LoadedGitLabMergeRequest(this, api, project, mrData)
        }
        send(result)
        awaitClose()
      }.onCompletion { models.remove(simpleId) }
        .shareIn(cs, SharingStarted.WhileSubscribed(0, 0), 1)
      // this the model will only be alive while it's needed
    }
  }

  override fun findCachedDetails(id: GitLabMergeRequestId): GitLabMergeRequestDetails? = detailsCache.getIfPresent(id)

  private inner class CachingListLoader(searchQuery: String)
    : SequentialListLoader<GitLabMergeRequestDetails> {
    private val actualLoader = GitLabMergeRequestsListLoader(api, project, searchQuery)

    override suspend fun loadNext(): SequentialListLoader.ListBatch<GitLabMergeRequestDetails> {
      return actualLoader.loadNext().also { (data, _) ->
        data.forEach {
          detailsCache.put(GitLabMergeRequestId.Simple(it), it)
        }
      }
    }
  }
}