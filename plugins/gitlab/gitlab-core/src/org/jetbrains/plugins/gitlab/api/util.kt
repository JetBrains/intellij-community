// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.util.LinkHttpHeaderValue
import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.SequenceComputer
import com.intellij.collaboration.util.SequenceItem
import com.intellij.collaboration.util.URIUtil
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabCredentialsRefreshException
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabMissingCredentialsException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpResponse
import kotlin.jvm.optionals.getOrNull

object GitLabApiUtil {
  fun isAuthorizationError(exception: Throwable): Boolean {
    return exception is GitLabCredentialsRefreshException ||
           exception is GitLabMissingCredentialsException ||
           isInvalidCredentialsError(exception)
  }

  fun isInvalidCredentialsError(exception: Throwable): Boolean {
    return exception is HttpStatusErrorException && exception.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED
  }

  /**
   * A [ComputableSequence] over a `Link`-paginated REST resource: each item is one page, loaded lazily by
   * following the `next` links to the last page. A "page" is whatever [loadPage] turns the response body into —
   * a `List` of items, a wrapper object, etc.
   *
   * The sequence keeps an `ETag` cache of pages keyed by page URI, shared across iterations: computing it again
   * (a fresh [ComputableSequence.getComputer]) re-requests each page conditionally, and an unchanged page
   * (`304 Not Modified`) is served from the cache instead of being re-downloaded. The cached entry also holds the
   * page's `next` link, which is reused on a `304`: a `304` is not required to echo the `Link` header
   * (RFC 9110 §15.4.5), so relying on the response to carry it would truncate pagination on servers that omit it.
   * A shrink is therefore detected when a page's content actually changes (a `200` whose `next` link is gone),
   * and entries for pages that no longer exist are evicted once the final page is reached.
   *
   * The `next` link is used as-is; if the server may hand back links with a different scheme (e.g. behind a
   * reverse proxy), [loadPage] is responsible for normalizing the request URI.
   *
   * NB: the returned sequence is NOT thread-safe. A computer must be iterated from a single coroutine at a time,
   * and its computers must not be iterated in parallel — they share the page cache.
   */
  @ApiStatus.Internal
  fun <P : Any> etagCachingLinkedPagesSequence(
    initialUri: URI,
    loadPage: suspend (uri: URI, eTag: String?) -> HttpResponse<out P?>,
  ): ComputableSequence<SequenceItem<P>> {
    val cache = mutableMapOf<URI, CachedPage<P>>()

    return ComputableSequence {
      val loadedURIs = mutableSetOf<URI>()
      SequenceComputer.byPointer(initialUri) { uri ->
        val cached = cache[uri]

        val response = loadPage(uri, cached?.eTag)
        val notModified = response.statusCode() == HttpURLConnection.HTTP_NOT_MODIFIED

        val page: P
        val nextUri: URI?
        if (cached != null && notModified) {
          // 304: the page content is unchanged, so serve it from the cache. For the next link, prefer whatever the
          // server echoed on the 304 (so a shrink it reports — a `Link` header without a `next` — is honored), and
          // fall back to the cached link only when the 304 carried no `Link` header at all: RFC 9110 §15.4.5 permits
          // omitting it, and re-reading an absent header would truncate pagination. The one case this cannot resolve
          // is a shrink hidden behind a `Link`-less 304 — indistinguishable from "unchanged, header not repeated".
          page = cached.page
          nextUri = if (response.hasLinkHeader()) response.nextPageLink(initialUri.scheme) else cached.nextUri
        }
        else {
          page = response.body() ?: error("Unexpected empty response for $uri")
          nextUri = response.nextPageLink(initialUri.scheme)
          val newETag = response.headers().firstValue(HttpClientUtil.ETAG_HEADER).getOrNull()
          if (newETag != null) {
            cache[uri] = CachedPage(newETag, page, nextUri)
          }
          else {
            cache.remove(uri)
          }
        }

        loadedURIs.add(uri)
        if (nextUri == null) {
          // Last page reached: forget pages that are no longer part of the resource.
          cache.keys.retainAll(loadedURIs)
        }

        page to nextUri
      }
    }
  }

  private fun HttpResponse<*>.nextPageLink(scheme: String): URI? =
    headers().firstValue(LinkHttpHeaderValue.HEADER_NAME).orElse(null)
      ?.let(LinkHttpHeaderValue::parse)
      ?.let(LinkHttpHeaderValue::nextLink)
      // GitLab may hand back next-page links with a different (proxy) scheme; keep the source scheme.
      ?.let { URIUtil.createUriWithCustomScheme(it, scheme) }

  private fun HttpResponse<*>.hasLinkHeader(): Boolean =
    headers().firstValue(LinkHttpHeaderValue.HEADER_NAME).orElse(null) != null
}

fun GitLabApi.Rest.projectApiUrl(projectId: String): URI = server.projectApiUri(URLEncoder.encode(projectId, Charsets.UTF_8))

fun GitLabServerPath.projectApiUri(projectId: String): URI = restApiUri
  .resolveRelative("projects/")
  .resolveRelative("$projectId/")

private class CachedPage<P>(val eTag: String, val page: P, val nextUri: URI?)
