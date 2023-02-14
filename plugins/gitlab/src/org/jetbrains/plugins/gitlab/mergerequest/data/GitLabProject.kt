// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.request.getAllProjectMembers
import org.jetbrains.plugins.gitlab.api.request.loadAllProjectLabels
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

interface GitLabProject {
  val projectMapping: GitLabProjectMapping

  val mergeRequests: GitLabProjectMergeRequestsStore

  suspend fun getLabels(): List<GitLabLabelDTO>
  suspend fun getMembers(): List<GitLabMemberDTO>
}

class GitLabLazyProject(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  override val projectMapping: GitLabProjectMapping
) : GitLabProject {

  private val cs = parentCs.childScope()

  override val mergeRequests by lazy {
    CachingGitLabProjectMergeRequestsStore(project, cs, api, projectMapping)
  }

  override suspend fun getLabels(): List<GitLabLabelDTO> =
    withContext(cs.coroutineContext) {
      api.loadAllProjectLabels(projectMapping.repository)
    }

  override suspend fun getMembers(): List<GitLabMemberDTO> =
    withContext(cs.coroutineContext) {
      api.getAllProjectMembers(projectMapping.repository)
    }
}