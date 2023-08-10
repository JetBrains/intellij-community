// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination

class SimpleGHGQLPagesLoader<T>(executor: GithubApiRequestExecutor,
                                requestProducer: (GraphQLRequestPagination) -> GithubApiRequest.Post<GraphQLPagedResponseDataDTO<T>>,
                                supportsTimestampUpdates: Boolean = false,
                                pageSize: Int = GithubRequestPagination.DEFAULT_PAGE_SIZE)
  : GHGQLPagesLoader<GraphQLPagedResponseDataDTO<T>, List<T>>(executor, requestProducer, supportsTimestampUpdates, pageSize) {

  fun loadAll(progressIndicator: ProgressIndicator): List<T> {
    val list = mutableListOf<T>()
    while (hasNext) {
      loadNext(progressIndicator)?.let { list.addAll(it) }
    }
    return list
  }

  override fun extractPageInfo(result: GraphQLPagedResponseDataDTO<T>) = result.pageInfo

  override fun extractResult(result: GraphQLPagedResponseDataDTO<T>) = result.nodes
}