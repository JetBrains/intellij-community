// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.AtomicClearableLazyValue
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader

internal class GithubPullRequestsRepositoryDataLoaderImpl(private val progressManager: ProgressManager,
                                                          private val requestExecutor: GithubApiRequestExecutor,
                                                          private val serverPath: GithubServerPath,
                                                          private val repoPath: GithubFullPath)
  : GithubPullRequestsRepositoryDataLoader, Disposable {

  init {
    requestExecutor.addListener(this) {
      reset()
    }
  }

  private val repoCollaboratorsWithPushAccessValue = object : AtomicClearableLazyValue<List<GithubUser>>() {
    override fun compute() = GithubApiPagesLoader
      .loadAll(requestExecutor, progressManager.progressIndicator,
               GithubApiRequests.Repos.Collaborators.pages(serverPath, repoPath.user, repoPath.repository))
      .filter { it.permissions.isPush }
  }
  override val collaboratorsWithPushAccess: List<GithubUser>
    get() = repoCollaboratorsWithPushAccessValue.value

  private val repoIssuesAssigneesValue = object : AtomicClearableLazyValue<List<GithubUser>>() {
    override fun compute() = GithubApiPagesLoader
      .loadAll(requestExecutor, progressManager.progressIndicator,
               GithubApiRequests.Repos.Assignees.pages(serverPath, repoPath.user, repoPath.repository))
  }
  override val issuesAssignees: List<GithubUser>
    get() = repoIssuesAssigneesValue.value

  private val repoIssuesLabelsValue = object : AtomicClearableLazyValue<List<GithubIssueLabel>>() {
    override fun compute() = GithubApiPagesLoader
      .loadAll(requestExecutor, progressManager.progressIndicator,
               GithubApiRequests.Repos.Labels.pages(serverPath, repoPath.user, repoPath.repository))
  }
  override val issuesLabels: List<GithubIssueLabel>
    get() = repoIssuesLabelsValue.value

  override fun reset() {
    repoCollaboratorsWithPushAccessValue.drop()
    repoIssuesAssigneesValue.drop()
    repoIssuesLabelsValue.drop()
  }

  override fun dispose() {
    reset()
  }
}