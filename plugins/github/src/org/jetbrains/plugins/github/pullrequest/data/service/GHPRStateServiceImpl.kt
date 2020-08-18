// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GHBranchProtectionRules
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityStateBuilder
import org.jetbrains.plugins.github.pullrequest.data.service.GHServiceUtil.logError
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture

class GHPRStateServiceImpl internal constructor(private val progressManager: ProgressManager,
                                                private val securityService: GHPRSecurityService,
                                                private val requestExecutor: GithubApiRequestExecutor,
                                                private val serverPath: GithubServerPath,
                                                private val repoPath: GHRepositoryPath)
  : GHPRStateService {

  private val repository = GHRepositoryCoordinates(serverPath, repoPath)

  override fun loadBranchProtectionRules(progressIndicator: ProgressIndicator,
                                         pullRequestId: GHPRIdentifier,
                                         baseBranch: String): CompletableFuture<GHBranchProtectionRules?> {
    if (!securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)) return CompletableFuture.completedFuture(null)

    return progressManager.submitIOTask(progressIndicator) {
      try {
        requestExecutor.execute(GithubApiRequests.Repos.Branches.getProtection(repository, baseBranch))
      }
      catch (e: Exception) {
        // assume there are no restrictions
        if (e !is ProcessCanceledException) LOG.info("Error occurred while loading branch protection rules for $baseBranch", e)
        null
      }
    }
  }

  override fun loadMergeabilityState(progressIndicator: ProgressIndicator,
                                     pullRequestId: GHPRIdentifier,
                                     headRefOid: String,
                                     prHtmlUrl: String,
                                     baseBranchProtectionRules: GHBranchProtectionRules?) =
    progressManager.submitIOTask(progressIndicator) {
      val mergeabilityData = requestExecutor.execute(GHGQLRequests.PullRequest.mergeabilityData(repository, pullRequestId.number))
                             ?: error("Could not find pull request $pullRequestId.number")
      val builder = GHPRMergeabilityStateBuilder(headRefOid, prHtmlUrl,
                                                 mergeabilityData)
      if (baseBranchProtectionRules != null) {
        builder.withRestrictions(securityService, baseBranchProtectionRules)
      }
      builder.build()
    }.logError(LOG, "Error occurred while loading mergeability state data for PR ${pullRequestId.number}")


  override fun close(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.PullRequests.update(serverPath, repoPath.owner, repoPath.repository,
                                                                          pullRequestId.number,
                                                                          state = GithubIssueState.closed))
      return@submitIOTask
    }.logError(LOG, "Error occurred while closing PR ${pullRequestId.number}")

  override fun reopen(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.PullRequests.update(serverPath, repoPath.owner, repoPath.repository,
                                                                          pullRequestId.number,
                                                                          state = GithubIssueState.open))
      return@submitIOTask
    }.logError(LOG, "Error occurred while reopening PR ${pullRequestId.number}")

  override fun markReadyForReview(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GHGQLRequests.PullRequest.markReadyForReview(repository, pullRequestId.id))
      return@submitIOTask
    }.logError(LOG, "Error occurred while marking PR ${pullRequestId.number} ready fro review")

  override fun merge(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier,
                     commitMessage: Pair<String, String>, currentHeadRef: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it, GithubApiRequests.Repos.PullRequests.merge(serverPath, repoPath, pullRequestId.number,
                                                                             commitMessage.first, commitMessage.second,
                                                                             currentHeadRef))
      GHPRStatisticsCollector.logMergedEvent(GithubPullRequestMergeMethod.merge)
      return@submitIOTask
    }.logError(LOG, "Error occurred while merging PR ${pullRequestId.number}")


  override fun rebaseMerge(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier,
                           currentHeadRef: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.PullRequests.rebaseMerge(serverPath, repoPath, pullRequestId.number,
                                                                               currentHeadRef))
      GHPRStatisticsCollector.logMergedEvent(GithubPullRequestMergeMethod.rebase)
      return@submitIOTask
    }.logError(LOG, "Error occurred while rebasing PR ${pullRequestId.number}")

  override fun squashMerge(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier,
                           commitMessage: Pair<String, String>, currentHeadRef: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.PullRequests.squashMerge(serverPath, repoPath, pullRequestId.number,
                                                                               commitMessage.first, commitMessage.second,
                                                                               currentHeadRef))
      GHPRStatisticsCollector.logMergedEvent(GithubPullRequestMergeMethod.squash)
      return@submitIOTask
    }.logError(LOG, "Error occurred while squash-merging PR ${pullRequestId.number}")

  companion object {
    private val LOG = logger<GHPRStateService>()
  }
}
