// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestByBranchDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.findMergeRequestsByBranch
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabMergeRequestsListLoader
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.util.concurrent.ConcurrentHashMap

interface GitLabProjectMergeRequestsStore {

  fun getListLoader(searchQuery: String): SequentialListLoader<GitLabMergeRequestDetails>

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
                                             private val tokenRefreshFlow: Flow<Unit>) : GitLabProjectMergeRequestsStore {

  private val cs = parentCs.childScope()

  private val glProject: GitLabProjectCoordinates = projectMapping.repository

  private val detailsCache = Caffeine.newBuilder()
    .weakValues()
    .build<String, GitLabMergeRequestDetails>()

  private val models = ConcurrentHashMap<String, SharedFlow<Result<GitLabMergeRequest>>>()

  private val reloadMergeRequest: MutableSharedFlow<String> = MutableSharedFlow(1)

  override fun getListLoader(searchQuery: String): SequentialListLoader<GitLabMergeRequestDetails> = CachingListLoader(searchQuery)

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
          mapScoped { mrData -> LoadedGitLabMergeRequest(project, this, api, glMetadata, projectMapping, mrData) }
        }
        .shareIn(cs, SharingStarted.WhileSubscribed(0, 0), 1)
      // this the model will only be alive while it's needed
    }
  }

  override suspend fun findByBranches(state: GitLabMergeRequestState,
                                      sourceBranchName: String,
                                      targetBranchName: String?): List<GitLabMergeRequestByBranchDTO> =
    withContext(Dispatchers.IO) {
      api.graphQL.findMergeRequestsByBranch(projectMapping.repository, state, sourceBranchName, targetBranchName).body()!!.nodes
    }

  override fun findCachedDetails(iid: String): GitLabMergeRequestDetails? = detailsCache.getIfPresent(iid)

  override suspend fun reloadMergeRequest(iid: String) {
    reloadMergeRequest.emit(iid)
  }

  @Throws(HttpStatusErrorException::class, GitLabMergeRequestDataException.EmptySourceProject::class, IllegalStateException::class)
  private suspend fun loadMergeRequest(iid: String): GitLabMergeRequestDTO {
    return withContext(Dispatchers.IO) {
      val body = api.graphQL.loadMergeRequest(glProject, iid).body()
      if (body == null) {
        api.rest.getCurrentUser() // Exception is generated automatically if status code >= 400
        error(CollaborationToolsBundle.message("graphql.errors", "empty response"))
      }
      if (body.sourceProject == null) {
        throw GitLabMergeRequestDataException.EmptySourceProject(GitLabBundle.message("merge.request.source.project.not.found"),
                                                                 body.webUrl)
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
          detailsCache.put(it.iid, it)
        }
      }
    }
  }
}