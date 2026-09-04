// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.openapi.components.service
import git4idea.GitStandardLocalBranch
import git4idea.remote.hosting.findHostedRemoteBranchTrackedBy
import git4idea.remote.hosting.findKnownRepositories
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchReviewPresenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly
import org.jetbrains.plugins.github.api.data.pullrequest.toPRIdentifier
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

private typealias PullRequestAndAccount = Pair<GHPullRequestRestIdOnly, Pair<GHRepositoryCoordinates, GithubAccount>>

class GHBranchReviewPresenter : GitBranchReviewPresenter {
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getReviewsFlow(
    repository: GitRepository,
    branches: Set<GitStandardLocalBranch>,
  ): Flow<Map<GitStandardLocalBranch, GitBranchReviewPresenter.Review?>>? {
    val repositoriesManager = repository.project.service<GHHostedRepositoriesManager>()
    if (repositoriesManager.findKnownRepositories(repository).isEmpty()) return null

    val connectionManager = repository.project.service<GHRepositoryConnectionManager>()
    // GitHub's REST API has no batch "find PR by branch" call, so each branch is still looked up on its own -
    // but concurrently, under this single per-repository subscription, instead of one subscription per branch.
    val branchReviewFlows = branches.map { branch ->
      getReviewFlow(repository, branch, repositoriesManager, connectionManager).map { review -> branch to review }
    }
    return combine(branchReviewFlows) { pairs -> pairs.toMap() }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun getReviewFlow(
    repository: GitRepository,
    branch: GitStandardLocalBranch,
    repositoriesManager: GHHostedRepositoriesManager,
    connectionManager: GHRepositoryConnectionManager,
  ): Flow<GitBranchReviewPresenter.Review?> =
    repositoriesManager.findHostedRemoteBranchTrackedBy(repository, branch)
      .combine(connectionManager.connectionState) { mappingAndBranch, connection -> mappingAndBranch to connection }
      .flatMapLatest { (mappingAndBranch, connection) ->
        val (mapping, remoteBranch) = mappingAndBranch ?: return@flatMapLatest flowOf<PullRequestAndAccount?>(null)
        if (connection == null || connection.repo != mapping) return@flatMapLatest flowOf(null)
        val repoAndAccount = mapping.repository to connection.account
        flow {
          val pullRequest = connection.dataContext.creationService
            .findOpenPullRequestDetails(null, mapping.repository.repositoryPath, remoteBranch)
          emit(pullRequest?.let { it to repoAndAccount })
        }
      }
      .map { prAndRepoAndAccount ->
        prAndRepoAndAccount?.let { (pullRequest, repoAndAccount) ->
          val project = repository.project
          val prId = pullRequest.toPRIdentifier()
          GitBranchReviewPresenter.Review(pullRequest.title ?: branch.name) {
            project.service<GHPRProjectViewModel>().activateAndAwaitProject(repoAndAccount) {
              viewPullRequest(prId)
            }
          }
        }
      }
}
