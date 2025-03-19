// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code
// is governed by the Apache 2.0 license.
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
import org.jetbrains.plugins.github.api.data.pullrequest.toPRIdentifier
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.pullrequest.action.GHOpenPullRequestExistingTabNotificationAction
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

    // If we already have a GH connection open, make sure it matches
    val connection = project.serviceAsync<GHRepositoryConnectionManager>().connectionState.value
    if (connection != null && (connection.repo.gitRepository != repository || connection.repo.gitRemote != remoteBranch.remote)) {
      return emptyList()
    }

    val (projectMapping, account) =
      connection?.let { it.repo to it.account }
      ?: GitPushNotificationUtil.findRepositoryAndAccount(
        project.serviceAsync<GHHostedRepositoriesManager>().knownRepositories,
        repository,
        remoteBranch.remote,
        project.serviceAsync<GHAccountManager>().accountsState.value,
        project.serviceAsync<GithubPullRequestsProjectUISettings>().selectedUrlAndAccount?.second,
        project.serviceAsync<GithubProjectDefaultAccountHolder>().account
      )
      ?: return emptyList()


    if (!canCreateReview(projectMapping, account, remoteBranch)) {
      return emptyList()
    }

    val existingPrs = findExistingPullRequests(projectMapping, account, remoteBranch)
    return when (existingPrs.size) {
      0 -> {
        listOf(GHPRCreatePullRequestNotificationAction(project, projectMapping, account))
      }
      1 -> {
        val singlePr = existingPrs.first()
        listOf(GHOpenPullRequestExistingTabNotificationAction(project, projectMapping, account, singlePr.toPRIdentifier()))
      }
      else -> {
        emptyList()
      }
    }
  }

  /**
   * Checks if it's even valid to create a PR.
   * For instance:
   * - The repository must exist
   * - The branch cannot be the default branch (we don't allow creating a PR from default -> default)
   */
  private suspend fun canCreateReview(
    repositoryMapping: GHGitRepositoryMapping,
    account: GithubAccount,
    branch: GitRemoteBranch,
  ): Boolean {
    val accountManager = serviceAsync<GHAccountManager>()
    val token = accountManager.findCredentials(account) ?: return false
    val executor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)

    val repository = repositoryMapping.repository
    val repositoryInfo = getRepositoryInfo(executor, repository) ?: run {
      LOG.warn("Repository not found: $repository")
      return false
    }

    // Don't allow creating PRs targeting the default branch
    val remoteBranchName = branch.nameForRemoteOperations
    return repositoryInfo.defaultBranch != remoteBranchName
  }

  private suspend fun getRepositoryInfo(
    executor: GithubApiRequestExecutor,
    repository: GHRepositoryCoordinates,
  ) = try {
    executor.executeSuspend(GHGQLRequests.Repo.find(repository))
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (e: Exception) {
    LOG.warn("Failed to lookup repository $repository", e)
    null
  }

  /**
   * Look up any existing open pull requests on the given remote branch.
   */
  private suspend fun findExistingPullRequests(
    repositoryMapping: GHGitRepositoryMapping,
    account: GithubAccount,
    branch: GitRemoteBranch,
  ): List<GHPullRequestRestIdOnly> {
    val accountManager = serviceAsync<GHAccountManager>()
    val token = accountManager.findCredentials(account) ?: return emptyList()
    val executor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)

    val repository = repositoryMapping.repository
    val remoteBranchName = branch.nameForRemoteOperations

    return try {
      executor.executeSuspend(PullRequests.find(repository, GithubIssueState.open, baseRef = null, headRef = repository.repositoryPath.owner + ":" + remoteBranchName)).items
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.warn("Failed to lookup existing pull requests for branch $remoteBranchName in $repository", e)
      emptyList()
    }
  }
}