// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.graphql.GHGQLPageInfo
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRequestPagination
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination
import java.util.concurrent.atomic.AtomicReference

abstract class GHGQLPagesLoader<T, R>(private val executor: GithubApiRequestExecutor,
                                      private val requestProducer: (GHGQLRequestPagination) -> GithubApiRequest.Post<T>,
                                      private val pageSize: Int = GithubRequestPagination.DEFAULT_PAGE_SIZE) {

  private val iterationDataRef = AtomicReference(IterationData(true))

  val hasNext: Boolean
    get() = iterationDataRef.get().hasNext

  @Synchronized
  fun loadNext(progressIndicator: ProgressIndicator): R? {
    val iterationData = iterationDataRef.get()
    if (!iterationData.hasNext) return null

    val response = executor.execute(progressIndicator, requestProducer(GHGQLRequestPagination(iterationData.cursor, pageSize)))
    val page = extractPageInfo(response)
    iterationDataRef.compareAndSet(iterationData, IterationData(page))

    return extractResult(response)
  }

  fun reset() {
    iterationDataRef.set(IterationData(true))
  }

  protected abstract fun extractPageInfo(result: T): GHGQLPageInfo
  protected abstract fun extractResult(result: T): R

  private class IterationData(val hasNext: Boolean, val cursor: String? = null) {
    constructor(page: GHGQLPageInfo) : this(page.hasNextPage, page.endCursor)
  }
}