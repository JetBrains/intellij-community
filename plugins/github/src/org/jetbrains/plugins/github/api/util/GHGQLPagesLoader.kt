// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.graphql.GHGQLPageInfo
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRequestPagination
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination

abstract class GHGQLPagesLoader<T, R>(private val executor: GithubApiRequestExecutor,
                                      private val requestProducer: (GHGQLRequestPagination) -> GithubApiRequest.Post<T>,
                                      private val pageSize: Int = GithubRequestPagination.DEFAULT_PAGE_SIZE) {
  private var cursor: String? = null

  var hasNext: Boolean = true
    private set

  fun loadNext(progressIndicator: ProgressIndicator): R? {
    if (!hasNext) return null

    val response = executor.execute(progressIndicator, requestProducer(
      GHGQLRequestPagination(cursor, pageSize)))
    val page = extractPageInfo(response)
    hasNext = page.hasNextPage
    cursor = page.endCursor
    return extractResult(response)
  }

  fun reset() {
    cursor = null
    hasNext = true
  }

  protected abstract fun extractPageInfo(result: T): GHGQLPageInfo
  protected abstract fun extractResult(result: T): R
}