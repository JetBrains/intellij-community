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
import org.jetbrains.plugins.github.api.search.GithubIssueSearchQuery
import java.util.*

class GithubPullRequestsLoader(private val serverPath: GithubServerPath,
                               repoPath: GithubFullPath,
                               private val progressManager: ProgressManager,
                               private val requestExecutor: GithubApiRequestExecutor) : Disposable {
  private val LOG = logger<GithubPullRequestsLoader>()

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("GitHub PR loading breaker", 1)
  private var progressIndicator = EmptyProgressIndicator()
  private val query = GithubIssueSearchQuery(GithubIssueSearchQuery.Type.pr, null, repoPath)
  private var nextPageRequest: GithubApiRequest<GithubResponsePage<GithubSearchedIssue>>? = createInitialRequest()

  private val stateEventDispatcher = EventDispatcher.create(StateListener::class.java)

  private fun createInitialRequest() = GithubApiRequests.Search.Issues.get(serverPath, query)

  @CalledInAwt
  fun requestLoadMore() {
    LOG.debug("Requested more pull requests")
    val indicator = progressIndicator
    executor.execute {
      if (indicator.isCanceled) return@execute
      try {
        stateEventDispatcher.multicaster.loadingStarted()
        LOG.debug("Starting listeners notified")
        progressManager.runProcess({ loadMore(indicator) }, indicator)
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
  private fun loadMore(progressIndicator: ProgressIndicator) {
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

  fun addStateListener(listener: StateListener) = stateEventDispatcher.addListener(listener)

  fun removeStateListener(listener: StateListener) = stateEventDispatcher.removeListener(listener)

  override fun dispose() {
    progressIndicator.cancel()
  }

  interface StateListener : EventListener {
    fun loadingStarted() {}
    fun loadingStopped() {}
    fun moreDataLoaded(data: List<GithubSearchedIssue>, hasNext: Boolean) {}
    fun loadingErrorOccurred(error: Throwable) {}
  }
}