// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.notification

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
import git4idea.remote.hosting.knownRepositories
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.pullrequest.action.GHPRCreatePullRequestNotificationAction
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

class GHPushNotificationCustomizer(private val project: Project) : GitPushNotificationCustomizer {
  private val settings: GithubPullRequestsProjectUISettings = GithubPullRequestsProjectUISettings.getInstance(project)
  private val projectsManager: GHHostedRepositoriesManager = project.service<GHHostedRepositoriesManager>()
  private val defaultAccountHolder: GithubProjectDefaultAccountHolder = project.service<GithubProjectDefaultAccountHolder>()
  private val accountManager: GHAccountManager = service<GHAccountManager>()

  override suspend fun getActions(
    repository: GitRepository,
    pushResult: GitPushRepoResult,
    customParams: Map<String, VcsPushOptionValue>
  ): List<AnAction> {
    val repoAndAccount = selectAccount(repository, pushResult) ?: return emptyList()
    val exists = doesReviewExist(pushResult, repoAndAccount) ?: return emptyList()
    if (exists) return emptyList()

    val connection = project.serviceAsync<GHRepositoryConnectionManager>().connectionState.value
    if (connection?.account != repoAndAccount.account || connection.repo != repoAndAccount.projectMapping) return emptyList()

    return listOf(GHPRCreatePullRequestNotificationAction(project, repoAndAccount.projectMapping, repoAndAccount.account))
  }

  private fun selectAccount(targetRepository: GitRepository, pushResult: GitPushRepoResult): RepositoryAndAccount? {
    val accountFromSettings = trySelectAccountFromSettings(targetRepository, pushResult)
    if (accountFromSettings != null) {
      return accountFromSettings
    }

    val selectedAccountAndRepository = trySelectSingleAccount(targetRepository, pushResult)
    if (selectedAccountAndRepository != null) {
      return selectedAccountAndRepository
    }

    return null
  }

  /**
   * Try to select an account from settings with necessary repository mappings
   *
   * @param targetRepository The target Git repository
   * @param pushResult The result of the Git push operation
   * @return The selected repository and account, or null if no account is found or if the target repository does not match the project mapping
   */
  private fun trySelectAccountFromSettings(targetRepository: GitRepository, pushResult: GitPushRepoResult): RepositoryAndAccount? {
    val (url, account) = settings.selectedUrlAndAccount ?: return null
    val projectMapping = projectsManager.knownRepositories.find { mapping: GHGitRepositoryMapping ->
      mapping.remote.url == url
    } ?: return null

    if (targetRepository != projectMapping.gitRepository) return null

    val remote = GitUtil.findRemoteByName(targetRepository, pushResult.targetRemote) ?: return null
    if (remote != projectMapping.gitRemote) return null

    return RepositoryAndAccount(projectMapping, account)
  }

  /**
   * Try to select an account from an account list with necessary repository mappings
   *
   * @param targetRepository The target Git repository
   * @param pushResult The result of the Git push operation
   * @return The RepositoryAndAccount object if a single account is found, null otherwise
   */
  private fun trySelectSingleAccount(targetRepository: GitRepository, pushResult: GitPushRepoResult): RepositoryAndAccount? {
    val targetRemote = GitUtil.findRemoteByName(targetRepository, pushResult.targetRemote)
    val projectMapping = projectsManager.knownRepositoriesState.value.find { mapping: GHGitRepositoryMapping ->
      mapping.gitRepository == targetRepository && mapping.gitRemote == targetRemote
    } ?: return null

    val defaultAccount = defaultAccountHolder.account
    if (defaultAccount?.server == projectMapping.repository.serverPath) {
      return RepositoryAndAccount(projectMapping, defaultAccount)
    }

    val account = accountManager.findAccountOrNull { account ->
      account.server == projectMapping.repository.serverPath
    } ?: return null

    return RepositoryAndAccount(projectMapping, account)
  }

  private suspend fun doesReviewExist(pushResult: GitPushRepoResult, repoAndAccount: RepositoryAndAccount): Boolean? {
    val (projectMapping, account) = repoAndAccount
    val token = accountManager.findCredentials(account) ?: return null
    val executor = GithubApiRequestExecutor.Factory.getInstance().create(token)

    val repositoryInfoRequest = GHGQLRequests.Repo.find(GHRepositoryCoordinates(account.server, projectMapping.repository.repositoryPath))
    val repositoryInfo = executor.executeSuspend(repositoryInfoRequest) ?: return null
    val defaultBranch = repositoryInfo.defaultBranch
    val targetBranch = GitBranchUtil.stripRefsPrefix(pushResult.targetBranch)
    if (defaultBranch != null && targetBranch.endsWith(defaultBranch)) return null
    val prBranch = targetBranch.removePrefix("${repoAndAccount.projectMapping.gitRemote.name}/")

    val findPullRequestByBranchesRequest = GHGQLRequests.PullRequest.findByBranches(
      repository = projectMapping.repository,
      baseBranch = null,
      headBranch = prBranch
    )
    val targetProjectPath = projectMapping.repository.repositoryPath.toString()
    val pullRequest = executor.executeSuspend(findPullRequestByBranchesRequest).nodes.find {
      it.baseRepository?.nameWithOwner == targetProjectPath &&
      it.headRepository?.nameWithOwner == targetProjectPath
    }

    return pullRequest != null
  }

  private data class RepositoryAndAccount(
    val projectMapping: GHGitRepositoryMapping,
    val account: GithubAccount
  )
}