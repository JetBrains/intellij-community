// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GithubResponsePage
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.api.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQuery
import java.util.*

class GithubPullRequestsLoader(private val progressManager: ProgressManager,
                               private val requestExecutorHolder: GithubApiRequestExecutorManager.ManagedHolder,
                               private val serverPath: GithubServerPath,
                               private val repoPath: GithubFullPath)
  : Disposable, GithubApiRequestExecutorManager.ExecutorChangeListener {
  private val LOG = logger<GithubPullRequestsLoader>()

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("GitHub PR loading breaker", 1)
  private var progressIndicator = EmptyProgressIndicator()
  private var query: String = buildQuery(null)
  private var nextPageRequest: GithubApiRequest<GithubResponsePage<GithubSearchedIssue>>? = createInitialRequest()
  private var isDisposed = false

  private val stateEventDispatcher = EventDispatcher.create(StateListener::class.java)

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
    if (isDisposed) return
    LOG.debug("Requested more pull requests")
    val indicator = progressIndicator
    val requestExecutor = requestExecutorHolder.executor
    executor.execute {
      if (indicator.isCanceled) return@execute
      try {
        stateEventDispatcher.multicaster.loadingStarted()
        LOG.debug("Starting listeners notified")
        progressManager.runProcess({ loadMore(requestExecutor, indicator) }, indicator)
      }
      catch (pce: ProcessCanceledException) {
        // ignore
      }
      finally {
        stateEventDispatcher.multicaster.loadingStopped()
        LOG.debug("Stopping listeners notified")
      }
    }
  }

  @CalledInBackground
  private fun loadMore(requestExecutor: GithubApiRequestExecutor, progressIndicator: ProgressIndicator) {
    try {
      LOG.debug("Loading pull requests")
      val request = nextPageRequest
      if (request == null) {
        LOG.debug("Nothing to load")
        return
      }
      val loadedPage = requestExecutor.execute(progressIndicator, request)
      nextPageRequest = loadedPage.nextLink?.let { GithubApiRequests.Search.Issues.get(it) }
      if (!progressIndicator.isCanceled) {
        stateEventDispatcher.multicaster.moreDataLoaded(loadedPage.items, loadedPage.nextLink != null)
        LOG.debug("Data listeners notified")
      }
    }
    catch (pce: ProcessCanceledException) {
      //ignore
    }
    catch (error: Throwable) {
      if (!progressIndicator.isCanceled) {
        stateEventDispatcher.multicaster.loadingErrorOccurred(error)
        LOG.debug("Error listeners notified")
      }
    }
  }

  override fun executorChanged() {
    reset()
  }

  @CalledInAwt
  fun reset() {
    if (isDisposed) return
    progressIndicator.cancel()
    progressIndicator = object : EmptyProgressIndicator() {
      override fun start() {
        checkCanceled()
        super.start()
      }
    }
    executor.execute {
      nextPageRequest = createInitialRequest()
      stateEventDispatcher.multicaster.loaderReset()
      LOG.debug("Reset listeners notified")
    }
  }

  fun addStateListener(listener: StateListener, disposable: Disposable) = stateEventDispatcher.addListener(listener, disposable)

  override fun dispose() {
    progressIndicator.cancel()
    isDisposed = true
  }

  interface StateListener : EventListener {
    fun loadingStarted() {}
    fun loadingStopped() {}
    fun moreDataLoaded(data: List<GithubSearchedIssue>, hasNext: Boolean) {}
    fun loadingErrorOccurred(error: Throwable) {}
    fun loaderReset() {}
  }
}