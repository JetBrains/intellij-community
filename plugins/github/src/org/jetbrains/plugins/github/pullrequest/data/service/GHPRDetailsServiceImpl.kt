// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHNotFoundException
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHServiceUtil.logError
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture

class GHPRDetailsServiceImpl(private val progressManager: ProgressManager,
                             private val requestExecutor: GithubApiRequestExecutor,
                             private val repository: GHRepositoryCoordinates) : GHPRDetailsService {

  private val serverPath = repository.serverPath
  private val repoPath = repository.repositoryPath

  override fun loadDetails(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier): CompletableFuture<GHPullRequest> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it, GHGQLRequests.PullRequest.findOne(repository, pullRequestId.number))
      ?: throw GHNotFoundException("Pull request ${pullRequestId.number} does not exist")
    }.logError(LOG, "Error occurred while loading PR details")

  override fun updateDetails(indicator: ProgressIndicator, pullRequestId: GHPRIdentifier, title: String?, description: String?)
    : CompletableFuture<GHPullRequest> = progressManager.submitIOTask(indicator) {
    requestExecutor.execute(it, GHGQLRequests.PullRequest.update(repository, pullRequestId.id, title, description))
  }.logError(LOG, "Error occurred while loading PR details")

  override fun adjustReviewers(indicator: ProgressIndicator,
                               pullRequestId: GHPRIdentifier,
                               delta: CollectionDelta<GHPullRequestRequestedReviewer>) =
    progressManager.submitIOTask(indicator) {
      val removedItems = delta.removedItems
      if (removedItems.isNotEmpty()) {
        it.text2 = GithubBundle.message("pull.request.removing.reviewers")
        requestExecutor.execute(it,
                                GithubApiRequests.Repos.PullRequests.Reviewers
                                  .remove(serverPath, repoPath.owner, repoPath.repository, pullRequestId.number,
                                          removedItems.filterIsInstance(GHUser::class.java).map { it.login },
                                          removedItems.filterIsInstance(GHTeam::class.java).map { it.slug }))
      }
      val newItems = delta.newItems
      if (newItems.isNotEmpty()) {
        it.text2 = GithubBundle.message("pull.request.adding.reviewers")
        requestExecutor.execute(it,
                                GithubApiRequests.Repos.PullRequests.Reviewers
                                  .add(serverPath, repoPath.owner, repoPath.repository, pullRequestId.number,
                                       newItems.filterIsInstance(GHUser::class.java).map { it.login },
                                       newItems.filterIsInstance(GHTeam::class.java).map { it.slug }))
      }
    }
      .logError(LOG, "Error occurred while adjusting the list of reviewers")

  override fun adjustAssignees(indicator: ProgressIndicator, pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHUser>) =
    progressManager.submitIOTask(indicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.Issues.updateAssignees(serverPath, repoPath.owner, repoPath.repository,
                                                                             pullRequestId.number.toString(),
                                                                             delta.newCollection.map { it.login }))
      return@submitIOTask
    }
      .logError(LOG, "Error occurred while adjusting the list of assignees")

  override fun adjustLabels(indicator: ProgressIndicator, pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHLabel>) =
    progressManager.submitIOTask(indicator) {
      requestExecutor.execute(indicator,
                              GithubApiRequests.Repos.Issues.Labels
                                .replace(serverPath, repoPath.owner, repoPath.repository, pullRequestId.number.toString(),
                                         delta.newCollection.map { it.name }))
      return@submitIOTask
    }.logError(LOG, "Error occurred while adjusting the list of labels")

  companion object {
    private val LOG = logger<GHPRDetailsService>()
  }
}