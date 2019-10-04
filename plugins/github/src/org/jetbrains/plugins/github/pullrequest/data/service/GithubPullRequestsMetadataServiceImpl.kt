// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.messages.MessageBus
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext.Companion.PULL_REQUEST_EDITED_TOPIC
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture

class GithubPullRequestsMetadataServiceImpl internal constructor(progressManager: ProgressManager,
                                                                 private val messageBus: MessageBus,
                                                                 private val requestExecutor: GithubApiRequestExecutor,
                                                                 private val serverPath: GithubServerPath,
                                                                 private val repoPath: GHRepositoryPath)
  : GithubPullRequestsMetadataService {

  init {
    requestExecutor.addListener(this) {
      resetData()
    }
  }

  private val collaboratorsValue = LazyCancellableBackgroundProcessValue.create(progressManager) { indicator ->
    GithubApiPagesLoader
      .loadAll(requestExecutor, indicator,
               GithubApiRequests.Repos.Collaborators.pages(serverPath, repoPath.owner, repoPath.repository))
      .filter { it.permissions.isPush }
      .map { GHUser(it.nodeId, it.login, it.htmlUrl, it.avatarUrl ?: "", null) }
  }

  override val collaboratorsWithPushAccess: CompletableFuture<List<GHUser>>
    get() = GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { collaboratorsValue.value } }

  private val assigneesValue = LazyCancellableBackgroundProcessValue.create(progressManager) { indicator ->
    GithubApiPagesLoader
      .loadAll(requestExecutor, indicator,
               GithubApiRequests.Repos.Assignees.pages(serverPath, repoPath.owner, repoPath.repository))
      .map { GHUser(it.nodeId, it.login, it.htmlUrl, it.avatarUrl ?: "", null) }
  }

  override val issuesAssignees: CompletableFuture<List<GHUser>>
    get() = GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { assigneesValue.value } }
  private val labelsValue = LazyCancellableBackgroundProcessValue.create(progressManager) { indicator ->
    GithubApiPagesLoader
      .loadAll(requestExecutor, indicator,
               GithubApiRequests.Repos.Labels.pages(serverPath, repoPath.owner, repoPath.repository))
      .map { GHLabel(it.nodeId, it.url, it.name, it.color) }
  }

  override val labels: CompletableFuture<List<GHLabel>>
    get() = GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { labelsValue.value } }

  override fun resetData() {
    collaboratorsValue.drop()
    assigneesValue.drop()
    labelsValue.drop()
  }

  @CalledInBackground
  override fun adjustReviewers(indicator: ProgressIndicator, pullRequest: Long, delta: CollectionDelta<GHUser>) {
    if (delta.isEmpty) return

    if (delta.removedItems.isNotEmpty()) {
      indicator.text2 = "Removing reviewers"
      requestExecutor.execute(indicator,
                              GithubApiRequests.Repos.PullRequests.Reviewers
                                .remove(serverPath, repoPath.owner, repoPath.repository, pullRequest,
                                        delta.removedItems.map { it.login }))
    }
    if (delta.newItems.isNotEmpty()) {
      indicator.text2 = "Adding reviewers"
      requestExecutor.execute(indicator,
                              GithubApiRequests.Repos.PullRequests.Reviewers
                                .add(serverPath, repoPath.owner, repoPath.repository, pullRequest,
                                     delta.newItems.map { it.login }))
    }
    messageBus.syncPublisher(PULL_REQUEST_EDITED_TOPIC).onPullRequestEdited(pullRequest)
  }

  @CalledInBackground
  override fun adjustAssignees(indicator: ProgressIndicator, pullRequest: Long, delta: CollectionDelta<GHUser>) {
    if (delta.isEmpty) return

    requestExecutor.execute(indicator,
                            GithubApiRequests.Repos.Issues.updateAssignees(serverPath, repoPath.owner, repoPath.repository,
                                                                           pullRequest.toString(), delta.newCollection.map { it.login }))
    messageBus.syncPublisher(PULL_REQUEST_EDITED_TOPIC).onPullRequestEdited(pullRequest)
  }

  @CalledInBackground
  override fun adjustLabels(indicator: ProgressIndicator, pullRequest: Long, delta: CollectionDelta<GHLabel>) {
    if (delta.isEmpty) return

    requestExecutor.execute(indicator,
                            GithubApiRequests.Repos.Issues.Labels
                              .replace(serverPath, repoPath.owner, repoPath.repository, pullRequest.toString(),
                                       delta.newCollection.map { it.name }))
    messageBus.syncPublisher(PULL_REQUEST_EDITED_TOPIC).onPullRequestEdited(pullRequest)
  }

  override fun dispose() {
    resetData()
  }
}