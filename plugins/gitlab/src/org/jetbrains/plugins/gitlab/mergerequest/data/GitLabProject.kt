// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.data.GitLabPlan
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestSetReviewers
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabRegistry

private val LOG = logger<GitLabProject>()

interface GitLabProject {
  val projectMapping: GitLabProjectMapping

  val mergeRequests: GitLabProjectMergeRequestsStore

  val labels: Flow<Result<List<GitLabLabelDTO>>>
  val members: Flow<Result<List<GitLabUserDTO>>>
  val defaultBranch: Deferred<String>
  val plan: Deferred<GitLabPlan>

  /**
   * Creates a merge request on the GitLab server and returns a DTO containing the merge request
   * once the merge request was successfully initialized on server.
   * The reason for this wait is that GitLab might take a few moments to process the merge request
   * before returning one that can be displayed in the IDE in a useful way.
   */
  suspend fun createMergeRequestAndAwaitCompletion(sourceBranch: String, targetBranch: String, title: String): GitLabMergeRequestDTO
  suspend fun adjustReviewers(mrIid: String, reviewers: List<GitLabUserDTO>): GitLabMergeRequestDTO

  fun reloadData()
}

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabLazyProject(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val glMetadata: GitLabServerMetadata?,
  override val projectMapping: GitLabProjectMapping,
  private val tokenRefreshFlow: Flow<Unit>
) : GitLabProject {

  private val cs = parentCs.childScope()

  private val projectCoordinates: GitLabProjectCoordinates = projectMapping.repository

  private val projectDataReloadSignal = MutableSharedFlow<Unit>()

  override val mergeRequests by lazy {
    CachingGitLabProjectMergeRequestsStore(project, cs, api, glMetadata, projectMapping, tokenRefreshFlow)
  }

  override val labels: Flow<Result<List<GitLabLabelDTO>>> = projectDataReloadSignal.withInitial(Unit).flatMapLatest {
    loadLabels()
  }.modelFlow(parentCs, LOG)

  override val members: SharedFlow<Result<List<GitLabUserDTO>>> = projectDataReloadSignal.withInitial(Unit).flatMapLatest {
    loadMembers()
  }.modelFlow(parentCs, LOG)

  override val defaultBranch: Deferred<String> = cs.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    val projectRepository = api.graphQL.getProjectRepository(projectCoordinates).body()
    projectRepository.rootRef
  }

  override val plan: Deferred<GitLabPlan> = cs.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    val namespace = api.rest.getProjectNamespace(projectMapping.repository.projectPath.owner).body()
    namespace.plan
  }

  @Throws(GitLabGraphQLMutationException::class)
  override suspend fun createMergeRequestAndAwaitCompletion(sourceBranch: String, targetBranch: String, title: String): GitLabMergeRequestDTO {
    return withContext(cs.coroutineContext + Dispatchers.IO) {
      var data: GitLabMergeRequestDTO = api.graphQL.createMergeRequest(projectCoordinates, sourceBranch, targetBranch, title).getResultOrThrow()
      val iid = data.iid
      var attempts = 1
      while (attempts++ < GitLabRegistry.getRequestPollingAttempts()) {
        val updatedMr = api.graphQL.loadMergeRequest(projectCoordinates, iid).body()
        requireNotNull(updatedMr)

        data = updatedMr

        if (data.diffRefs != null) break

        delay(GitLabRegistry.getRequestPollingIntervalMillis().toLong())
      }
      data
    }
  }

  @Throws(GitLabGraphQLMutationException::class, IllegalStateException::class)
  override suspend fun adjustReviewers(mrIid: String, reviewers: List<GitLabUserDTO>): GitLabMergeRequestDTO {
    return withContext(cs.coroutineContext + Dispatchers.IO) {
      if (GitLabVersion(15, 3) <= api.getMetadata().version) {
        api.graphQL.mergeRequestSetReviewers(projectCoordinates, mrIid, reviewers).getResultOrThrow()
      }
      else {
        api.rest.mergeRequestSetReviewers(projectCoordinates, mrIid, reviewers).body()
        api.graphQL.loadMergeRequest(projectCoordinates, mrIid).body() ?: error("Merge request could not be loaded")
      }
    }
  }

  override fun reloadData() {
    cs.launch {
      projectDataReloadSignal.emit(Unit)
    }
  }

  private fun loadLabels(): Flow<Result<List<GitLabLabelDTO>>> = channelFlow<Result<List<GitLabLabelDTO>>> {
    runCatchingUser {
      val loadedLabels = mutableListOf<GitLabLabelDTO>()
      api.graphQL.createAllProjectLabelsFlow(projectMapping.repository)
        .collect { labelDTOs ->
          loadedLabels.addAll(labelDTOs)
          send(Result.success(loadedLabels))
        }
    }.onFailure { e -> send(Result.failure(e)) }
  }

  private fun loadMembers(): Flow<Result<List<GitLabUserDTO>>> = channelFlow<Result<List<GitLabUserDTO>>> {
    runCatchingUser {
      val loadedMembers = mutableListOf<GitLabUserDTO>()
      ApiPageUtil.createPagesFlowByLinkHeader(getProjectUsersURI(projectMapping.repository)) { api.rest.getProjectUsers(it) }
        .map { response -> response.body().map(GitLabUserDTO::fromRestDTO) }
        .collect { userDTOs ->
          loadedMembers.addAll(userDTOs)
          send(Result.success(loadedMembers))
        }
    }.onFailure { e -> send(Result.failure(e)) }
  }
}