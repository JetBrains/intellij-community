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
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGraphQLMutationException
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

private val LOG = logger<GitLabProject>()

interface GitLabProject {
  val projectMapping: GitLabProjectMapping

  val mergeRequests: GitLabProjectMergeRequestsStore

  val labels: Flow<Result<List<GitLabLabelDTO>>>
  val members: Flow<Result<List<GitLabUserDTO>>>
  val defaultBranch: Deferred<String>

  suspend fun createMergeRequest(sourceBranch: String, targetBranch: String, title: String): GitLabMergeRequestDTO
}

class GitLabLazyProject(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  override val projectMapping: GitLabProjectMapping,
  private val tokenRefreshFlow: Flow<Unit>
) : GitLabProject {

  private val cs = parentCs.childScope()

  private val projectCoordinates: GitLabProjectCoordinates = projectMapping.repository

  override val mergeRequests by lazy {
    CachingGitLabProjectMergeRequestsStore(project, cs, api, projectMapping, tokenRefreshFlow)
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
  override suspend fun createMergeRequest(sourceBranch: String, targetBranch: String, title: String): GitLabMergeRequestDTO {
    return withContext(cs.coroutineContext + Dispatchers.IO) {
      api.graphQL.createMergeRequest(projectCoordinates, sourceBranch, targetBranch, title).getResultOrThrow()
    }
  }
}