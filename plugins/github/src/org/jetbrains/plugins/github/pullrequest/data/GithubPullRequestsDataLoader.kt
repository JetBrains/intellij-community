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
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

//TODO: cancel loading in removed providers
class GithubPullRequestsDataLoader(private val project: Project,
                                   private val progressManager: ProgressManager,
                                   private val git: Git,
                                   private val requestExecutor: GithubApiRequestExecutor,
                                   private val repository: GitRepository,
                                   private val remote: GitRemote) : Disposable {

  private val progressIndicator = NonReusableEmptyProgressIndicator()
  private val cache = CacheBuilder.newBuilder()
    .removalListener<Long, DataProvider> { invalidationEventDispatcher.multicaster.providerChanged(it.key) }
    .maximumSize(5)
    .build<Long, DataProvider>()

  private val invalidationEventDispatcher = EventDispatcher.create(ProviderChangedListener::class.java)

  init {
    LowMemoryWatcher.register(Runnable { cache.invalidateAll() }, this)
  }

  @CalledInAwt
  fun invalidateData(number: Long) {
    cache.invalidate(number)
  }

  @CalledInAwt
  fun getDataProvider(githubSearchedIssue: GithubSearchedIssue): DataProvider {
    return cache.get(githubSearchedIssue.number) {
      val task = DataTask(githubSearchedIssue.pullRequestLinks!!.url)
      progressManager.runProcessWithProgressAsynchronously(task, progressIndicator)
      task
    }
  }

  fun addProviderChangesListener(listener: ProviderChangedListener, disposable: Disposable) =
    invalidationEventDispatcher.addListener(listener, disposable)

  private inner class DataTask(private val url: String)
    : Task.Backgroundable(project, "Load Pull Request Data", true), DataProvider {

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
        if (!isCommitFetched(details.head.sha)) {
          git.fetch(repository, remote, emptyList(), "refs/pull/${details.number}/head:").throwOnError()
        }
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
        return future.get()
      }
      catch (e: CancellationException) {
        throw ProcessCanceledException(e)
      }
      catch (e: InterruptedException) {
        throw ProcessCanceledException(e)
      }
      catch (e: ExecutionException) {
        throw e.cause ?: e
      }
    }

    private fun isCommitFetched(commitHash: String): Boolean {
      val result = git.getObjectType(repository, commitHash)
      return result.success() && result.outputAsJoinedString == "commit"
    }
  }

  override fun dispose() {
    progressIndicator.cancel()
  }

  interface DataProvider {
    val detailsRequest: CompletableFuture<GithubPullRequestDetailedWithHtml>
    val branchFetchRequest: CompletableFuture<Couple<String>>
    val logCommitsRequest: CompletableFuture<List<GitCommit>>
    val changesRequest: CompletableFuture<List<Change>>
  }

  interface ProviderChangedListener : EventListener {
    fun providerChanged(pullRequestNumber: Long)
  }
}