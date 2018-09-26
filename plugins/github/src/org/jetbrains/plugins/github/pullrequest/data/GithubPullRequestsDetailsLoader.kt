// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListSelectionModel
import java.util.*
import java.util.concurrent.Future
import kotlin.properties.Delegates

class GithubPullRequestsDetailsLoader(progressManager: ProgressManager,
                                      private val requestExecutor: GithubApiRequestExecutor,
                                      private val selectionModel: GithubPullRequestsListSelectionModel)
  : SingleWorkerProcessExecutor(progressManager, "GitHub PR info loading breaker"),
    GithubPullRequestsListSelectionModel.SelectionChangedListener {

  @set:CalledInAwt
  var request: Future<GithubPullRequestDetailedWithHtml>?
    by Delegates.observable<Future<GithubPullRequestDetailedWithHtml>?>(null) { _, _, _ ->
      changeEventDispatcher.multicaster.requestChanged()
    }
    private set

  private val changeEventDispatcher = EventDispatcher.create(RequestChangedListener::class.java)
  private val loadingEventDispatcher = EventDispatcher.create(LoadingListener::class.java)

  init {
    selectionModel.addChangesListener(this, this)
  }

  override fun selectionChanged() {
    cancelCurrentTasks()
    val selection = selectionModel.current
    if (selection == null) {
      request = null
      loadingEventDispatcher.multicaster.loaderCleared()
    }
    else {
      request = submit { indicator -> loadDetails(indicator, requestExecutor, selection) }
    }
  }

  @CalledInBackground
  private fun loadDetails(indicator: ProgressIndicator, requestExecutor: GithubApiRequestExecutor, searchedIssue: GithubSearchedIssue)
    : GithubPullRequestDetailedWithHtml {
    try {
      val links = searchedIssue.pullRequestLinks ?: throw IllegalStateException("Missing pull request links")
      val details = requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.getHtml(links.url))
      loadingEventDispatcher.multicaster.detailsLoaded(details)
      return details
    }
    catch (pce: ProcessCanceledException) {
      throw pce
    }
    catch (e: Exception) {
      loadingEventDispatcher.multicaster.errorOccurred(e)
      throw e
    }
  }

  fun addRequestChangeListener(listener: RequestChangedListener, disposable: Disposable) =
    changeEventDispatcher.addListener(listener, disposable)

  fun addLoadingListener(listener: LoadingListener, disposable: Disposable) =
    loadingEventDispatcher.addListener(listener, disposable)

  interface RequestChangedListener : EventListener {
    fun requestChanged()
  }

  interface LoadingListener : EventListener {
    fun detailsLoaded(details: GithubPullRequestDetailedWithHtml)
    fun errorOccurred(error: Throwable)
    fun loaderCleared()
  }
}
