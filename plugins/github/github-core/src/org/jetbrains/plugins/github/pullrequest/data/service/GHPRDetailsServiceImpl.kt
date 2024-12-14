// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.util.CollectionDelta
import com.intellij.collaboration.util.ResultUtil.processErrorAndGet
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.progress.reportProgress
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHNotFoundException
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityStateBuilder

internal class GHPRDetailsServiceImpl(
  private val project: Project,
  private val securityService: GHPRSecurityService,
  private val requestExecutor: GithubApiRequestExecutor,
  private val repository: GHRepositoryCoordinates,
) : GHPRDetailsService {
  private val serverPath = repository.serverPath
  private val repoPath = repository.repositoryPath

  override suspend fun findPRId(number: Long): GHPRIdentifier? =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.findOneId(repository, number))
    }.getOrElse { null }

  override suspend fun loadDetails(pullRequestId: GHPRIdentifier): GHPullRequest =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.findOne(repository, pullRequestId.number))
      ?: throw GHNotFoundException("Pull request ${pullRequestId.number} does not exist")
    }.processErrorAndGet {
      LOG.info("Error occurred while loading PR details", it)
    }

  override suspend fun updateDetails(pullRequestId: GHPRIdentifier, title: String?, description: String?): GHPullRequest =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.update(repository, pullRequestId.id, title, description))
    }.processErrorAndGet {
      LOG.info("Error occurred while updating PR details", it)
    }

  override suspend fun adjustReviewers(pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHPullRequestRequestedReviewer>) =
    runCatching {
      reportProgress { reporter ->
        reporter.indeterminateStep(GithubBundle.message("pull.request.details.adjusting.reviewers")) {
          val removedItems = delta.removedItems
          if (removedItems.isNotEmpty()) {
            reporter.indeterminateStep(GithubBundle.message("pull.request.removing.reviewers")) {
              requestExecutor.executeSuspend(GithubApiRequests.Repos.PullRequests.Reviewers
                                               .remove(serverPath, repoPath.owner, repoPath.repository, pullRequestId.number,
                                                       removedItems.filterIsInstance<GHUser>().map { it.login },
                                                       removedItems.filterIsInstance<GHTeam>().map { it.slug }))
            }
          }
          val newItems = delta.newItems
          if (newItems.isNotEmpty()) {
            reporter.indeterminateStep(GithubBundle.message("pull.request.adding.reviewers")) {
              requestExecutor.executeSuspend(GithubApiRequests.Repos.PullRequests.Reviewers
                                               .add(serverPath, repoPath.owner, repoPath.repository, pullRequestId.number,
                                                    newItems.filterIsInstance<GHUser>().map { it.login },
                                                    newItems.filterIsInstance<GHTeam>().map { it.slug }))
            }
          }
        }
      }
    }.processErrorAndGet {
      LOG.info("Error occurred while adjusting the list of reviewers", it)
    }

  override suspend fun adjustAssignees(pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHUser>) {
    runCatching {
      reportProgress { reporter ->
        reporter.indeterminateStep(GithubBundle.message("pull.request.details.adjusting.assignees")) {
          requestExecutor.executeSuspend(GithubApiRequests.Repos.Issues
                                           .updateAssignees(serverPath, repoPath.owner, repoPath.repository,
                                                            pullRequestId.number.toString(),
                                                            delta.newCollection.map { it.login }))
        }
      }
    }.processErrorAndGet {
      LOG.error("Error occurred while adjusting the list of assignees")
    }
  }

  override suspend fun adjustLabels(pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHLabel>) {
    runCatching {
      reportProgress { reporter ->
        reporter.indeterminateStep(GithubBundle.message("pull.request.details.adjusting.labels")) {
          requestExecutor.executeSuspend(GithubApiRequests.Repos.Issues.Labels
                                           .replace(serverPath, repoPath.owner, repoPath.repository, pullRequestId.number.toString(),
                                                    delta.newCollection.map { it.name }))
        }
      }
    }.processErrorAndGet {
      LOG.error("Error occurred while adjusting the list of labels")
    }
  }

  override suspend fun loadMergeabilityState(pullRequestId: GHPRIdentifier): GHPRMergeabilityState =
    runCatching {
      val mergeabilityData = requestExecutor.executeSuspend(GHGQLRequests.PullRequest.mergeabilityData(repository, pullRequestId.number))
                             ?: error("Could not find pull request ${pullRequestId.number}")
      val currentUserIsAdmin = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.ADMIN)
      GHPRMergeabilityStateBuilder(mergeabilityData, currentUserIsAdmin).build()
    }.processErrorAndGet {
      LOG.info("Error occurred while loading mergeability state data for PR ${pullRequestId.number}")
    }


  override suspend fun close(pullRequestId: GHPRIdentifier) {
    runCatching {
      requestExecutor.executeSuspend(
        GithubApiRequests.Repos.PullRequests.update(serverPath, repoPath.owner, repoPath.repository,
                                                    pullRequestId.number,
                                                    state = GithubIssueState.closed))
    }.processErrorAndGet {
      LOG.info("Error occurred while closing PR ${pullRequestId.number}")
    }
  }

  override suspend fun reopen(pullRequestId: GHPRIdentifier) {
    runCatching {
      requestExecutor.executeSuspend(
        GithubApiRequests.Repos.PullRequests.update(serverPath, repoPath.owner, repoPath.repository,
                                                    pullRequestId.number,
                                                    state = GithubIssueState.open))
    }.processErrorAndGet {
      LOG.info("Error occurred while reopening PR ${pullRequestId.number}")
    }
  }

  override suspend fun markReadyForReview(pullRequestId: GHPRIdentifier) {
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.markReadyForReview(repository, pullRequestId.id))
    }.processErrorAndGet {
      LOG.info("Error occurred while marking PR ${pullRequestId.number} ready fro review")
    }
  }

  override suspend fun merge(pullRequestId: GHPRIdentifier, commitMessage: Pair<String, String>, currentHeadRef: String) =
    runCatching {
      requestExecutor.executeSuspend(
        GithubApiRequests.Repos.PullRequests.merge(serverPath, repoPath, pullRequestId.number,
                                                   commitMessage.first, commitMessage.second,
                                                   currentHeadRef))
      GHPRStatisticsCollector.logMergedEvent(project, GithubPullRequestMergeMethod.merge)
    }.processErrorAndGet {
      LOG.info("Error occurred while merging PR ${pullRequestId.number}")
    }


  override suspend fun rebaseMerge(pullRequestId: GHPRIdentifier, currentHeadRef: String) {
    runCatching {
      requestExecutor.executeSuspend(
        GithubApiRequests.Repos.PullRequests.rebaseMerge(serverPath, repoPath, pullRequestId.number,
                                                         currentHeadRef))
      GHPRStatisticsCollector.logMergedEvent(project, GithubPullRequestMergeMethod.rebase)
    }.processErrorAndGet {
      LOG.info("Error occurred while rebasing PR ${pullRequestId.number}")
    }
  }

  override suspend fun squashMerge(pullRequestId: GHPRIdentifier, commitMessage: Pair<String, String>, currentHeadRef: String) {
    runCatching {
      requestExecutor.executeSuspend(
        GithubApiRequests.Repos.PullRequests.squashMerge(serverPath, repoPath, pullRequestId.number,
                                                         commitMessage.first, commitMessage.second,
                                                         currentHeadRef))
      GHPRStatisticsCollector.logMergedEvent(project, GithubPullRequestMergeMethod.squash)
    }.processErrorAndGet {
      LOG.info("Error occurred while squash-merging PR ${pullRequestId.number}")
    }
  }

  companion object {
    private val LOG = logger<GHPRDetailsService>()
  }
}