// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.async.asResultFlow
import com.intellij.collaboration.async.collectBatches
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.*
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

  /**
   * Creates a merge request on the GitLab server and returns a DTO containing the merge request
   * once the merge request was successfully initialized on server.
   * The reason for this wait is that GitLab might take a few moments to process the merge request
   * before returning one that can be displayed in the IDE in a useful way.
   */
  suspend fun createMergeRequestAndAwaitCompletion(sourceBranch: String, targetBranch: String, title: String): GitLabMergeRequestDTO
  suspend fun adjustReviewers(mrIid: String, reviewers: List<GitLabUserDTO>): GitLabMergeRequestDTO
}

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

  override val mergeRequests by lazy {
    CachingGitLabProjectMergeRequestsStore(project, cs, api, glMetadata, projectMapping, tokenRefreshFlow)
  }

  override val labels: Flow<Result<List<GitLabLabelDTO>>> =
    api.graphQL.createAllProjectLabelsFlow(projectMapping.repository)
      .collectBatches()
      .asResultFlow()
      .modelFlow(parentCs, LOG)

  override val members: Flow<Result<List<GitLabUserDTO>>> =
    ApiPageUtil.createPagesFlowByLinkHeader(getProjectUsersURI(projectMapping.repository)) {
      api.rest.getProjectUsers(it)
    }
      .map { it.body().map(GitLabUserDTO::fromRestDTO) }
      .collectBatches()
      .asResultFlow()
      .modelFlow(parentCs, LOG)

  override val defaultBranch: Deferred<String> = cs.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    val projectRepository = api.graphQL.getProjectRepository(projectCoordinates).body()
    projectRepository.rootRef
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
}