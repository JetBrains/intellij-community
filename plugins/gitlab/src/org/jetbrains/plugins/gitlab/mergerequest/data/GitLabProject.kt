// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.request.getProjectUsers
import org.jetbrains.plugins.gitlab.api.request.getProjectUsersURI
import org.jetbrains.plugins.gitlab.api.request.loadAllProjectLabels
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

interface GitLabProject {
  val projectMapping: GitLabProjectMapping

  val mergeRequests: GitLabProjectMergeRequestsStore

  suspend fun getLabels(): List<GitLabLabelDTO>
  suspend fun getMembers(): List<GitLabUserDTO>
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

  private val allLabels = cs.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    api.graphQL.loadAllProjectLabels(projectMapping.repository)
  }

  private val allMembers = cs.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    ApiPageUtil.createPagesFlowByLinkHeader(getProjectUsersURI(projectMapping.repository)) {
      api.rest.getProjectUsers(projectMapping.repository.serverPath, it)
    }.map { it.body() }.foldToList(GitLabUserDTO::fromRestDTO)
  }

  override suspend fun getLabels(): List<GitLabLabelDTO> = allLabels.await()

  override suspend fun getMembers(): List<GitLabUserDTO> = allMembers.await()
}