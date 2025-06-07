// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data.loaders

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.util.LinkHttpHeaderValue
import com.intellij.collaboration.async.Change
import com.intellij.collaboration.async.PaginatedPotentiallyInfiniteListLoader
import com.intellij.collaboration.async.ReloadablePotentiallyInfiniteListLoader
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.util.URIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabRestETagListLoader.PageInfo
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpResponse

fun <K, V> startGitLabRestETagListLoaderIn(
  cs: CoroutineScope,
  initialURI: URI,
  extractKey: (V) -> K,

  requestReloadFlow: Flow<Unit>? = null,
  requestRefreshFlow: Flow<Unit>? = null,
  requestChangeFlow: Flow<Change<V>>? = null,

  shouldTryToLoadAll: Boolean = false,

  performRequest: suspend (uri: URI, eTag: String?) -> HttpResponse<out List<V>?>
): ReloadablePotentiallyInfiniteListLoader<V> {
  val loader = GitLabRestETagListLoader(cs, initialURI, extractKey, shouldTryToLoadAll, performRequest)

  cs.launchNow { requestReloadFlow?.collect { loader.reload() } }
  cs.launch { requestRefreshFlow?.collect { loader.refresh() } }
  cs.launch { requestChangeFlow?.collect { loader.update(it) } }

  return loader
}

private class GitLabRestETagListLoader<K, V>(
  cs: CoroutineScope,
  private val initialURI: URI,
  extractKey: (V) -> K,

  shouldTryToLoadAll: Boolean = false,

  private val performRequest: suspend (uri: URI, eTag: String?) -> HttpResponse<out List<V>?>
) : PaginatedPotentiallyInfiniteListLoader<PageInfo, K, V>(PageInfo(initialURI), extractKey, shouldTryToLoadAll) {
  companion object {
    private const val ETAG_HEADER = "ETag"
  }

  data class PageInfo(
    val link: URI,
    val nextLink: URI? = null,
    val etag: String? = null
  ) : PaginatedPotentiallyInfiniteListLoader.PageInfo<PageInfo> {
    override fun createNextPageInfo(): PageInfo? =
      nextLink?.let { PageInfo(it) }
  }

  override suspend fun performRequestAndProcess(
    pageInfo: PageInfo,
    f: (pageInfo: PageInfo?, results: List<V>?) -> Page<PageInfo, V>?
  ): Page<PageInfo, V>? {
    val response = try {
      performRequest(pageInfo.link, pageInfo.etag)
    }
    catch (e: HttpStatusErrorException) {
      // Only make sure that 404's are dealt with appropriately, all other
      // status code errors can propagate.
      if (e.statusCode == 404) {
        return null
      }
      throw e
    }

    val isNotModified = response.statusCode() == HttpURLConnection.HTTP_NOT_MODIFIED
    val results = if (isNotModified) null else (response.body() ?: return null)

    val newEtag = response.headers().firstValue(ETAG_HEADER).orElse(null)
    val nextLink = response.headers().firstValue(LinkHttpHeaderValue.HEADER_NAME).orElse(null)
      ?.let(LinkHttpHeaderValue::parse)
      ?.let(LinkHttpHeaderValue::nextLink)
      ?.let {
        URIUtil.createUriWithCustomScheme(it, initialURI.scheme)
      }

    return f(pageInfo.copy(nextLink = nextLink, etag = newEtag), results)
  }
}
