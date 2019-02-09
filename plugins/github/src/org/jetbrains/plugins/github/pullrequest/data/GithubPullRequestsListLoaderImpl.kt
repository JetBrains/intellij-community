// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.ui.CollectionListModel
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubResponsePage
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.api.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQuery
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQueryHolder
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import javax.swing.ListModel
import javax.swing.event.ListDataListener
import kotlin.properties.Delegates

internal class GithubPullRequestsListLoaderImpl(private val progressManager: ProgressManager,
                                                private val requestExecutor: GithubApiRequestExecutor,
                                                private val serverPath: GithubServerPath,
                                                private val repoPath: GithubFullPath)
  : GithubPullRequestsListLoader, ListModel<GithubSearchedIssue>, GithubPullRequestSearchQueryHolder, Disposable {

  private var initialRequest = GithubApiRequests.Search.Issues.get(serverPath, buildQuery(null))
  private var lastFuture = CompletableFuture.completedFuture(GithubResponsePage.empty<GithubSearchedIssue>(initialRequest.url))
  private var progressIndicator = NonReusableEmptyProgressIndicator()

  override var loading: Boolean by Delegates.observable(false) { _, _, _ ->
    loadingStateChangeEventDispatcher.multicaster.eventOccurred()
  }

  private val listModelDelegate = CollectionListModel<GithubSearchedIssue>()

  override var error: Throwable? by Delegates.observable<Throwable?>(null) { _, _, _ ->
    errorChangeEventDispatcher.multicaster.eventOccurred()
  }
  private var hasNext = true

  override var searchQuery: GithubPullRequestSearchQuery
    by Delegates.observable(GithubPullRequestSearchQuery(emptyList())) { _, _, _ ->
      initialRequest = GithubApiRequests.Search.Issues.get(serverPath, buildQuery(searchQuery))
      reset()
    }

  private val loadingStateChangeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  private val errorChangeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  init {
    requestExecutor.addListener(this) { reset() }
  }

  private fun buildQuery(searchQuery: GithubPullRequestSearchQuery?): String {
    return GithubApiSearchQueryBuilder.searchQuery {
      qualifier("type", GithubIssueSearchType.pr.name)
      qualifier("repo", repoPath.fullName)
      searchQuery?.buildApiSearchQuery(this)
    }
  }

  override fun canLoadMore() = !loading && (hasNext || error != null)

  override fun loadMore() {
    val indicator = progressIndicator
    if (canLoadMore()) {
      loading = true
      requestLoadMore(indicator).handleOnEdt { responsePage, error ->
        if (indicator.isCanceled) return@handleOnEdt
        when {
          error != null && !GithubAsyncUtil.isCancellation(error) -> {
            this.error = error
          }
          responsePage != null -> {
            listModelDelegate.add(responsePage.items)
            hasNext = responsePage.hasNext
          }
        }
        loading = false
      }
    }
  }

  private fun requestLoadMore(indicator: ProgressIndicator): CompletableFuture<GithubResponsePage<GithubSearchedIssue>> {
    lastFuture = lastFuture.thenApplyAsync {
      it.nextLink?.let { url ->
        progressManager.runProcess(Computable { requestExecutor.execute(indicator, GithubApiRequests.Search.Issues.get(url)) }, indicator)
      } ?: GithubResponsePage.empty()
    }
    return lastFuture
  }

  override fun getElementAt(index: Int): GithubSearchedIssue = listModelDelegate.getElementAt(index)
  override fun getSize(): Int = listModelDelegate.size

  override fun reset() {
    lastFuture = lastFuture.handle { _, _ ->
      GithubResponsePage.empty<GithubSearchedIssue>(initialRequest.url)
    }

    progressIndicator.cancel()
    progressIndicator = NonReusableEmptyProgressIndicator()
    error = null
    hasNext = true
    loading = false

    listModelDelegate.removeAll()
  }

  override fun addListDataListener(l: ListDataListener) = listModelDelegate.addListDataListener(l)
  override fun removeListDataListener(l: ListDataListener) = listModelDelegate.removeListDataListener(l)

  override fun addLoadingStateChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(loadingStateChangeEventDispatcher, disposable, listener)

  override fun addErrorChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(errorChangeEventDispatcher, disposable, listener)

  override fun dispose() = progressIndicator.cancel()
}