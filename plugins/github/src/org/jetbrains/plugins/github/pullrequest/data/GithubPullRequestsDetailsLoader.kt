// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import git4idea.commands.Git
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListComponent
import java.util.concurrent.Future

class GithubPullRequestsDetailsLoader(progressManager: ProgressManager,
                                      private val requestExecutorHolder: GithubApiRequestExecutorManager.ManagedHolder,
                                      private val git: Git,
                                      private val repository: GitRepository,
                                      private val remote: GitRemote)
  : SingleWorkerProcessExecutor(progressManager,
                                "GitHub PR info loading breaker"), GithubPullRequestsListComponent.PullRequestSelectionListener {

  @set:CalledInAwt
  var detailsFuture: Future<GithubPullRequestDetailed>? = null
    private set

  override fun selectionChanged(selection: GithubSearchedIssue?) {
    cancelCurrentTasks()
    if (selection == null) {
      detailsFuture = null
    }
    else {
      val requestExecutor = requestExecutorHolder.executor
      detailsFuture = submit { indicator -> loadInformationAndFetchBranch(indicator, requestExecutor, selection) }
    }
  }

  @CalledInBackground
  private fun loadInformationAndFetchBranch(indicator: ProgressIndicator,
                                            requestExecutor: GithubApiRequestExecutor,
                                            searchedIssue: GithubSearchedIssue): GithubPullRequestDetailed {

    val links = searchedIssue.pullRequestLinks ?: throw IllegalStateException("Missing pull request links")
    val pullRequest = requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.get(links.url))

    if (!isCommitFetched(pullRequest.head.sha)) {
      git.fetch(repository, remote, emptyList(), "refs/pull/${pullRequest.number}/head:").throwOnError()
    }
    if (!isCommitFetched(pullRequest.head.sha)) throw IllegalStateException("Pull request head is not available after fetch")
    return pullRequest
  }

  private fun isCommitFetched(commitHash: String): Boolean {
    val result = git.getObjectType(repository, commitHash)
    return result.success() && result.outputAsJoinedString == "commit"
  }
}
