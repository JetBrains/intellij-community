// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.util.LinkHttpHeaderValue
import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.SequenceComputer.ComputationOutcome
import com.intellij.collaboration.util.SequenceItem
import com.intellij.collaboration.util.toList
import com.intellij.testFramework.common.timeoutRunBlocking
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpHeaders
import java.net.http.HttpResponse

internal class GitLabEtagCachingLinkedPagesSequenceTest {

  private val baseUri: URI = URI.create("https://gitlab.test/api/v4/projects/1/discussions")
  private val page2Uri = "https://gitlab.test/api/v4/projects/1/discussions?page=2"

  @Test
  fun `emits a single page`() = timeoutRunBlocking {
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { _, _ -> page(listOf(1, 2, 3)) }

    assertEquals(listOf(listOf(1, 2, 3)), seq.pages())
  }

  @Test
  fun `follows next links and emits every page`() = timeoutRunBlocking {
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { uri: URI, _: String? ->
      if (uri == baseUri) page(listOf(1, 2), nextLink = page2Uri) else page(listOf(3, 4))
    }

    assertEquals(listOf(listOf(1, 2), listOf(3, 4)), seq.pages())
  }

  @Test
  fun `the last page is flagged as last without an extra request`() = timeoutRunBlocking {
    var requests = 0
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { uri: URI, _: String? ->
      requests++
      if (uri == baseUri) page(listOf(1, 2), nextLink = page2Uri) else page(listOf(3, 4))
    }

    val computer = seq.getComputer()
    assertEquals(ComputationOutcome.Item(SequenceItem(listOf(1, 2), isLast = false)), computer.computeNext())
    assertEquals(ComputationOutcome.Item(SequenceItem(listOf(3, 4), isLast = true)), computer.computeNext())
    assertEquals(2, requests) // the last page carried no next link, so its `isLast` was known without an extra request
    assertEquals(ComputationOutcome.Done, computer.computeNext())
    assertEquals(2, requests) // reaching `Done` did not trigger another request either
  }

  @Test
  fun `re-iteration re-requests with the cached ETag and serves the cached page on 304`() = timeoutRunBlocking {
    val sentETags = mutableListOf<String?>()
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { _: URI, eTag: String? ->
      sentETags += eTag
      if (eTag == null) page(listOf(1, 2), eTag = "e1") else notModified()
    }

    assertEquals(listOf(listOf(1, 2)), seq.pages()) // initial load
    assertEquals(listOf(listOf(1, 2)), seq.pages()) // refresh: 304 serves the cached page
    assertEquals(listOf(null, "e1"), sentETags)
  }

  @Test
  fun `updates the cache when the page changes`() = timeoutRunBlocking {
    val sentETags = mutableListOf<String?>()
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { _: URI, eTag: String? ->
      sentETags += eTag
      when (eTag) {
        null -> page(listOf(1), eTag = "e1")
        "e1" -> page(listOf(1, 2), eTag = "e2") // changed since the previous ETag
        else -> notModified()
      }
    }

    assertEquals(listOf(listOf(1)), seq.pages())
    assertEquals(listOf(listOf(1, 2)), seq.pages())
    assertEquals(listOf(listOf(1, 2)), seq.pages()) // now validated against the updated ETag
    assertEquals(listOf(null, "e1", "e2"), sentETags)
  }

  @Test
  fun `keeps the cached entry when a 304 does not echo the ETag`() = timeoutRunBlocking {
    val sentETags = mutableListOf<String?>()
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { _: URI, eTag: String? ->
      sentETags += eTag
      if (eTag == null) page(listOf(1), eTag = "e1") else notModified() // 304 carries no ETag header
    }

    seq.toList()
    seq.toList()
    seq.toList()
    // the entry survives the ETag-less 304s, so every refresh keeps validating with "e1"
    assertEquals(listOf(null, "e1", "e1"), sentETags)
  }

  @Test
  fun `drops the cache entry when the response carries no ETag`() = timeoutRunBlocking {
    val sentETags = mutableListOf<String?>()
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { _: URI, eTag: String? ->
      sentETags += eTag
      page(listOf(1)) // no ETag -> nothing to cache
    }

    seq.toList()
    seq.toList()
    assertEquals(listOf(null, null), sentETags)
  }

  @Test
  fun `caches pages independently by URI`() = timeoutRunBlocking {
    val calls = mutableListOf<Pair<Boolean, String?>>() // isFirstPage to sent ETag
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { uri: URI, eTag: String? ->
      val isFirstPage = uri == baseUri
      calls += isFirstPage to eTag
      when {
        isFirstPage && eTag == null -> page(listOf(1), eTag = "e1", nextLink = page2Uri)
        isFirstPage -> notModified() // conditional refresh: the cached next link (not the 304 response) keeps paging to page 2
        eTag == null -> page(listOf(2), eTag = "e2")
        else -> notModified()
      }
    }

    assertEquals(listOf(listOf(1), listOf(2)), seq.pages())
    assertEquals(listOf(listOf(1), listOf(2)), seq.pages())
    assertEquals(listOf(true to null, false to null, true to "e1", false to "e2"), calls)
  }

