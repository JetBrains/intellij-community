// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import kotlin.properties.Delegates

internal class GHPRListLoader(
  progressManager: ProgressManager,
  requestExecutor: GithubApiRequestExecutor,
  repository: GHRepositoryCoordinates,
) : GHListLoaderBase<GHPullRequestShort>(progressManager) {

  var searchQuery by Delegates.observable<GHPRSearchQuery?>(null) { _, _, _ ->
    reset()
  }

  private val loader = SimpleGHGQLPagesLoader(requestExecutor, { p ->
    GHGQLRequests.PullRequest.search(repository.serverPath, buildQuery(repository.repositoryPath, searchQuery), p)
  })

  override fun canLoadMore() = !loading && loader.hasNext && error == null

  override fun doLoadMore(indicator: ProgressIndicator, update: Boolean) = loader.loadNext(indicator, update)

  override fun reset() {
    loader.reset()
    super.reset()
  }

  companion object {
    private fun buildQuery(repoPath: GHRepositoryPath, searchQuery: GHPRSearchQuery?): String {
      return GithubApiSearchQueryBuilder.searchQuery {
        qualifier("type", GithubIssueSearchType.pr.name)
        qualifier("repo", repoPath.toString())
        searchQuery?.buildApiSearchQuery(this)
      }
    }
  }
}
