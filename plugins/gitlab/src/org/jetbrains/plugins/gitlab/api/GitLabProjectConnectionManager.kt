// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.async.disposingScope
import com.intellij.collaboration.util.serviceGet
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManager
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManagerImpl
import git4idea.remote.hosting.ValidatingHostedGitRepositoryConnectionFactory
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

@Service
internal class GitLabProjectConnectionManager(project: Project) :
  SingleHostedGitRepositoryConnectionManager<GitLabProjectMapping, GitLabAccount, GitLabProjectConnection>,
  Disposable {

  private val connectionFactory = ValidatingHostedGitRepositoryConnectionFactory(project.serviceGet<GitLabProjectsManager>(),
                                                                                 serviceGet<GitLabAccountManager>()) { glProject, account, tokenState ->
    val apiClient = GitLabApiImpl { tokenState.value }
    val currentUser = apiClient.getCurrentUser(glProject.repository.serverPath) ?: error("Unable to load current user")
    GitLabProjectConnection(project, this, glProject, account, currentUser, apiClient, tokenState)
  }

  private val delegate = SingleHostedGitRepositoryConnectionManagerImpl(disposingScope(), connectionFactory)

  override val connectionState: StateFlow<GitLabProjectConnection?>
    get() = delegate.connectionState

  override suspend fun openConnection(repo: GitLabProjectMapping, account: GitLabAccount): GitLabProjectConnection? =
    delegate.openConnection(repo, account)

  override suspend fun closeConnection() = delegate.closeConnection()

  override fun dispose() = Unit
}