  @Test
  fun `continues pagination from the cached next link when a 304 omits the Link header`() = timeoutRunBlocking {
    // a 304 is not required to echo the `Link` header, so the cached next link must keep pagination going regardless
    val sentETags = mutableListOf<Pair<Boolean, String?>>() // isFirstPage to sent ETag
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { uri: URI, eTag: String? ->
      val isFirstPage = uri == baseUri
      sentETags += isFirstPage to eTag
      when {
        isFirstPage && eTag == null -> page(listOf(1), eTag = "e1", nextLink = page2Uri)
        eTag == null -> page(listOf(2), eTag = "e2")
        else -> notModified() // every conditional refresh replies 304 WITHOUT a Link header
      }
    }

    assertEquals(listOf(listOf(1), listOf(2)), seq.pages()) // initial load links page 1 -> page 2
    assertEquals(listOf(listOf(1), listOf(2)), seq.pages()) // refresh: both pages 304 with no Link, the cache drives paging
    assertEquals(
      listOf(true to null, false to null, true to "e1", false to "e2"),
      sentETags
    )
  }

  @Test
  fun `evicts entries for pages that disappear`() = timeoutRunBlocking {
    var firstPageRequests = 0
    val calls = mutableListOf<Pair<Boolean, String?>>()
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { uri: URI, eTag: String? ->
      val isFirstPage = uri == baseUri
      calls += isFirstPage to eTag
      if (isFirstPage) {
        firstPageRequests++
        when (firstPageRequests) {
          1 -> page(listOf(1), eTag = "e1", nextLink = page2Uri)     // two pages
          2 -> page(listOf(1), eTag = "e1b")                         // page 1 changed: shrank to one page (no next link)
          else -> page(listOf(1), eTag = "e1c", nextLink = page2Uri) // page 1 changed again: grew back to two pages
        }
      }
      else {
        if (eTag == null) page(listOf(2), eTag = "e2") else notModified()
      }
    }

    seq.toList() // loads page 1 (e1) and page 2 (e2)
    seq.toList() // page 1 changed and dropped its next link -> page 2 evicted from the cache
    seq.toList() // page 1 changed and linked page 2 again -> page 2 re-requested, now without a cached ETag

    assertEquals(
      listOf(true to null, false to null, true to "e1", true to "e1b", false to null),
      calls
    )
  }

  @Test
  fun `evicts a vanished page when a 304 still reports pagination without a next link`() = timeoutRunBlocking {
    // the page content is unchanged (304), but the server echoes a Link header with no `next` -> the resource shrank,
    // so the echoed structure must win over the cached next link
    var firstPageRequests = 0
    val calls = mutableListOf<Pair<Boolean, String?>>()
    val seq = GitLabApiUtil.etagCachingLinkedPagesSequence(baseUri) { uri: URI, eTag: String? ->
      val isFirstPage = uri == baseUri
      calls += isFirstPage to eTag
      if (isFirstPage) {
        firstPageRequests++
        if (firstPageRequests == 1) page(listOf(1), eTag = "e1", nextLink = page2Uri) // two pages
        else notModifiedWithoutNextLink()                                             // unchanged, but no more next page
      }
      else {
        if (eTag == null) page(listOf(2), eTag = "e2") else notModified()
      }
    }

    seq.toList() // loads page 1 (e1) and page 2 (e2)
    seq.toList() // page 1 -> 304 reporting no next -> page 2 evicted, not requested
    seq.toList() // page 1 -> 304 reporting no next -> page 2 still not requested

    assertEquals(
      listOf(true to null, false to null, true to "e1", true to "e1"),
      calls
    )
  }

  private suspend fun ComputableSequence<ListPart<Int>>.pages(): List<List<Int>> =
    toList().map { it.value }

  private fun page(body: List<Int>, eTag: String? = null, nextLink: String? = null): HttpResponse<out List<Int>?> =
    response(HttpURLConnection.HTTP_OK, body, eTag, nextLink)

  private fun notModified(nextLink: String? = null): HttpResponse<out List<Int>?> =
    response(HttpURLConnection.HTTP_NOT_MODIFIED, body = null, eTag = null, nextLink = nextLink)

  /** A `304` that echoes a `Link` header carrying pagination structure but no `next` relation (the resource shrank). */
  private fun notModifiedWithoutNextLink(): HttpResponse<out List<Int>?> {
    val response = mockk<HttpResponse<List<Int>?>>()
    every { response.statusCode() } returns HttpURLConnection.HTTP_NOT_MODIFIED
    every { response.body() } returns null
    every { response.headers() } returns HttpHeaders.of(
      mapOf(LinkHttpHeaderValue.HEADER_NAME to listOf("<$baseUri>; rel=\"first\"")),
    ) { _, _ -> true }
    return response
  }

  private fun response(status: Int, body: List<Int>?, eTag: String?, nextLink: String?): HttpResponse<out List<Int>?> {
    val response = mockk<HttpResponse<List<Int>?>>()
    every { response.statusCode() } returns status
    every { response.body() } returns body
    every { response.headers() } returns headers(eTag, nextLink)
    return response
  }

  private fun headers(eTag: String?, nextLink: String?): HttpHeaders {
    val raw = buildMap<String, List<String>> {
      if (eTag != null) put(HttpClientUtil.ETAG_HEADER, listOf(eTag))
      if (nextLink != null) put(LinkHttpHeaderValue.HEADER_NAME, listOf("<$nextLink>; rel=\"next\""))
    }
    return HttpHeaders.of(raw) { _, _ -> true }
  }
}
