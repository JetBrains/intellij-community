// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Couple
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.util.EventDispatcher
import git4idea.GitCommit
import git4idea.commands.Git
import git4idea.history.GitLogUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.properties.Delegates

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
  : GithubPullRequestDataProvider, Disposable {
  private var detailsLoadingIndicator = NonReusableEmptyProgressIndicator()
  private var changesLoadingIndicator = NonReusableEmptyProgressIndicator()

  private val requestsChangesEventDispatcher = EventDispatcher.create(GithubPullRequestDataProvider.RequestsChangedListener::class.java)

  override var detailsRequest: CompletableFuture<GithubPullRequestDetailedWithHtml>
    by Delegates.observable(CompletableFuture()) { _, _, _ -> requestsChangesEventDispatcher.multicaster.detailsRequestChanged() }
  override var branchFetchRequest = CompletableFuture<Couple<String>>()
  override var logCommitsRequest: CompletableFuture<List<GitCommit>>
    by Delegates.observable(CompletableFuture()) { _, _, _ -> requestsChangesEventDispatcher.multicaster.commitsRequestChanged() }

  @CalledInAwt
  internal fun load() {
    requestDetails()
    requestCommits()
  }

  @CalledInAwt
  override fun reloadDetails() {
    requestDetails()
  }

  @CalledInAwt
  override fun reloadCommits() {
    requestCommits()
  }

  @CalledInAwt
  private fun requestDetails() {
    detailsLoadingIndicator.cancel()
    detailsLoadingIndicator = NonReusableEmptyProgressIndicator()

    val oldDetailsRequest = detailsRequest
    val newDetailsRequest = CompletableFuture<GithubPullRequestDetailedWithHtml>()
    newDetailsRequest.handleOnEdt { details, _ ->
      if (details == null) return@handleOnEdt
      if (oldDetailsRequest.isDone) {
        val oldDetails: GithubPullRequestDetailedWithHtml? = try {
          oldDetailsRequest.joinCancellable()
        }
        catch (e: Exception) {
          null
        }
        if (oldDetails == null
            || oldDetails.base.sha != details.base.sha
            || oldDetails.head.sha != details.head.sha) {
          reloadCommits()
        }
      }
    }
    progressManager.runProcessWithProgressAsynchronously(object : Task.Backgroundable(project, "Load Pull Request Details", true) {
      override fun run(indicator: ProgressIndicator) {
        loadDetails(newDetailsRequest, indicator)
      }
    }, detailsLoadingIndicator)
    detailsRequest = newDetailsRequest
  }

  private fun requestCommits() {
    changesLoadingIndicator.cancel()
    changesLoadingIndicator = NonReusableEmptyProgressIndicator()

    val detailsRequest = detailsRequest
    val newBranchFetchRequest = CompletableFuture<Couple<String>>()
    val newLogCommitsRequest = CompletableFuture<List<GitCommit>>()
    progressManager.runProcessWithProgressAsynchronously(object : Task.Backgroundable(project, "Load Pull Request Changes", true) {
      override fun run(indicator: ProgressIndicator) {
        fetchBranch(newBranchFetchRequest, detailsRequest, indicator)
        loadCommits(newLogCommitsRequest, newBranchFetchRequest, indicator)
      }
    }, changesLoadingIndicator)
    branchFetchRequest = newBranchFetchRequest
    logCommitsRequest = newLogCommitsRequest
  }

  @CalledInAwt
  override fun dispose() {
    detailsLoadingIndicator.cancel()
    changesLoadingIndicator.cancel()
  }

  @CalledInBackground
  private fun loadDetails(result: CompletableFuture<GithubPullRequestDetailedWithHtml>, indicator: ProgressIndicator) {
    runPartialTask(result, indicator) {
      requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.getHtml(serverPath, username, repositoryName, number))
    }
  }

  @CalledInBackground
  private fun fetchBranch(result: CompletableFuture<Couple<String>>,
                          detailsRequest: CompletableFuture<GithubPullRequestDetailedWithHtml>,
                          indicator: ProgressIndicator) =
    runPartialTask(result, indicator) {
      val details = detailsRequest.joinCancellable()
      git.fetch(repository, remote, emptyList(), "refs/pull/${details.number}/head:").throwOnError()
      if (!isCommitFetched(details.base.sha)) throw IllegalStateException("Pull request base is not available after fetch")
      if (!isCommitFetched(details.head.sha)) throw IllegalStateException("Pull request head is not available after fetch")
      Couple.of(details.base.sha, details.head.sha)
    }

  private fun isCommitFetched(commitHash: String): Boolean {
    val result = git.getObjectType(repository, commitHash)
    return result.success() && result.outputAsJoinedString == "commit"
  }

  @CalledInBackground
  private fun loadCommits(result: CompletableFuture<List<GitCommit>>,
                          fetchRequest: CompletableFuture<Couple<String>>,
                          indicator: ProgressIndicator) =
    runPartialTask(result, indicator) {
      val hashes = fetchRequest.joinCancellable()
      GitLogUtil.collectFullDetails(project, repository.root, "${hashes.first}..${hashes.second}")
    }

  @Throws(ProcessCanceledException::class)
  private fun <T> CompletableFuture<T>.joinCancellable(): T {
    try {
      return join()
    }
    catch (e: CancellationException) {
      throw ProcessCanceledException(e)
    }
    catch (e: CompletionException) {
      if (GithubAsyncUtil.isCancellation(e)) throw ProcessCanceledException(e)
      throw e.cause ?: e
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

  override fun addRequestsChangesListener(listener: GithubPullRequestDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.addListener(listener)

  override fun removeRequestsChangesListener(listener: GithubPullRequestDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.removeListener(listener)
}