// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.notification

import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import git4idea.account.AccountUtil
import git4idea.account.RepoAndAccount
import git4idea.branch.GitBranchUtil
import git4idea.push.GitPushNotificationCustomizer
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.request.getProject
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestByBranchDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.findMergeRequestsByBranch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.action.GitLabMergeRequestOpenCreateTabNotificationAction
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

typealias GitLabRepoAndAccount = RepoAndAccount<GitLabProjectMapping, GitLabAccount>

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
    val repoAndAccount = selectRepoAndAccount(repository, pushResult) ?: return emptyList()
    val exists = doesReviewExist(pushResult, repoAndAccount) ?: return emptyList()
    if (exists) return emptyList()

    val connection = project.serviceAsync<GitLabProjectConnectionManager>().connectionState.value
    if (connection?.account != repoAndAccount.account || connection.repo != repoAndAccount.projectMapping) return emptyList()

    return listOf(GitLabMergeRequestOpenCreateTabNotificationAction(project, repoAndAccount.projectMapping, repoAndAccount.account))
  }

  private fun selectRepoAndAccount(targetRepository: GitRepository, pushResult: GitPushRepoResult): GitLabRepoAndAccount? {
    AccountUtil.selectPersistedRepoAndAccount(targetRepository, pushResult, preferences.selectedRepoAndAccount)?.let {
      return it
    }
    AccountUtil.selectSingleAccount(projectsManager, accountManager, targetRepository, pushResult, defaultAccountHolder.account)?.let {
      return it
    }

    return null
  }

  private suspend fun doesReviewExist(pushResult: GitPushRepoResult, repoAndAccount: GitLabRepoAndAccount): Boolean? {
    val (projectMapping, account) = repoAndAccount
    val token = accountManager.findCredentials(account) ?: return null
    val api = apiManager.getClient(account.server, token)
    val mrBranch = getReviewBranch(api, pushResult, projectMapping) ?: return null
    val mergeRequest = getMergeRequest(api, projectMapping, mrBranch)

    return mergeRequest != null
  }

  private suspend fun getReviewBranch(
    api: GitLabApi,
    pushResult: GitPushRepoResult,
    projectMapping: GitLabProjectMapping
  ): String? {
    val defaultBranch = withContext(Dispatchers.IO) {
      api.graphQL.getProject(projectMapping.repository).body().repository?.rootRef
    }
    val targetBranch = GitBranchUtil.stripRefsPrefix(pushResult.targetBranch)
    if (defaultBranch != null && targetBranch.endsWith(defaultBranch)) return null

    return targetBranch.removePrefix("${projectMapping.gitRemote.name}/")
  }

  private suspend fun getMergeRequest(
    api: GitLabApi,
    projectMapping: GitLabProjectMapping,
    mrBranch: String
  ): GitLabMergeRequestByBranchDTO? {
    return withContext(Dispatchers.IO) {
      val targetProjectPath = projectMapping.repository.projectPath.fullPath()
      val mrs = api.graphQL.findMergeRequestsByBranch(projectMapping.repository, GitLabMergeRequestState.OPENED, mrBranch).body()!!.nodes
      mrs.find { it.targetProject.fullPath == targetProjectPath && it.sourceProject?.fullPath == targetProjectPath }
    }
  }
}