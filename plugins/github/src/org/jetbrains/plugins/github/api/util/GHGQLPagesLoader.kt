// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination
import java.util.*
import java.util.concurrent.atomic.AtomicReference

abstract class GHGQLPagesLoader<T, R>(private val executor: GithubApiRequestExecutor,
                                      private val requestProducer: (GraphQLRequestPagination) -> GithubApiRequest.Post<T>,
                                      private val supportsTimestampUpdates: Boolean = false,
                                      private val pageSize: Int = GithubRequestPagination.DEFAULT_PAGE_SIZE) {

  private val iterationDataRef = AtomicReference(IterationData(true))

  val hasNext: Boolean
    get() = iterationDataRef.get().hasNext

  @Synchronized
  fun loadNext(progressIndicator: ProgressIndicator, update: Boolean = false): R? {
    val iterationData = iterationDataRef.get()

    val pagination: GraphQLRequestPagination =
      if (update) {
        if (hasNext || !supportsTimestampUpdates) return null
        GraphQLRequestPagination(iterationData.timestamp, pageSize)
      }
      else {
        if (!hasNext) return null
        GraphQLRequestPagination(iterationData.cursor, pageSize)
      }

    val executionDate = Date()
    val response = executor.execute(progressIndicator, requestProducer(pagination))
    val page = extractPageInfo(response)
    iterationDataRef.compareAndSet(iterationData, IterationData(page, executionDate))

    return extractResult(response)
  }

  fun reset() {
    iterationDataRef.set(IterationData(true))
  }

  protected abstract fun extractPageInfo(result: T): GraphQLCursorPageInfoDTO
  protected abstract fun extractResult(result: T): R

  private class IterationData(val hasNext: Boolean, val timestamp: Date? = null, val cursor: String? = null) {
    constructor(page: GraphQLCursorPageInfoDTO, timestamp: Date) : this(page.hasNextPage, timestamp, page.endCursor)
  }
}