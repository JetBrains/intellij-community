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
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.action.GitLabOpenMergeRequestExistingTabNotificationAction
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

private val LOG = logger<GitLabPushNotificationCustomizer>()

internal class GitLabPushNotificationCustomizer(private val project: Project) : GitPushNotificationCustomizer {
  override suspend fun getActions(
    repository: GitRepository,
    pushResult: GitPushRepoResult,
    customParams: Map<String, VcsPushOptionValue>,
  ): List<AnAction> {
    if (!pushResult.isSuccessful) return emptyList()
    val remoteBranch = pushResult.findRemoteBranch(repository) ?: return emptyList()

    // If we already have a GitLab connection open, make sure it matches
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

    if (!canCreateReview(projectMapping, account, remoteBranch)) {
      return emptyList()
    }

    val existingMRs = findExistingMergeRequests(projectMapping, account, remoteBranch)
    return when (existingMRs.size) {
      0 -> {
        listOf(GitLabMergeRequestOpenCreateTabNotificationAction(project, projectMapping, account))
      }
      1 -> {
        val singleMR = existingMRs.first()
        listOf(GitLabOpenMergeRequestExistingTabNotificationAction(project, projectMapping, account, singleMR.iid))
      }
      else -> {
        emptyList()
      }
    }
  }

  /**
   * Checks if it's even valid to create a merge request.
   * For instance:
   * - The repository must exist
   * - The branch cannot be the default branch (we don't allow creating an MR from default -> default)
   */
  private suspend fun canCreateReview(projectMapping: GitLabProjectMapping, account: GitLabAccount, branch: GitRemoteBranch): Boolean {
    val accountManager = serviceAsync<GitLabAccountManager>()
    val token = accountManager.findCredentials(account) ?: return false
    val api = serviceAsync<GitLabApiManager>().getClient(account.server, token)

    val repository = projectMapping.repository
    val repositoryInfo = getRepositoryInfo(api, repository) ?: run {
      LOG.warn("Repository not found: $repository")
      return false
    }

    // Don't allow creating MRs targeting the default branch
    val remoteBranchName = branch.nameForRemoteOperations
    return repositoryInfo.rootRef != remoteBranchName
  }

  private suspend fun getRepositoryInfo(api: GitLabApi, project: GitLabProjectCoordinates) =
    try {
      api.graphQL.findProject(project).body()?.repository
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.warn("Failed to lookup repository $project", e)
      null
    }

  /**
   * Look up any existing open merge requests on the given remote branch.
   */
  private suspend fun findExistingMergeRequests(
    projectMapping: GitLabProjectMapping,
    account: GitLabAccount,
    branch: GitRemoteBranch,
  ): List<GitLabMergeRequestByBranchDTO> {
    val accountManager = serviceAsync<GitLabAccountManager>()
    val token = accountManager.findCredentials(account) ?: return emptyList()
    val api = serviceAsync<GitLabApiManager>().getClient(account.server, token)

    val repository = projectMapping.repository
    val remoteBranchName = branch.nameForRemoteOperations

    return withContext(Dispatchers.IO) {
      val targetProjectPath = repository.projectPath.fullPath()
      try {
        val mrs = api.graphQL.findMergeRequestsByBranch(repository, GitLabMergeRequestState.OPENED, remoteBranchName).body()!!.nodes
        mrs.filter { it.targetProject.fullPath == targetProjectPath && it.sourceProject?.fullPath == targetProjectPath }
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        LOG.warn("Failed to lookup existing merge requests for branch $remoteBranchName in $repository", e)
        emptyList()
      }
    }
  }
}