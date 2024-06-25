// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.notification

import com.intellij.collaboration.auth.findAccountOrNull
import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.GitRemoteBranch
import git4idea.push.*
import git4idea.remote.hosting.knownRepositories
import git4idea.repo.GitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.request.findProject
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestByBranchDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.findMergeRequestsByBranch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.action.GitLabMergeRequestOpenCreateTabNotificationAction
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

private val LOG = logger<GitLabPushNotificationCustomizer>()

internal class GitLabPushNotificationCustomizer(private val project: Project) : GitPushNotificationCustomizer {
  override suspend fun getActions(
    repository: GitRepository,
    pushResult: GitPushRepoResult,
    customParams: Map<String, VcsPushOptionValue>
  ): List<AnAction> {
    if (!pushResult.isSuccessful) return emptyList()
    val remoteBranch = pushResult.findRemoteBranch(repository) ?: return emptyList()

    val connection = project.serviceAsync<GitLabProjectConnectionManager>().connectionState.value
    if (connection != null && (connection.repo.gitRepository != repository || connection.repo.gitRemote != remoteBranch.remote)) {
      return emptyList()
    }

    val (projectMapping, account) = connection?.let {
      it.repo to it.account
    } ?: run {
      val accountManager = serviceAsync<GitLabAccountManager>()
      val savedAccount = project.serviceAsync<GitLabMergeRequestsPreferences>().selectedUrlAndAccountId?.second?.let { savedId ->
        accountManager.findAccountOrNull { it.id == savedId }
      }
      GitPushNotificationUtil.findRepositoryAndAccount(
        project.serviceAsync<GitLabProjectsManager>().knownRepositories,
        repository, remoteBranch.remote,
        accountManager.accountsState.value,
        savedAccount,
        project.serviceAsync<GitLabProjectDefaultAccountHolder>().account
      )
    } ?: return emptyList()

    val canCreate = canCreateReview(projectMapping, account, remoteBranch)
    if (!canCreate) return emptyList()

    return listOf(GitLabMergeRequestOpenCreateTabNotificationAction(project, projectMapping, account))
  }

  private suspend fun canCreateReview(projectMapping: GitLabProjectMapping, account: GitLabAccount, branch: GitRemoteBranch): Boolean {
    val accountManager = serviceAsync<GitLabAccountManager>()
    val token = accountManager.findCredentials(account) ?: return false
    val api = serviceAsync<GitLabApiManager>().getClient(account.server, token)

    val repository = projectMapping.repository
    val repositoryInfo = getRepositoryInfo(api, repository) ?: run {
      LOG.warn("Repository not found $repository")
      return false
    }

    val remoteBranchName = branch.nameForRemoteOperations
    if (repositoryInfo.rootRef == remoteBranchName) {
      return false
    }

    if (findExistingMergeRequests(api, repository, remoteBranchName).isNotEmpty()) {
      return false
    }

    return true
  }

  private suspend fun getRepositoryInfo(api: GitLabApi, project: GitLabProjectCoordinates) =
    try {
      api.graphQL.findProject(project).body()?.repository
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.warn("Failed to lookup a repository $project", e)
      null
    }

  private suspend fun findExistingMergeRequests(
    api: GitLabApi,
    project: GitLabProjectCoordinates,
    remoteBranchName: String,
  ): List<GitLabMergeRequestByBranchDTO> =
    withContext(Dispatchers.IO) {
      val targetProjectPath = project.projectPath.fullPath()
      try {
        val mrs = api.graphQL.findMergeRequestsByBranch(project, GitLabMergeRequestState.OPENED, remoteBranchName).body()!!.nodes
        mrs.filter { it.targetProject.fullPath == targetProjectPath && it.sourceProject?.fullPath == targetProjectPath }
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        LOG.warn("Failed to lookup an existing merge request for $remoteBranchName in $project", e)
        emptyList()
    }
  }
}