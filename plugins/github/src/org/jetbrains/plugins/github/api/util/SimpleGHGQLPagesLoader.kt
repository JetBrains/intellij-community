// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.graphql.GHGQLPagedRequestResponse
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRequestPagination
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination

class SimpleGHGQLPagesLoader<T>(executor: GithubApiRequestExecutor,
                                requestProducer: (GHGQLRequestPagination) -> GithubApiRequest.Post<GHGQLPagedRequestResponse<T>>,
                                supportsTimestampUpdates: Boolean = false,
                                pageSize: Int = GithubRequestPagination.DEFAULT_PAGE_SIZE)
  : GHGQLPagesLoader<GHGQLPagedRequestResponse<T>, List<T>>(executor, requestProducer, supportsTimestampUpdates, pageSize) {

  fun loadAll(progressIndicator: ProgressIndicator): List<T> {
    val list = mutableListOf<T>()
    while (hasNext) {
      loadNext(progressIndicator)?.let { list.addAll(it) }
    }
    return list
  }

  override fun extractPageInfo(result: GHGQLPagedRequestResponse<T>) = result.pageInfo

  override fun extractResult(result: GHGQLPagedRequestResponse<T>) = result.nodes
}