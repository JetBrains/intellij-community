// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Couple
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import git4idea.GitCommit
import git4idea.commands.Git
import git4idea.history.GitLogUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

internal class GithubPullRequestDataProviderImpl(private val project: Project,
                                                 private val progressManager: ProgressManager,
                                                 private val git: Git,
                                                 private val requestExecutor: GithubApiRequestExecutor,
                                                 private val repository: GitRepository,
                                                 private val remote: GitRemote,
                                                 private val serverPath: GithubServerPath,
                                                 private val username: String,
                                                 private val repositoryName: String,
                                                 private val number: Long)
  : GithubPullRequestDataProvider {
  private val loadingIndicator = NonReusableEmptyProgressIndicator()

  override val detailsRequest = CompletableFuture<GithubPullRequestDetailedWithHtml>()
  override val branchFetchRequest = CompletableFuture<Couple<String>>()
  override val logCommitsRequest = CompletableFuture<List<GitCommit>>()
  override val changesRequest = CompletableFuture<List<Change>>()

  internal fun load() {
    progressManager.runProcessWithProgressAsynchronously(InitialLoadingTask(), loadingIndicator)
  }

  internal fun cancel() {
    loadingIndicator.cancel()
  }

  inner class InitialLoadingTask : Task.Backgroundable(project, "Load Pull Request Data", true) {
    override fun run(indicator: ProgressIndicator) {
      runPartialTask(detailsRequest, indicator) {
        requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.getHtml(serverPath, username, repositoryName, number))
      }

      runPartialTask(branchFetchRequest, indicator) {
        val details = getOrHandle(detailsRequest)
        git.fetch(repository, remote, emptyList(), "refs/pull/${details.number}/head:").throwOnError()
        if (!isCommitFetched(details.base.sha)) throw IllegalStateException("Pull request base is not available after fetch")
        if (!isCommitFetched(details.head.sha)) throw IllegalStateException("Pull request head is not available after fetch")
        Couple.of(details.base.sha, details.head.sha)
      }

      runPartialTask(logCommitsRequest, indicator) {
        val hashes = getOrHandle(branchFetchRequest)
        GitLogUtil.collectFullDetails(project, repository.root, "${hashes.first}..${hashes.second}")
      }

      runPartialTask(changesRequest, indicator) {
        val commits = getOrHandle(logCommitsRequest)
        CommittedChangesTreeBrowser.zipChanges(commits.reversed().flatMap { it.changes })
      }
    }

    private inline fun <T> runPartialTask(resultFuture: CompletableFuture<T>, indicator: ProgressIndicator, crossinline task: () -> T) {
      try {
        if (resultFuture.isCancelled) return
        indicator.checkCanceled()
        val result = task()
        resultFuture.complete(result)
      }
      catch (pce: ProcessCanceledException) {
        resultFuture.cancel(true)
      }
      catch (e: Exception) {
        resultFuture.completeExceptionally(e)
      }
    }

    @Throws(ProcessCanceledException::class)
    private fun <T> getOrHandle(future: CompletableFuture<T>): T {
      try {
        return future.join()
      }
      catch (e: CancellationException) {
        throw ProcessCanceledException(e)
      }
      catch (e: CompletionException) {
        if (GithubAsyncUtil.isCancellation(e)) throw ProcessCanceledException(e)
        throw e.cause ?: e
      }
    }

    private fun isCommitFetched(commitHash: String): Boolean {
      val result = git.getObjectType(repository, commitHash)
      return result.success() && result.outputAsJoinedString == "commit"
    }
  }
}