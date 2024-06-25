// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.notification

import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.branch.GitBranchUtil
import git4idea.push.*
import git4idea.remote.hosting.knownRepositories
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.pullrequest.action.GHPRCreatePullRequestNotificationAction
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

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


    val canCreate = canCreateReview(pushResult, projectMapping, account)
    if (!canCreate) return emptyList()

    return listOf(GHPRCreatePullRequestNotificationAction(project, projectMapping, account))
  }

  private suspend fun canCreateReview(pushResult: GitPushRepoResult, projectMapping: GHGitRepositoryMapping, account: GithubAccount): Boolean {
    try {
      val accountManager: GHAccountManager = serviceAsync<GHAccountManager>()
      val token = accountManager.findCredentials(account) ?: return false
      val executor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)
      val prBranch = getReviewBranch(executor, pushResult, projectMapping, account) ?: return false
      val pullRequest = findPullRequest(executor, projectMapping, prBranch)

      return pullRequest == null
    }
    catch (e: Exception) {
      LOG.warn("Failed to lookup an existing pull request for $pushResult", e)
      return false
    }
  }

  private suspend fun getReviewBranch(
    executor: GithubApiRequestExecutor,
    pushResult: GitPushRepoResult,
    projectMapping: GHGitRepositoryMapping,
    account: GithubAccount
  ): String? {
    val repositoryInfoRequest = GHGQLRequests.Repo.find(GHRepositoryCoordinates(account.server, projectMapping.repository.repositoryPath))
    val repositoryInfo = executor.executeSuspend(repositoryInfoRequest) ?: return null
    val defaultBranch = repositoryInfo.defaultBranch
    val targetBranch = GitBranchUtil.stripRefsPrefix(pushResult.targetBranch)
    if (defaultBranch != null && targetBranch.endsWith(defaultBranch)) return null

    return targetBranch.removePrefix("${projectMapping.gitRemote.name}/")
  }

  private suspend fun findPullRequest(
    executor: GithubApiRequestExecutor,
    projectMapping: GHGitRepositoryMapping,
    prBranch: String,
  ): GHPullRequest? {
    val findPullRequestByBranchesRequest = GHGQLRequests.PullRequest.findByBranches(
      repository = projectMapping.repository,
      baseBranch = null,
      headBranch = prBranch
    )
    val targetProjectPath = projectMapping.repository.repositoryPath.toString()
    return executor.executeSuspend(findPullRequestByBranchesRequest).nodes.find {
      it.baseRepository?.nameWithOwner == targetProjectPath &&
      it.headRepository?.nameWithOwner == targetProjectPath
    }
  }
}