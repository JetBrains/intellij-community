// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.async.ReloadablePotentiallyInfiniteListLoader
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.loadUpdatableJsonList
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestByBranchDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.findMergeRequestsByBranch
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestListURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.startGitLabRestETagListLoaderIn
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.util.concurrent.ConcurrentHashMap

interface GitLabProjectMergeRequestsStore {

  fun getListLoaderIn(cs: CoroutineScope, searchQuery: String): ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO>

  /**
   * @return a handle for result of loading a shared MR model
   */
  fun getShared(iid: String): SharedFlow<Result<GitLabMergeRequest>>

  /**
   * @return cached short MR details
   */
  fun findCachedDetails(iid: String): GitLabMergeRequestDetails?

  /**
   * Update shared merge request
   */
  suspend fun reloadMergeRequest(iid: String)

  /**
   * Find merge requests in specified [state] on a remote
   * with a source branch name [sourceBranchName] and a target branch name [targetBranchName]
   */
  suspend fun findByBranches(state: GitLabMergeRequestState,
                             sourceBranchName: String,
                             targetBranchName: String? = null): List<GitLabMergeRequestByBranchDTO>
}

class CachingGitLabProjectMergeRequestsStore(private val project: Project,
                                             parentCs: CoroutineScope,
                                             private val api: GitLabApi,
                                             private val glMetadata: GitLabServerMetadata?,
                                             private val projectMapping: GitLabProjectMapping,
                                             private val currentUser: GitLabUserDTO,
                                             private val tokenRefreshFlow: Flow<Unit>) : GitLabProjectMergeRequestsStore {

  private val cs = parentCs.childScope()

  private val glProject: GitLabProjectCoordinates = projectMapping.repository

  private val detailsCache = Caffeine.newBuilder()
    .weakValues()
    .build<String, GitLabMergeRequestDetails>()

  private val models = ConcurrentHashMap<String, SharedFlow<Result<GitLabMergeRequest>>>()

  private val reloadMergeRequest: MutableSharedFlow<String> = MutableSharedFlow(1)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getListLoaderIn(cs: CoroutineScope, searchQuery: String): ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO> {
    val loader = startGitLabRestETagListLoaderIn(
      cs,
      getMergeRequestListURI(glProject, searchQuery),
      { it.id },

      requestReloadFlow = tokenRefreshFlow.withInitial(Unit)
    ) { uri, etag ->
      api.rest.loadUpdatableJsonList<GitLabMergeRequestShortRestDTO>(
        GitLabApiRequestName.REST_GET_MERGE_REQUESTS, uri, etag
      )
    }

    // Keep the first N merge requests always hot and loaded
    cs.launch {
      loader.stateFlow.mapLatest { it.list?.take(Registry.intValue("gitlab.merge.requests.cached.from.list")) }.filterNotNull()
        .distinctUntilChanged()
        .collectLatest { mrs ->
          coroutineScope {
            mrs.forEach { mr ->
              launch {
                getShared(mr.iid).collect()
              }
            }
          }
        }
    }

    return loader
  }

  init {
    cs.launch {
      tokenRefreshFlow.collect {
        models.keys.forEach { mrId -> reloadMergeRequest(mrId) }
      }
    }
  }

  override fun getShared(iid: String): SharedFlow<Result<GitLabMergeRequest>> {
    return models.getOrPut(iid) {
      reloadMergeRequest
        .filter { requestedId -> requestedId == iid }
        .withInitial(iid)
        .map { mrId -> runCatchingUser { loadMergeRequest(mrId) } } // TODO: create from cached details
        .transformConsecutiveSuccesses {
          mapScoped { mrData -> LoadedGitLabMergeRequest(project, this, api, glMetadata, projectMapping, currentUser, mrData) }
        }
        .shareIn(cs, SharingStarted.WhileSubscribed(0, 1000), 1)
      // this the model will only be alive while it's needed
    }
  }

  override suspend fun findByBranches(state: GitLabMergeRequestState,
                                      sourceBranchName: String,
                                      targetBranchName: String?): List<GitLabMergeRequestByBranchDTO> =
    withContext(Dispatchers.IO) {
      val body = api.graphQL.findMergeRequestsByBranch(projectMapping.repository, state, sourceBranchName, targetBranchName).body()
      body!!.nodes
    }

  override fun findCachedDetails(iid: String): GitLabMergeRequestDetails? = detailsCache.getIfPresent(iid)

  override suspend fun reloadMergeRequest(iid: String) {
    reloadMergeRequest.emit(iid)
  }

  @Throws(HttpStatusErrorException::class, IllegalStateException::class)
  private suspend fun loadMergeRequest(iid: String): GitLabMergeRequestDTO {
    return withContext(Dispatchers.IO) {
      val body = api.graphQL.loadMergeRequest(glProject, iid).body()
      if (body == null) {
        api.rest.getCurrentUser() // Exception is generated automatically if status code >= 400
        error(CollaborationToolsBundle.message("graphql.errors", "empty response"))
      }
      body
    }
  }
}