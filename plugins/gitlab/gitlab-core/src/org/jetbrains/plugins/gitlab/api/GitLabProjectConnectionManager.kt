// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManager
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManagerImpl
import git4idea.remote.hosting.ValidatingHostedGitRepositoryConnectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

@Service(Service.Level.PROJECT)
internal class GitLabProjectConnectionManager(private val project: Project, cs: CoroutineScope) :
  SingleHostedGitRepositoryConnectionManager<GitLabProjectMapping, GitLabAccount, GitLabProjectConnection> {

  private val accountManager = service<GitLabAccountManager>()
  private val projectsManager = project.service<GitLabProjectsManager>()

  private val connectionFactory = ValidatingHostedGitRepositoryConnectionFactory(
    { projectsManager },
    { accountManager }
  ) { glProjectMapping, account, _ ->
    doOpenConnectionIn(this, glProjectMapping, account)
  }

  private val delegate = SingleHostedGitRepositoryConnectionManagerImpl(cs, connectionFactory)

  override val connectionState: StateFlow<GitLabProjectConnection?>
    get() = delegate.connectionState

  init {
    cs.launch {
      accountManager.accountsState.collect {
        val currentAccount = connectionState.value?.account
        if (currentAccount != null && !it.contains(currentAccount)) {
          closeConnection()
        }
      }
    }
  }

  private suspend fun doOpenConnectionIn(
    scope: CoroutineScope,
    glProjectMapping: GitLabProjectMapping,
    account: GitLabAccount,
  ): GitLabProjectConnection {
    val apiClient = service<GitLabApiManager>().getClient(account)
    val glMetadata = apiClient.getMetadataOrNull()
    val currentUser = apiClient.graphQL.getCurrentUser()
    val projectDetails = apiClient.loadProjectDetails(glMetadata, glProjectMapping.repository.projectPath)
    return GitLabProjectConnection(project,
                                   scope,
                                   glProjectMapping,
                                   projectDetails,
                                   account,
                                   currentUser,
                                   apiClient,
                                   glMetadata)
  }

  override suspend fun openConnection(repo: GitLabProjectMapping, account: GitLabAccount): GitLabProjectConnection? =
    delegate.openConnection(repo, account)

  override suspend fun closeConnection() = delegate.closeConnection()
}