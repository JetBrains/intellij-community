// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.notification

import com.intellij.collaboration.auth.findAccountOrNull
import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.GitRemoteBranch
import git4idea.push.GitPushNotificationCustomizer
import git4idea.push.GitPushNotificationUtil
import git4idea.push.GitPushRepoResult
import git4idea.push.findRemoteBranch
import git4idea.push.isSuccessful
import git4idea.remote.hosting.knownRepositories
import git4idea.repo.GitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
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
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath

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

    if (connection != null) {
      if (connection.repo.gitRepository != repository || connection.repo.gitRemote != remoteBranch.remote) {
        return emptyList()
      }
      return createActionList(connection.account, connection.projectData.projectCoordinates.projectPath, remoteBranch, connection.repo)
    }

    val accountManager = serviceAsync<GitLabAccountManager>()
    val savedAccount = project.serviceAsync<GitLabMergeRequestsPreferences>().selectedUrlAndAccountId?.second?.let { savedId ->
      accountManager.findAccountOrNull { it.id == savedId }
    }
    val (projectMapping, account) = GitPushNotificationUtil.findRepositoryAndAccount(
      project.serviceAsync<GitLabProjectsManager>().knownRepositories,
      repository, remoteBranch.remote,
      accountManager.accountsState.value,
      savedAccount,
      project.serviceAsync<GitLabProjectDefaultAccountHolder>().account
    ) ?: return emptyList()
    // be aware, for a renamed project no action will be returned, because of the incorrect path
    val projectFullPath = projectMapping.repository.projectPath
    return createActionList(account, projectFullPath, remoteBranch, projectMapping)
  }

  private suspend fun createActionList(
    account: GitLabAccount,
    projectFullPath: GitLabProjectPath,
    remoteBranch: GitRemoteBranch,
    projectMapping: GitLabProjectMapping,
  ): List<AnAction> {
    val accountManager = serviceAsync<GitLabAccountManager>()
    val token = accountManager.findCredentials(account) ?: return emptyList()
    val api = serviceAsync<GitLabApiManager>().getClient(account.server, token)

    if (!canCreateReview(api, projectFullPath, remoteBranch)) {
      return emptyList()
    }

    val existingMRs = findExistingMergeRequests(api, projectFullPath, remoteBranch)
    return actions(existingMRs, projectMapping, account)
  }

  private fun actions(
    existingMRs: List<GitLabMergeRequestByBranchDTO>,
    projectMapping: GitLabProjectMapping,
    account: GitLabAccount,
  ): List<AnAction> {
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
  private suspend fun canCreateReview(
    api: GitLabApi,
    projectFullPath: GitLabProjectPath,
    branch: GitRemoteBranch,
  ): Boolean {
    val repositoryInfo = getRepositoryInfo(api, projectFullPath) ?: run {
      LOG.warn("Repository not found: $projectFullPath")
      return false
    }

    // Don't allow creating MRs targeting the default branch
    val remoteBranchName = branch.nameForRemoteOperations
    return repositoryInfo.rootRef != remoteBranchName
  }

  private suspend fun getRepositoryInfo(api: GitLabApi, projectFullPath: GitLabProjectPath) =
    try {
      api.graphQL.findProject(projectFullPath).body()?.repository
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.warn("Failed to lookup repository $projectFullPath in $project", e)
      null
    }

  /**
   * Look up any existing open merge requests on the given remote branch.
   */
  private suspend fun findExistingMergeRequests(
    api: GitLabApi,
    targetProjectPath: GitLabProjectPath,
    branch: GitRemoteBranch,
  ): List<GitLabMergeRequestByBranchDTO> {
    val remoteBranchName = branch.nameForRemoteOperations

    return withContext(Dispatchers.IO) {
      try {
        val mrs = api.graphQL.findMergeRequestsByBranch(targetProjectPath, GitLabMergeRequestState.OPENED, remoteBranchName).body()!!.nodes
        mrs.filter { it.targetProject.fullPath == targetProjectPath.fullPath() && it.sourceProject?.fullPath == targetProjectPath.fullPath() }
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        LOG.warn("Failed to lookup existing merge requests for branch $remoteBranchName in $targetProjectPath", e)
        emptyList()
      }
    }
  }
}