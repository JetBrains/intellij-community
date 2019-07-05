// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.concurrency.JobScheduler
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQuery
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQueryHolder
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

internal class GHPRListLoaderImpl(progressManager: ProgressManager,
                                  private val requestExecutor: GithubApiRequestExecutor,
                                  private val serverPath: GithubServerPath,
                                  private val repoPath: GithubFullPath,
                                  listModel: CollectionListModel<GHPullRequestShort>,
                                  private val searchQueryHolder: GithubPullRequestSearchQueryHolder)
  : GHListLoaderBase<GHPullRequestShort>(progressManager, listModel), GHPRListLoader {

  private val loader = SimpleGHGQLPagesLoader(requestExecutor, { p ->
    GHGQLRequests.PullRequest.search(serverPath, buildQuery(searchQueryHolder.query), p)
  })

  private val outdatedStateEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var outdated: Boolean by Delegates.observable(false) { _, _, newValue ->
    if (newValue) sizeChecker.stop()
    outdatedStateEventDispatcher.multicaster.eventOccurred()
  }
  private val sizeChecker = ListChangesChecker()

  private var resetDisposable: Disposable

  init {
    requestExecutor.addListener(this) { reset() }
    searchQueryHolder.addQueryChangeListener(this) { reset() }

    Disposer.register(this, sizeChecker)

    resetDisposable = Disposer.newDisposable()
    Disposer.register(this, resetDisposable)
  }

  private fun buildQuery(searchQuery: GithubPullRequestSearchQuery?): String {
    return GithubApiSearchQueryBuilder.searchQuery {
      qualifier("type", GithubIssueSearchType.pr.name)
      qualifier("repo", repoPath.fullName)
      searchQuery?.buildApiSearchQuery(this)
    }
  }

  override val filterNotEmpty: Boolean
    get() = !searchQueryHolder.query.isEmpty()

  override fun resetFilter() {
    searchQueryHolder.query = GithubPullRequestSearchQuery.parseFromString("state:open")
  }

  override fun canLoadMore() = !loading && (loader.hasNext || error != null)

  override fun doLoadMore(indicator: ProgressIndicator): List<GHPullRequestShort>? = loader.loadNext(indicator)

  override fun handleResult(list: List<GHPullRequestShort>) {
    super.handleResult(list)
    sizeChecker.start()
  }

  override fun reset() {
    loader.reset()
    super.reset()
    listModel.removeAll()

    outdated = false
    sizeChecker.stop()

    Disposer.dispose(resetDisposable)
    resetDisposable = Disposer.newDisposable()
    Disposer.register(this, resetDisposable)

    loadMore()
  }

  override fun reloadData(request: CompletableFuture<out GHPullRequestShort>) {
    request.handleOnEdt(resetDisposable) { result, error ->
      if (error == null && result != null) updateData(result)
    }
  }

  private fun updateData(pullRequest: GHPullRequestShort) {
    val index = listModel.items.indexOfFirst { it.id == pullRequest.id }
    listModel.setElementAt(pullRequest, index)
  }

  override fun addOutdatedStateChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(outdatedStateEventDispatcher, disposable, listener)

  private inner class ListChangesChecker : Disposable {

    private var scheduler: ScheduledFuture<*>? = null
    private var progressIndicator: ProgressIndicator? = null

    @Volatile
    private var lastETag: String? = null
      set(value) {
        if (field != null && value != null && field != value) runInEdt { outdated = true }
        field = value
      }

    @CalledInAwt
    fun start() {
      if (scheduler == null) {
        val indicator = NonReusableEmptyProgressIndicator()
        progressIndicator = indicator
        scheduler = JobScheduler.getScheduler().scheduleWithFixedDelay({
                                                                         try {
                                                                           lastETag = loadListETag(indicator)
                                                                         }
                                                                         catch (e: Exception) {
                                                                           //ignore
                                                                         }
                                                                       }, 0, 1, TimeUnit.MINUTES)
      }
    }

    private fun loadListETag(indicator: ProgressIndicator): String? =
      progressManager.runProcess(Computable {
        requestExecutor.execute(GithubApiRequests.Repos.PullRequests.getListETag(serverPath, repoPath))
      }, indicator)

    @CalledInAwt
    fun stop() {
      scheduler?.cancel(true)
      scheduler = null
      progressIndicator?.cancel()
      progressIndicator = NonReusableEmptyProgressIndicator()
      lastETag = null
    }

    override fun dispose() {
      scheduler?.cancel(true)
      progressIndicator?.cancel()
    }
  }
}