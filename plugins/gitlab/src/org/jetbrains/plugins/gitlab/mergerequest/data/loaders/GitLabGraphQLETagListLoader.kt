// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data.loaders

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.async.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabGraphQLETagListLoader.PageInfo

fun <K, V> startGitLabGraphQLListLoaderIn(
  cs: CoroutineScope,
  extractKey: (V) -> K,

  requestReloadFlow: Flow<Unit>? = null,
  requestRefreshFlow: Flow<Unit>? = null,
  requestChangeFlow: Flow<Change<V>>? = null,

  shouldTryToLoadAll: Boolean = false,

  performRequest: suspend (cursor: String?) -> GraphQLConnectionDTO<V>?
): ReloadablePotentiallyInfiniteListLoader<V> {
  val loader = GitLabGraphQLETagListLoader(cs, extractKey, shouldTryToLoadAll, performRequest)

  cs.launchNow { requestReloadFlow?.collect { loader.reload() } }
  cs.launch { requestRefreshFlow?.collect { loader.refresh() } }
  cs.launch { requestChangeFlow?.collect { loader.update(it) } }

  return loader
}

private class GitLabGraphQLETagListLoader<K, V>(
  cs: CoroutineScope,

  extractKey: (V) -> K,
  shouldTryToLoadAll: Boolean = false,

  private val performRequest: suspend (cursor: String?) -> GraphQLConnectionDTO<V>?
) : PaginatedPotentiallyInfiniteListLoader<PageInfo, K, V>(cs, PageInfo(), extractKey, shouldTryToLoadAll) {
  /**
   *
   */
  data class PageInfo(
    val cursor: String? = null,
    val nextCursor: String? = null,
  ) : PaginatedPotentiallyInfiniteListLoader.PageInfo<PageInfo> {
    override fun createNextPageInfo(): PageInfo? =
      nextCursor?.let { PageInfo(it) }
  }

  override suspend fun performRequestAndProcess(
    pageInfo: PageInfo,
    f: (pageInfo: PageInfo?, results: List<V>?) -> Page<PageInfo, V>?
  ): Page<PageInfo, V>? {
    val results = performRequest(pageInfo.cursor)
    val nextCursor = results?.pageInfo?.endCursor

    return f(pageInfo.copy(nextCursor = nextCursor), results?.nodes)
  }
}