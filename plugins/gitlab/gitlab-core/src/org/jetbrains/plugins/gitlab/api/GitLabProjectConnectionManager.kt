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
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectDTO
import org.jetbrains.plugins.gitlab.api.request.findProject
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.api.request.getProject
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath

@Service(Service.Level.PROJECT)
internal class GitLabProjectConnectionManager(project: Project, cs: CoroutineScope) :
  SingleHostedGitRepositoryConnectionManager<GitLabProjectMapping, GitLabAccount, GitLabProjectConnection> {

  private val accountManager = service<GitLabAccountManager>()
  private val projectsManager = project.service<GitLabProjectsManager>()

  private val connectionFactory = ValidatingHostedGitRepositoryConnectionFactory(
    { projectsManager },
    { accountManager }
  ) { glProjectMapping, account, tokenState ->
    val scope = this
    val apiClient = service<GitLabApiManager>().getClient(account.server) { tokenState.value }
    val glMetadata = apiClient.getMetadataOrNull()
    val currentUser = apiClient.graphQL.getCurrentUser()

    var projectCoordinates: GitLabProjectCoordinates = glProjectMapping.repository
    val projectData: GitLabProjectDTO
    val originalProject = apiClient.graphQL.findProject(projectCoordinates.projectPath).body()
    if (originalProject != null) {
      projectCoordinates = glProjectMapping.repository
      projectData = originalProject
    }
    else {
      val restProjectResponse = apiClient.rest.getProject(projectCoordinates.projectPath).body()
      val actualProjectPath = GitLabProjectPath.extractProjectPath(restProjectResponse.pathWithNamespace)
                              ?: error("Unable to parse the project path: ${restProjectResponse.pathWithNamespace}")
      projectCoordinates = projectCoordinates.copy(projectPath = actualProjectPath)
      projectData = apiClient.graphQL.findProject(actualProjectPath).body()
                    ?: error("Could not find the project $actualProjectPath. Check if the project exists and you have access to it.")
    }

    GitLabProjectConnection(project,
                            scope,
                            glProjectMapping,
                            projectCoordinates,
                            projectData,
                            account,
                            currentUser,
                            apiClient,
                            glMetadata,
                            tokenState)
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

  override suspend fun openConnection(repo: GitLabProjectMapping, account: GitLabAccount): GitLabProjectConnection? =
    delegate.openConnection(repo, account)

  override suspend fun closeConnection() = delegate.closeConnection()
}