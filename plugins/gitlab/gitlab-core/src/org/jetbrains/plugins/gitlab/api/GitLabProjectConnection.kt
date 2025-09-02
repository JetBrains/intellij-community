// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.openapi.project.Project
import git4idea.remote.hosting.HostedGitRepositoryConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLazyProject
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.util.*

class GitLabProjectConnection(
  project: Project,
  private val scope: CoroutineScope,
  override val repo: GitLabProjectMapping,
  override val account: GitLabAccount,
  val currentUser: GitLabUserDTO,
  apiClient: GitLabApi,
  glMetadata: GitLabServerMetadata?,
  tokenState: Flow<String>
) : HostedGitRepositoryConnection<GitLabProjectMapping, GitLabAccount> {
  val id: String = UUID.randomUUID().toString()

  val tokenRefreshFlow: Flow<Unit> = tokenState.drop(1).map { }

  val projectData = GitLabLazyProject(project, scope, apiClient, glMetadata, repo, currentUser, tokenRefreshFlow)
  val imageLoader = GitLabImageLoader(apiClient, repo.repository.serverPath)

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