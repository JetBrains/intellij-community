// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data.loaders

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.async.Change
import com.intellij.collaboration.async.PaginatedPotentiallyInfiniteListLoader
import com.intellij.collaboration.async.ReloadablePotentiallyInfiniteListLoader
import com.intellij.collaboration.async.launchNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabGraphQLListLoader.PageInfo

fun <K, V> startGitLabGraphQLListLoaderIn(
  cs: CoroutineScope,
  extractKey: (V) -> K,

  requestReloadFlow: Flow<Unit>? = null,
  requestRefreshFlow: Flow<Unit>? = null,
  requestChangeFlow: Flow<Change<V>>? = null,

  isReversed: Boolean = false,
  shouldTryToLoadAll: Boolean = false,

  performRequest: suspend (cursor: String?) -> GraphQLConnectionDTO<V>?
): ReloadablePotentiallyInfiniteListLoader<V> {
  val loader = GitLabGraphQLListLoader(cs, extractKey, shouldTryToLoadAll, isReversed, performRequest)

  cs.launchNow { requestReloadFlow?.collect { loader.reload() } }
  cs.launch { requestRefreshFlow?.collect { loader.refresh() } }
  cs.launch { requestChangeFlow?.collect { loader.update(it) } }

  return loader
}

private class GitLabGraphQLListLoader<K, V>(
  cs: CoroutineScope,

  extractKey: (V) -> K,
  shouldTryToLoadAll: Boolean = false,

  private val isReversed: Boolean = false,

  private val performRequest: suspend (cursor: String?) -> GraphQLConnectionDTO<V>?
) : PaginatedPotentiallyInfiniteListLoader<PageInfo, K, V>(PageInfo(), extractKey, shouldTryToLoadAll) {
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
    val nextCursor = if (isReversed) results?.pageInfo?.startCursor else results?.pageInfo?.endCursor

    return f(pageInfo.copy(nextCursor = nextCursor), results?.nodes)
  }
}