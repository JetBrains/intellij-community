// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.notification

import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.GitRemoteBranch
import git4idea.push.*
import git4idea.remote.hosting.knownRepositories
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests.Repos.PullRequests
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.pullrequest.action.GHPRCreatePullRequestNotificationAction
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.util.concurrent.CancellationException

private val LOG = logger<GHPushNotificationCustomizer>()

internal class GHPushNotificationCustomizer(private val project: Project) : GitPushNotificationCustomizer {
  override suspend fun getActions(
    repository: GitRepository,
    pushResult: GitPushRepoResult,
    customParams: Map<String, VcsPushOptionValue>,
  ): List<AnAction> {
    if (!pushResult.isSuccessful) return emptyList()
    val remoteBranch = pushResult.findRemoteBranch(repository) ?: return emptyList()

    val connection = project.serviceAsync<GHRepositoryConnectionManager>().connectionState.value
    if (connection != null && (connection.repo.gitRepository != repository || connection.repo.gitRemote != remoteBranch.remote)) {
      return emptyList()
    }

    val (projectMapping, account) = connection?.let {
      it.repo to it.account
    } ?: GitPushNotificationUtil.findRepositoryAndAccount(
      project.serviceAsync<GHHostedRepositoriesManager>().knownRepositories,
      repository, remoteBranch.remote,
      serviceAsync<GHAccountManager>().accountsState.value,
      project.serviceAsync<GithubPullRequestsProjectUISettings>().selectedUrlAndAccount?.second,
      project.serviceAsync<GithubProjectDefaultAccountHolder>().account
    ) ?: return emptyList()

    val canCreate = canCreateReview(projectMapping, account, remoteBranch)
    if (!canCreate) return emptyList()

    return listOf(GHPRCreatePullRequestNotificationAction(project, projectMapping, account))
  }

  private suspend fun canCreateReview(repositoryMapping: GHGitRepositoryMapping, account: GithubAccount, branch: GitRemoteBranch): Boolean {
    val accountManager = serviceAsync<GHAccountManager>()
    val token = accountManager.findCredentials(account) ?: return false
    val executor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)

    val repository = repositoryMapping.repository
    val repositoryInfo = getRepositoryInfo(executor, repository) ?: run {
      LOG.warn("Repository not found $repository")
      return false
    }

    val remoteBranchName = branch.nameForRemoteOperations
    if (repositoryInfo.defaultBranch == remoteBranchName) {
      return false
    }

    if (findExistingPullRequests(executor, repository, remoteBranchName).isNotEmpty()) {
      return false
    }

    return true
  }

  private suspend fun getRepositoryInfo(executor: GithubApiRequestExecutor, repository: GHRepositoryCoordinates) =
    try {
      executor.executeSuspend(GHGQLRequests.Repo.find(repository))
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.warn("Failed to lookup a repository $repository", e)
      null
    }

  private suspend fun findExistingPullRequests(
    executor: GithubApiRequestExecutor,
    repository: GHRepositoryCoordinates,
    remoteBranchName: String,
  ): List<GHPullRequestRestIdOnly> = try {
    executor.executeSuspend(PullRequests.find(repository,
                                              GithubIssueState.open,
                                              null,
                                              repository.repositoryPath.owner + ":" + remoteBranchName)).items
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (e: Exception) {
    LOG.warn("Failed to lookup an existing pull request for $remoteBranchName in $repository", e)
    emptyList()
  }
}