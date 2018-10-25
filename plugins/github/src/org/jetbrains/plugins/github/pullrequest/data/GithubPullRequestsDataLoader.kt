// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.util.EventDispatcher
import git4idea.GitCommit
import git4idea.commands.Git
import git4idea.history.GitLogUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

internal class GithubPullRequestsDataLoader(private val project: Project,
                                            private val progressManager: ProgressManager,
                                            private val git: Git,
                                            private val requestExecutor: GithubApiRequestExecutor,
                                            private val repository: GitRepository,
                                            private val remote: GitRemote) : Disposable {

  private var isDisposed = false
  private val cache = CacheBuilder.newBuilder()
    .removalListener<Long, DataTask> {
      it.value.cancel()
      invalidationEventDispatcher.multicaster.providerChanged(it.key)
    }
    .maximumSize(5)
    .build<Long, DataTask>()

  private val invalidationEventDispatcher = EventDispatcher.create(ProviderChangedListener::class.java)

  init {
    LowMemoryWatcher.register(Runnable { invalidateAllData() }, this)
  }

  @CalledInAwt
  fun invalidateData(number: Long) {
    cache.invalidate(number)
  }

  @CalledInAwt
  fun invalidateAllData() {
    cache.invalidateAll()
  }

  @CalledInAwt
  fun getDataProvider(githubSearchedIssue: GithubSearchedIssue): GithubPullRequestDataProvider {
    if (isDisposed) throw IllegalStateException("Already disposed")

    return cache.get(githubSearchedIssue.number) {
      val indicator = NonReusableEmptyProgressIndicator()
      val task = DataTask(githubSearchedIssue.pullRequestLinks!!.url, indicator)
      progressManager.runProcessWithProgressAsynchronously(task, indicator)
      task
    }
  }

  fun addProviderChangesListener(listener: ProviderChangedListener, disposable: Disposable) =
    invalidationEventDispatcher.addListener(listener, disposable)

  private inner class DataTask(private val url: String, private val progressIndicator: ProgressIndicator)
    : Task.Backgroundable(project, "Load Pull Request Data", true), GithubPullRequestDataProvider {

    override val detailsRequest = CompletableFuture<GithubPullRequestDetailedWithHtml>()
    override val branchFetchRequest = CompletableFuture<Couple<String>>()
    override val logCommitsRequest = CompletableFuture<List<GitCommit>>()
    override val changesRequest = CompletableFuture<List<Change>>()

    override fun run(indicator: ProgressIndicator) {
      runPartialTask(detailsRequest, indicator) {
        requestExecutor.execute(progressIndicator, GithubApiRequests.Repos.PullRequests.getHtml(url))
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

    internal fun cancel() {
      progressIndicator.cancel()
    }
  }

  override fun dispose() {
    invalidateAllData()
    isDisposed = true
  }

  interface ProviderChangedListener : EventListener {
    fun providerChanged(pullRequestNumber: Long)
  }
}