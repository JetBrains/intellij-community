// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GithubResponsePage
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.api.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQuery
import java.util.*

class GithubPullRequestsLoader(progressManager: ProgressManager,
                               private val requestExecutorHolder: GithubApiRequestExecutorManager.ManagedHolder,
                               private val serverPath: GithubServerPath,
                               private val repoPath: GithubFullPath)
  : SingleWorkerProcessExecutor(progressManager, "GitHub PR loading breaker"), GithubApiRequestExecutorManager.ExecutorChangeListener {

  private var query: String = buildQuery(null)
  private var nextPageRequest: GithubApiRequest<GithubResponsePage<GithubSearchedIssue>>? = createInitialRequest()

  private val stateEventDispatcher = EventDispatcher.create(PullRequestsLoadingListener::class.java)

  init {
    requestExecutorHolder.addListener(this, this)
  }

  private fun createInitialRequest() = GithubApiRequests.Search.Issues.get(serverPath, query)

  @CalledInAwt
  fun setSearchQuery(searchQuery: GithubPullRequestSearchQuery?) {
    query = buildQuery(searchQuery)
  }

  private fun buildQuery(searchQuery: GithubPullRequestSearchQuery?): String {
    return GithubApiSearchQueryBuilder.searchQuery {
      qualifier("type", GithubIssueSearchType.pr.name)
      qualifier("repo", repoPath.fullName)
      searchQuery?.buildApiSearchQuery(this)
    }
  }

  @CalledInAwt
  fun requestLoadMore() {
    val requestExecutor = requestExecutorHolder.executor
    submit { indicator -> loadMore(requestExecutor, indicator) }
  }

  @CalledInBackground
  private fun loadMore(requestExecutor: GithubApiRequestExecutor, progressIndicator: ProgressIndicator) {
    try {
      val request = nextPageRequest
      if (request == null) {
        runInEdt {
          if (!progressIndicator.isCanceled) stateEventDispatcher.multicaster.moreDataLoaded(emptyList(), false)
        }
        return
      }
      val loadedPage = requestExecutor.execute(progressIndicator, request)
      nextPageRequest = loadedPage.nextLink?.let { GithubApiRequests.Search.Issues.get(it) }
      runInEdt {
        if (!progressIndicator.isCanceled) stateEventDispatcher.multicaster.moreDataLoaded(loadedPage.items, loadedPage.nextLink != null)
      }
    }
    catch (pce: ProcessCanceledException) {
      //ignore
    }
    catch (error: Throwable) {
      runInEdt { if (!progressIndicator.isCanceled) stateEventDispatcher.multicaster.loadingErrorOccurred(error) }
    }
  }

  override fun executorChanged() {
    reset()
  }

  @CalledInAwt
  fun reset() {
    cancelCurrentTasks()
    submit {
      nextPageRequest = createInitialRequest()
      runInEdt { stateEventDispatcher.multicaster.loaderReset() }
    }
  }

  fun addLoadingListener(listener: PullRequestsLoadingListener, disposable: Disposable) = stateEventDispatcher.addListener(listener,
                                                                                                                           disposable)

  interface PullRequestsLoadingListener : EventListener {
    fun moreDataLoaded(data: List<GithubSearchedIssue>, hasNext: Boolean) {}
    fun loadingErrorOccurred(error: Throwable) {}
    fun loaderReset() {}
  }
}