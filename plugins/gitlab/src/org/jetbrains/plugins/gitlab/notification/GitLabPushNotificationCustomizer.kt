// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.notification

import com.intellij.collaboration.auth.findAccountOrNull
import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import git4idea.push.GitPushNotificationCustomizer
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.request.getProject
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.api.request.findMergeRequestsByBranch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.action.GitLabMergeRequestOpenCreateTabNotificationAction
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

class GitLabPushNotificationCustomizer(private val project: Project) : GitPushNotificationCustomizer {
  private val preferences: GitLabMergeRequestsPreferences = project.service<GitLabMergeRequestsPreferences>()
  private val projectsManager: GitLabProjectsManager = project.service<GitLabProjectsManager>()
  private val defaultAccountHolder: GitLabProjectDefaultAccountHolder = project.service<GitLabProjectDefaultAccountHolder>()
  private val accountManager: GitLabAccountManager = service<GitLabAccountManager>()
  private val apiManager: GitLabApiManager = service<GitLabApiManager>()

  override suspend fun getActions(
    repository: GitRepository,
    pushResult: GitPushRepoResult,
    customParams: Map<String, VcsPushOptionValue>
  ): List<AnAction> {
    val projectData = selectAccount(repository, pushResult) ?: return emptyList()
    val isExist = isReviewExisting(pushResult, projectData) ?: return emptyList()
    if (isExist) return emptyList()

    val connection = project.serviceAsync<GitLabProjectConnectionManager>().connectionState.value
    if (connection == null || (connection.account == projectData.account && connection.repo == projectData.projectMapping)) {
      return listOf(GitLabMergeRequestOpenCreateTabNotificationAction(project, projectData.projectMapping, projectData.account))
    }

    return emptyList()
  }

  private fun selectAccount(targetRepository: GitRepository, pushResult: GitPushRepoResult): ProjectData? {
    val accountFromPreferences = trySelectAccountFromPreferences(targetRepository, pushResult)
    if (accountFromPreferences != null) {
      return accountFromPreferences
    }

    val selectedAccountAndRepository = trySelectSingleAccount(targetRepository, pushResult)
    if (selectedAccountAndRepository != null) {
      return selectedAccountAndRepository
    }

    return null
  }

  /**
   * Try to select an account from preferences with necessary repository mappings
   *
   * @param targetRepository The target Git repository.
   * @param pushResult The result of the Git push operation.
   * @return The selected project data, or null if no account is found or if the target repository does not match the project mapping.
   */
  private fun trySelectAccountFromPreferences(targetRepository: GitRepository, pushResult: GitPushRepoResult): ProjectData? {
    val (projectMapping, account) = preferences.selectedRepoAndAccount ?: return null
    if (targetRepository != projectMapping.gitRepository) return null

    val remote = GitUtil.findRemoteByName(targetRepository, pushResult.targetRemote) ?: return null
    if (remote != projectMapping.gitRemote) return null

    return ProjectData(projectMapping, account)
  }

  /**
   * Try to select an account from an account list with necessary repository mappings
   *
   * @param targetRepository The target Git repository.
   * @param pushResult The result of the Git push operation.
   * @return The ProjectData object if a single account is found, null otherwise.
   */
  private fun trySelectSingleAccount(targetRepository: GitRepository, pushResult: GitPushRepoResult): ProjectData? {
    val targetRemote = GitUtil.findRemoteByName(targetRepository, pushResult.targetRemote)
    val projectMapping = projectsManager.knownRepositoriesState.value.find { mapping: GitLabProjectMapping ->
      mapping.gitRepository == targetRepository && mapping.gitRemote == targetRemote
    } ?: return null

    val defaultAccount = defaultAccountHolder.account
    if (defaultAccount?.server == projectMapping.repository.serverPath) {
      return ProjectData(projectMapping, defaultAccount)
    }

    val account = accountManager.findAccountOrNull { account ->
      account.server == projectMapping.repository.serverPath
    } ?: return null

    return ProjectData(projectMapping, account)
  }

  private suspend fun isReviewExisting(pushResult: GitPushRepoResult, projectData: ProjectData): Boolean? {
    val (projectMapping, account) = projectData
    val token = accountManager.findCredentials(account) ?: return null
    val api = apiManager.getClient(account.server, token)
    val defaultBranch = withContext(Dispatchers.IO) {
      api.graphQL.getProject(projectMapping.repository).body().repository.rootRef
    }
    val targetBranch = GitBranchUtil.stripRefsPrefix(pushResult.targetBranch)
    if (targetBranch.endsWith(defaultBranch)) return null
    val mrBranch = targetBranch.removePrefix("${projectData.projectMapping.gitRemote.name}/")

    val mergeRequest = withContext(Dispatchers.IO) {
      val targetProjectPath = projectMapping.repository.projectPath.fullPath()
      val mrs = api.graphQL.findMergeRequestsByBranch(projectMapping.repository, GitLabMergeRequestState.OPENED, mrBranch).body()!!.nodes
      mrs.find { it.targetProject.fullPath == targetProjectPath && it.sourceProject?.fullPath == targetProjectPath }
    }

    return mergeRequest != null
  }

  private data class ProjectData(
    val projectMapping: GitLabProjectMapping,
    val account: GitLabAccount
  )
}