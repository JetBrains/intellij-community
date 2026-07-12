// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.openapi.project.Project
import git4idea.remote.hosting.HostedGitRepositoryConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.data.GitLabProjectDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProjectImpl
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.util.UUID

/**
 * A low-level helper representing a GitLab project, to which the app was authorized to connect
 */
class GitLabProjectConnection(
  project: Project,
  private val scope: CoroutineScope,
  override val repo: GitLabProjectMapping,
  projectDetails: GitLabProjectDetails,
  override val account: GitLabAccount,
  val currentUser: GitLabUserDTO,
  apiClient: GitLabApi,
  glMetadata: GitLabServerMetadata?,
) : HostedGitRepositoryConnection<GitLabProjectMapping, GitLabAccount> {
  val id: String = UUID.randomUUID().toString()

  val imageLoader: GitLabImageLoader = GitLabImageLoader(apiClient)

  val projectData: GitLabProject = GitLabProjectImpl(project,
                                                     scope,
                                                     apiClient,
                                                     glMetadata,
                                                     projectDetails,
                                                     currentUser,
                                                     // handle the project rename
                                                     repo.repository.copy(projectPath = projectDetails.path),
                                                     repo.remote)

  val serverVersion: GitLabVersion? = glMetadata?.version

  override suspend fun close() {
    try {
      (scope.coroutineContext[Job] ?: error("Missing job")).cancelAndJoin()
    }
    catch (_: Exception) {
    }
  }

  override suspend fun awaitClose() {
    try {
      (scope.coroutineContext[Job] ?: error("Missing job")).join()
    }
    catch (_: Exception) {
    }
  }
}