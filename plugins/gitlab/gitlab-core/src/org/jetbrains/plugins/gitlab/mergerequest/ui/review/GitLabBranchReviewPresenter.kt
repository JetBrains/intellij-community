// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

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
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestByBranchDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState

class GitLabBranchReviewPresenter : GitBranchReviewPresenter {
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getReviewsFlow(
    repository: GitRepository,
    branches: Set<GitStandardLocalBranch>,
  ): Flow<Map<GitStandardLocalBranch, GitBranchReviewPresenter.Review?>>? {
    val projectsManager = repository.project.service<GitLabProjectsManager>()
    if (projectsManager.findKnownRepositories(repository).isEmpty()) return null

    val connectionManager = repository.project.service<GitLabProjectConnectionManager>()
    val remoteBranchFlows = branches.map { branch ->
      projectsManager.findHostedRemoteBranchTrackedBy(repository, branch).map { branch to it }
    }
    return combine(remoteBranchFlows) { pairs -> pairs.toMap() }
      .combine(connectionManager.connectionState) { branchToMappingAndBranch, connection -> branchToMappingAndBranch to connection }
      .flatMapLatest { (branchToMappingAndBranch, connection) ->
        if (connection == null) {
          return@flatMapLatest flowOf<Map<GitStandardLocalBranch, GitLabMergeRequestByBranchDTO?>>(branches.associateWith { null })
        }
        val remoteNameByBranch = branchToMappingAndBranch.mapNotNull { (branch, mappingAndBranch) ->
          val (mapping, remoteBranch) = mappingAndBranch ?: return@mapNotNull null
          if (connection.repo != mapping) return@mapNotNull null
          branch to remoteBranch.nameForRemoteOperations
        }.toMap()
        if (remoteNameByBranch.isEmpty()) {
          return@flatMapLatest flowOf<Map<GitStandardLocalBranch, GitLabMergeRequestByBranchDTO?>>(branches.associateWith { null })
        }
        flow {
          val mergeRequestsBySourceBranch = connection.projectData.mergeRequests
            .findByBranches(GitLabMergeRequestState.OPENED, remoteNameByBranch.values)
            .groupBy { it.sourceBranch }
          emit(branches.associateWith { branch -> remoteNameByBranch[branch]?.let { mergeRequestsBySourceBranch[it]?.firstOrNull() } })
        }
      }
      .map { mergeRequestByBranch ->
        mergeRequestByBranch.mapValues { (_, mergeRequest) ->
          mergeRequest?.let { mr ->
            GitBranchReviewPresenter.Review(mr.title) {
              repository.project.service<GitLabProjectViewModel>().activateAndAwaitProject {
                openMergeRequestDetails(mr.iid, GitLabStatistics.ToolWindowOpenTabActionPlace.ACTION, focus = true)
              }
            }
          }
        }
      }
  }
}
