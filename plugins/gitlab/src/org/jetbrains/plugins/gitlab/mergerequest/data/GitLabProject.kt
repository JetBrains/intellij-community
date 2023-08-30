// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.async.asResultFlow
import com.intellij.collaboration.async.collectBatches
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.request.createAllProjectLabelsFlow
import org.jetbrains.plugins.gitlab.api.request.getProjectUsers
import org.jetbrains.plugins.gitlab.api.request.getProjectUsersURI
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

private val LOG = logger<GitLabProject>()

interface GitLabProject {
  val projectMapping: GitLabProjectMapping

  val mergeRequests: GitLabProjectMergeRequestsStore

  val labels: Flow<Result<List<GitLabLabelDTO>>>
  val members: Flow<Result<List<GitLabUserDTO>>>
}

class GitLabLazyProject(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  override val projectMapping: GitLabProjectMapping,
  private val tokenRefreshFlow: Flow<Unit>
) : GitLabProject {

  private val cs = parentCs.childScope()

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
}