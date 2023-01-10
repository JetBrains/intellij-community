// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManager
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManagerImpl
import git4idea.remote.hosting.ValidatingHostedGitRepositoryConnectionFactory
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

typealias GitLabProjectConnectionManager = SingleHostedGitRepositoryConnectionManager<GitLabProjectMapping, GitLabAccount, GitLabProjectConnection>

internal fun GitLabProjectConnectionManager(scope: CoroutineScope,
                                            repositoriesManager: GitLabProjectsManager,
                                            accountManager: GitLabAccountManager)
  : GitLabProjectConnectionManager {
  val connectionFactory = ValidatingHostedGitRepositoryConnectionFactory(repositoriesManager,
                                                                         accountManager) { project, account, tokenState ->
    val apiClient = GitLabApi { tokenState.value }
    val currentUser = apiClient.getCurrentUser(project.repository.serverPath) ?: error("Unable to load current user")
    GitLabProjectConnection(this, project, account, currentUser, apiClient, tokenState)
  }

  return SingleHostedGitRepositoryConnectionManagerImpl(scope, connectionFactory)
}
