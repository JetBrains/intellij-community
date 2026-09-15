// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
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
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestBranchMatch
import org.jetbrains.plugins.github.api.data.pullrequest.toPRIdentifier
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

class GHBranchReviewPresenter : GitBranchReviewPresenter {
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getReviewsFlow(
    repository: GitRepository,
    branches: Set<GitStandardLocalBranch>,
  ): Flow<Map<GitStandardLocalBranch, GitBranchReviewPresenter.Review?>>? {
    val repositoriesManager = repository.project.service<GHHostedRepositoriesManager>()
    if (repositoriesManager.findKnownRepositories(repository).isEmpty()) {
      LOG.debug("No known GitHub repository for ${repository.root.name}, review status is disabled")
      return null
    }

    val connectionManager = repository.project.service<GHRepositoryConnectionManager>()
    val remoteBranchFlows = branches.map { branch ->
      repositoriesManager.findHostedRemoteBranchTrackedBy(repository, branch)
        .map { branch to it }
    }
    return combine(remoteBranchFlows) { pairs -> pairs.toMap() }
      .combine(connectionManager.connectionState) { branchToMappingAndBranch, connection -> branchToMappingAndBranch to connection }
      .flatMapLatest { (branchToMappingAndBranch, connection) ->
        val noReviews = flowOf(branches.associateWith { null as GitBranchReviewPresenter.Review? })
        if (connection == null) {
          return@flatMapLatest noReviews
        }

        val remoteBranchByBranch = branchToMappingAndBranch.mapNotNull { (branch, mappingAndBranch) ->
          val (mapping, remoteBranch) = mappingAndBranch ?: return@mapNotNull null
          if (connection.repo != mapping) return@mapNotNull null
          branch to remoteBranch
        }.toMap()
        if (remoteBranchByBranch.isEmpty()) return@flatMapLatest noReviews

        val repoAndAccount = connection.repo.repository to connection.account
        flow {
          val pullRequestsByHeadRef = connection.dataContext.creationService
            .findOpenPullRequestsByHeadBranches(remoteBranchByBranch.values)
            .groupBy { it.headRefName }
          emit(branches.associateWith { branch ->
            val remoteBranch = remoteBranchByBranch[branch] ?: return@associateWith null
            val pullRequest = pullRequestsByHeadRef[remoteBranch.nameForRemoteOperations]?.firstOrNull() ?: return@associateWith null
            toReview(repository, pullRequest, repoAndAccount, branch)
          })
        }
      }
  }

  private fun toReview(
    repository: GitRepository,
    pullRequest: GHPullRequestBranchMatch,
    repoAndAccount: Pair<GHRepositoryCoordinates, GithubAccount>,
    branch: GitStandardLocalBranch,
  ): GitBranchReviewPresenter.Review {
    val prId = pullRequest.toPRIdentifier()
    return GitBranchReviewPresenter.Review(pullRequest.title ?: branch.name) {
      repository.project.service<GHPRProjectViewModel>().activateAndAwaitProject(repoAndAccount) {
        viewPullRequest(prId)
      }
    }
  }
}

private val LOG = logger<GHBranchReviewPresenter>()
