// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubResponsePage
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.api.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQuery
import java.util.concurrent.CompletableFuture

internal class GithubPullRequestsLoader(private val progressManager: ProgressManager,
                                        private val requestExecutor: GithubApiRequestExecutor,
                                        private val serverPath: GithubServerPath,
                                        private val repoPath: GithubFullPath) : Disposable {
  private var initialRequest = GithubApiRequests.Search.Issues.get(serverPath, buildQuery(null))
  private var lastFuture = CompletableFuture.completedFuture(GithubResponsePage.empty<GithubSearchedIssue>(initialRequest.url))

  @CalledInAwt
  fun setSearchQuery(searchQuery: GithubPullRequestSearchQuery?) {
    initialRequest = GithubApiRequests.Search.Issues.get(serverPath, buildQuery(searchQuery))
  }

  private fun buildQuery(searchQuery: GithubPullRequestSearchQuery?): String {
    return GithubApiSearchQueryBuilder.searchQuery {
      qualifier("type", GithubIssueSearchType.pr.name)
      qualifier("repo", repoPath.fullName)
      searchQuery?.buildApiSearchQuery(this)
    }
  }

  @CalledInAwt
  fun requestLoadMore(indicator: ProgressIndicator): CompletableFuture<GithubResponsePage<GithubSearchedIssue>> {
    lastFuture = lastFuture.thenApplyAsync {
      it.nextLink?.let { url ->
        progressManager.runProcess(Computable { requestExecutor.execute(indicator, GithubApiRequests.Search.Issues.get(url)) }, indicator)
      } ?: GithubResponsePage.empty()
    }
    return lastFuture
  }

  @CalledInAwt
  fun reset() {
    lastFuture = lastFuture.handle { _, _ ->
      GithubResponsePage.empty<GithubSearchedIssue>(initialRequest.url)
    }
  }

  override fun dispose() {
    reset()
  }
}