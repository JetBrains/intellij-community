// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.net.PlatformHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class SeRankerEvaluatorTest {
  private lateinit var server: HttpServer
  private lateinit var client: HttpClient

  /** The statuses of the next responses. The last status repeats. */
  private val statuses = ConcurrentLinkedQueue<Int>()
  private val requestCount = AtomicInteger()

  @BeforeEach
  fun startServer() {
    server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/") { exchange ->
      requestCount.incrementAndGet()
      exchange.requestBody.readAllBytes()
      val status = (if (statuses.size > 1) statuses.poll() else statuses.peek()) ?: 200
      val bytes = (if (status == 200) ANSWER else """{"error":"test"}""").encodeToByteArray()
      if (status == 401) {
        // Without the challenge, the JDK client with an authenticator throws an IOException instead of the status.
        exchange.responseHeaders.add("WWW-Authenticate", """Bearer realm="test"""")
      }
      if (status == 429) exchange.responseHeaders.add("Retry-After", "0")
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    client = PlatformHttpClient.client()
  }

  @AfterEach
  fun stopServer() {
    client.shutdownNow()
    server.stop(0)
  }

  @Test
  fun `the dataset in the repository has unique actions and expects only its own actions`() {
    val dataset = SeRankerDataset.load(SeRankerDataset.defaultPath())
    val ids = dataset.actions.map { it.id }

    assertEquals(ids.size, ids.toSet().size)
    assertTrue(dataset.requests.isNotEmpty())
    for (request in dataset.requests) {
      assertTrue(request.request.isNotBlank())
      assertTrue(request.expected.size in 1..3, request.request)
      assertTrue(ids.containsAll(request.expected), request.request)
    }
  }

  @Test
  fun `a throttled request gets a new attempt and its time does not count`() = runBlocking {
    statuses += listOf(429, 200)

    val run = evaluator().evaluate(ranker, listOf(REQUEST))

    val result = run.results.single()
    assertEquals(listOf("ZoomIn"), result.matches)
    assertEquals(1, result.throttledResponses)
    assertEquals(1, result.requestDurations.size)
    assertNotNull(result.rankingDuration)
    assertEquals(2, requestCount.get())
    assertNull(run.rejection)
  }

  @Test
  fun `a rejected key stops the evaluation of the ranker`() = runBlocking {
    statuses += 401

    val run = evaluator().evaluate(ranker, listOf(REQUEST, REQUEST, REQUEST))

    assertEquals(emptyList<SeRankerRequestResult>(), run.results)
    assertEquals(1, requestCount.get())
    assertEquals(false, run.rejection?.contains(ranker.key), run.rejection)
  }

  @Test
  fun `a request that fails on each attempt has no matches`() = runBlocking {
    statuses += 500

    val run = evaluator(maxAttempts = 2).evaluate(ranker, listOf(REQUEST))

    assertNull(run.results.single().matches)
    assertEquals(2, requestCount.get())
  }

  @Test
  fun `the metrics count the positions of the expected actions`() {
    val metrics = SeRankerMetrics(SeRankerRun(ranker, listOf(
      result(listOf("A"), listOf("A", "B")),
      result(listOf("B", "C"), listOf("X", "C")),
      result(listOf("A"), emptyList()),
      result(listOf("A"), matches = null),
    ), rejection = null))

    assertEquals(3, metrics.ranked.size)
    assertEquals(1, metrics.failed)
    assertEquals(1, metrics.hits(1))
    assertEquals(2, metrics.hits(3))
    assertEquals(1, metrics.bestFirst)
    assertEquals(1, metrics.empty)
    assertEquals(0.5, metrics.meanReciprocalRank, 1e-9)
  }

  @Test
  fun `the nearest-rank percentile picks a measured duration`() {
    val durations = (1..10).map { (it * 100).milliseconds }

    assertEquals(500L, durations.percentile(50)?.inWholeMilliseconds)
    assertEquals(1000L, durations.percentile(95)?.inWholeMilliseconds)
    assertNull(emptyList<Duration>().percentile(50))
  }

  @Test
  fun `the report compares the rankers and escapes the table cells`() {
    val missed = SeRankerDatasetRequest("a | b", listOf("A"), "A")
    val dataset = SeRankerDataset("Test IDE", listOf(SeActionsRankerCandidate("A", "Action A", null)), listOf(missed))
    val other = SeEvaluatedRanker("Other", SeDecisionsApi.TYPESAFE, "http://localhost", "other-model", "other-key")

    val report = renderRankerReport(dataset, mapOf("Date" to "today"), listOf(
      SeRankerRun(ranker, listOf(SeRankerRequestResult(missed, listOf("B"), 10.milliseconds, listOf(10.milliseconds), 0)), null),
      SeRankerRun(other, listOf(SeRankerRequestResult(missed, listOf("A"), 20.milliseconds, listOf(20.milliseconds), 0)), null),
    ))

    assertTrue(report.contains("| Metric | Test | Other |"), report)
    assertTrue(report.contains("| Hit@1 | 0.0% ("), report)
    assertTrue(report.contains("| Only Other finds it | 1 |"), report)
    assertTrue(report.contains("| a \\| b | A | B | A |"), report)
    assertFalse(report.contains(ranker.key), report)
  }

  private fun evaluator(maxAttempts: Int = 6): SeRankerEvaluator {
    return SeRankerEvaluator(client, CANDIDATES, concurrency = 1, maxAttempts = maxAttempts, retryDelay = { _, _ -> Duration.ZERO })
  }

  private fun result(expected: List<String>, matches: List<String>?): SeRankerRequestResult {
    val request = SeRankerDatasetRequest("request", expected, expected.first())
    return SeRankerRequestResult(request, matches, rankingDuration = null, requestDurations = emptyList(), throttledResponses = 0)
  }

  private val ranker: SeEvaluatedRanker
    get() = SeEvaluatedRanker("Test", SeDecisionsApi.OPENAI, "http://127.0.0.1:${server.address.port}/v1", "test-model", "test-secret")

  private companion object {
    val CANDIDATES: List<SeActionsRankerCandidate> = listOf(
      SeActionsRankerCandidate("ZoomIn", "Zoom In", "Increase the IDE scale"),
      SeActionsRankerCandidate("ZoomOut", "Zoom Out", null),
    )
    val REQUEST: SeRankerDatasetRequest = SeRankerDatasetRequest("too small", listOf("ZoomIn"), "ZoomIn")
    const val ANSWER: String = """{"answers":[{"name":"action","choice":"a0","probabilities":[{"value":"a0","probability":0.9}]}]}"""
  }
}
