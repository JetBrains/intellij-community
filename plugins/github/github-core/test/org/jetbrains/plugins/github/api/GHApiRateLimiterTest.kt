// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRateLimit
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Optional
import javax.net.ssl.SSLSession
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val REPO_PATH = "/repos/owner/repo"
private const val SEARCH_PATH = "/search/issues"
private const val TOKEN = "test-token"
private const val PRIMARY_RATE_LIMIT_BODY = """{"message":"API rate limit exceeded for user ID 1."}"""

/**
 * Checks the primary rate limit budgets of [GHApiRateLimiter] against a local HTTP server.
 *
 * Each case starts its own server on a new port, so the budgets of the cases don't mix in the shared [GHApiRateLimiter].
 */
@TestApplication
internal class GHApiRateLimiterTest {
  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var server: HttpServer
  private lateinit var serverPath: GithubServerPath
  private lateinit var executor: GithubApiRequestExecutor

  private val received = AtomicInteger()

  @Volatile
  private var answer: (HttpExchange) -> Unit = { respond(it, 200, "{}") }

  @BeforeEach
  fun startServer() {
    Registry.get(GithubApiRequestExecutor.Factory.JDK11_CLIENT_REGISTRY_KEY).setValue(true, testDisposable)
    server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/") { exchange ->
      exchange.use {
        received.incrementAndGet()
        answer(it)
      }
    }
    server.start()
    serverPath = GithubServerPath(true, "127.0.0.1", server.address.port, null)
    executor = GithubApiRequestExecutor.Factory.getInstance().create(serverPath, TOKEN)
  }

  @AfterEach
  fun stopServer() {
    server.stop(0)
  }

  @Test
  fun `an exhausted REST budget fails REST requests without a call to the server`(): Unit = timeoutRunBlocking {
    val resetAt = inOneHour()
    answer = { respondExhausted(it, "core", resetAt) }
    executor.execute(restRequest())

    val error = assertThrows<GithubRateLimitExceededException> {
      executor.execute(restRequest())
    }

    assertEquals(resetAt, error.resetAt)
    assertEquals(1, received.get())
  }

  @Test
  fun `an exhausted REST budget fails REST requests and doesn't block GraphQL requests`(): Unit = timeoutRunBlocking {
    answer = { respondExhausted(it, "core", inOneHour()) }
    executor.execute(restRequest())

    assertThrows<GithubRateLimitExceededException> {
      executor.execute(restRequest())
    }
    executor.execute(graphQLRequest())

    assertEquals(2, received.get())
  }

  @Test
  fun `an exhausted GraphQL budget fails GraphQL requests and doesn't block REST requests`(): Unit = timeoutRunBlocking {
    answer = { respondExhausted(it, "graphql", inOneHour()) }
    executor.execute(graphQLRequest())

    assertThrows<GithubRateLimitExceededException> {
      executor.execute(graphQLRequest())
    }
    executor.execute(restRequest())

    assertEquals(2, received.get())
  }

  @Test
  fun `an exhausted search budget doesn't block REST requests`(): Unit = timeoutRunBlocking {
    answer = { respondExhausted(it, "search", inOneHour()) }
    executor.execute(restRequest())

    executor.execute(restRequest())

    assertEquals(2, received.get())
  }

  @Test
  fun `an exhausted REST budget does not block search requests`(): Unit = timeoutRunBlocking {
    answer = {
      if (it.requestURI.path.endsWith(SEARCH_PATH)) {
        respond(it, 200, """{"total_count":0,"incomplete_results":false,"items":[]}""")
      }
      else respondExhausted(it, "core", inOneHour())
    }
    executor.execute(restRequest())

    executor.execute(searchRequest())
    assertThrows<GithubRateLimitExceededException> {
      executor.execute(restRequest())
    }

    assertEquals(2, received.get())
  }

  @Test
  fun `an exhausted budget without the resource header fails the requests of its kind`(): Unit = timeoutRunBlocking {
    val resetAt = inOneHour()
    answer = { respondExhausted(it, null, resetAt) }
    executor.execute(restRequest())

    val error = assertThrows<GithubRateLimitExceededException> {
      executor.execute(restRequest())
    }
    executor.execute(graphQLRequest())

    assertEquals(resetAt, error.resetAt)
    assertEquals(2, received.get())
  }

  @Test
  fun `an exhausted search budget without the resource header doesn't block REST requests`(): Unit = timeoutRunBlocking {
    answer = {
      if (it.requestURI.path.endsWith(SEARCH_PATH)) {
        addExhaustedBudgetHeaders(it, null, inOneHour())
        respond(it, 200, """{"total_count":0,"incomplete_results":false,"items":[]}""")
      }
      else respondRepo(it)
    }
    executor.execute(searchRequest())

    executor.execute(restRequest())

    assertEquals(2, received.get())
  }

  @Test
  fun `requests go through after the budget resets`(): Unit = timeoutRunBlocking {
    answer = { respondExhausted(it, "core", Instant.fromEpochSeconds(Clock.System.now().epochSeconds - 1)) }
    executor.execute(restRequest())

    executor.execute(restRequest())

    assertEquals(2, received.get())
  }

  @Test
  fun `a remaining budget of an older response doesn't unblock requests`(): Unit = timeoutRunBlocking {
    val limiter = GHApiRateLimiter()
    val resetAt = inOneHour()
    val sent = CompletableDeferred<Unit>()
    val answered = CompletableDeferred<Unit>()
    // the server answers the first request before the budget runs out, but the answer arrives after the answer to the second request
    val first = async {
      limiter.execute(
        restRequest(),
        send = {
          sent.complete(Unit)
          answered.await()
          TestResponse(coreBudgetHeaders(remaining = 10, resetAt))
        },
        parse = { _, _ -> TestRepo("repo") },
      )
    }
    sent.await()
    limiter.executeLocally(restRequest(), coreBudgetHeaders(remaining = 0, resetAt)) { TestRepo("repo") }
    answered.complete(Unit)
    first.await()

    assertThrows<GithubRateLimitExceededException> {
      limiter.execute(restRequest(), send = { fail("The request reached the server with an exhausted budget") }, parse = { _, _ -> })
    }
  }

  @Test
  fun `a rate limit error of the server reports the reset time`(): Unit = timeoutRunBlocking {
    val resetAt = inOneHour()
    answer = {
      addExhaustedBudgetHeaders(it, "core", resetAt)
      respond(it, 403, """{"message":"API rate limit exceeded for user ID 1."}""")
    }

    val error = assertThrows<GithubRateLimitExceededException> {
      executor.execute(restRequest())
    }

    assertEquals(resetAt, error.resetAt)
  }

  @Test
  fun `a 429 without remaining requests is a primary rate limit error`(): Unit = timeoutRunBlocking {
    val resetAt = inOneHour()
    answer = {
      addExhaustedBudgetHeaders(it, "core", resetAt)
      respond(it, 429, """{"message":"Too many requests"}""")
    }

    val error = assertThrows<GithubRateLimitExceededException> {
      executor.execute(restRequest())
    }

    assertEquals(resetAt, error.resetAt)
  }

  @Test
  fun `a rate limit error without the headers blocks the requests of its kind for a minute`(): Unit = timeoutRunBlocking {
    answer = { if (received.get() == 1) respond(it, 403, PRIMARY_RATE_LIMIT_BODY) else respondRepo(it) }
    val before = Clock.System.now()

    assertThrows<GithubRateLimitExceededException> {
      executor.execute(restRequest())
    }
    val error = assertThrows<GithubRateLimitExceededException> {
      executor.execute(restRequest())
    }
    executor.execute(graphQLRequest())

    assertEquals(2, received.get())
    val resetAt = error.resetAt!!
    assertTrue(resetAt > before && resetAt <= Clock.System.now() + 1.minutes) { "Unexpected reset time $resetAt" }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `a rate limit error of a search request doesn't block REST requests`(withHeaders: Boolean): Unit = timeoutRunBlocking {
    answer = {
      if (it.requestURI.path.endsWith(SEARCH_PATH)) {
        if (withHeaders) addExhaustedBudgetHeaders(it, "search", inOneHour())
        respond(it, 403, PRIMARY_RATE_LIMIT_BODY)
      }
      else respondRepo(it)
    }

    assertThrows<GithubRateLimitExceededException> {
      executor.execute(searchRequest())
    }
    executor.execute(restRequest())

    assertEquals(2, received.get())
  }

  @Test
  fun `a new limit of concurrent requests applies to a server in use without a restart`(): Unit = timeoutRunBlocking {
    Registry.get(MAX_CONCURRENT_REQUESTS_REGISTRY_KEY).setValue(1, testDisposable)
    val limiter = GHApiRateLimiter()
    val running = CompletableDeferred<Unit>()
    val finish = CompletableDeferred<Unit>()
    val first = launch {
      limiter.executeLocally(restRequest()) {
        running.complete(Unit)
        finish.await()
        TestRepo("repo")
      }
    }
    running.await()

    // with one permit, the second request waits for the first one
    val second = withTimeoutOrNull(200.milliseconds) {
      limiter.executeLocally(restRequest()) { TestRepo("repo") }
    }
    assertNull(second)

    Registry.get(MAX_CONCURRENT_REQUESTS_REGISTRY_KEY).setValue(2, testDisposable)
    withTimeout(5.seconds) {
      limiter.executeLocally(restRequest()) { TestRepo("repo") }
    }

    finish.complete(Unit)
    first.join()
  }

  @ParameterizedTest
  @ValueSource(strings = ["http://example.com:80", "https://example.com:443"])
  fun `a URL with the default port and a URL without a port share the budget`(urlWithPort: String): Unit = timeoutRunBlocking {
    val limiter = GHApiRateLimiter()
    exhaustBudget(limiter, "$urlWithPort$REPO_PATH")

    assertTrue(isBlocked(limiter, "${urlWithPort.substringBeforeLast(':')}$REPO_PATH"))
  }

  @Test
  fun `URLs with the same host and port share the budget whatever the scheme`(): Unit = timeoutRunBlocking {
    val limiter = GHApiRateLimiter()
    exhaustBudget(limiter, "http://example.com:443$REPO_PATH")

    assertTrue(isBlocked(limiter, "https://example.com$REPO_PATH"))
    assertFalse(isBlocked(limiter, "http://example.com$REPO_PATH"))
  }

  @Test
  fun `a URL of another scheme without a port has a budget of its own`(): Unit = timeoutRunBlocking {
    val limiter = GHApiRateLimiter()
    exhaustBudget(limiter, "custom://example.com$REPO_PATH")

    assertTrue(isBlocked(limiter, "custom://example.com$REPO_PATH"))
    assertFalse(isBlocked(limiter, "custom://example.com:443$REPO_PATH"))
    assertFalse(isBlocked(limiter, "https://example.com$REPO_PATH"))
  }

  private suspend fun exhaustBudget(limiter: GHApiRateLimiter, url: String) {
    limiter.executeLocally(GithubApiRequest.Get.Json(url, TestRepo::class.java), coreBudgetHeaders(remaining = 0, inOneHour())) {
      TestRepo("repo")
    }
  }

  /**
   * Whether the [limiter] fails a REST request to the [url] without a call to the server.
   */
  private suspend fun isBlocked(limiter: GHApiRateLimiter, url: String): Boolean = try {
    limiter.executeLocally(GithubApiRequest.Get.Json(url, TestRepo::class.java)) { TestRepo("repo") }
    false
  }
  catch (_: GithubRateLimitExceededException) {
    true
  }

  /**
   * Runs the [request] through the limiter without a server.
   * The response has the [headers], and [result] gives the result of the request.
   */
  private suspend fun <T> GHApiRateLimiter.executeLocally(
    request: GithubApiRequest<T>,
    headers: Map<String, String> = emptyMap(),
    result: suspend () -> T,
  ): T = execute(request, send = { TestResponse(headers) }, parse = { _, _ -> result() })

  private fun coreBudgetHeaders(remaining: Int, resetAt: Instant): Map<String, String> = mapOf(
    GHRateLimitHeaders.RATE_LIMIT_RESOURCE_HEADER to "core",
    GHRateLimitHeaders.RATE_LIMIT_REMAINING_HEADER to remaining.toString(),
    GHRateLimitHeaders.RATE_LIMIT_RESET_HEADER to resetAt.epochSeconds.toString(),
  )

  private fun restRequest(): GithubApiRequest<TestRepo> =
    GithubApiRequest.Get.Json("${serverPath.toApiUrl()}$REPO_PATH", TestRepo::class.java)

  private fun searchRequest(): GithubApiRequest<*> =
    GithubApiRequests.Search.Issues.get("${serverPath.toApiUrl()}$SEARCH_PATH?q=repo:owner/repo")

  private fun graphQLRequest(): GithubApiRequest<Unit> = TestGQLQuery(serverPath.toGraphQLUrl())

  // the header holds whole seconds
  private fun inOneHour(): Instant = Instant.fromEpochSeconds(Clock.System.now().epochSeconds + 3600)

  private fun respondExhausted(exchange: HttpExchange, resource: String?, resetAt: Instant) {
    addExhaustedBudgetHeaders(exchange, resource, resetAt)
    respondRepo(exchange)
  }

  private fun respondRepo(exchange: HttpExchange) = respond(exchange, 200, """{"name":"repo"}""")

  /**
   * Without the [resource], the headers are like the headers of some GitHub Enterprise versions.
   */
  private fun addExhaustedBudgetHeaders(exchange: HttpExchange, resource: String?, resetAt: Instant) {
    if (resource != null) exchange.responseHeaders.add(GHRateLimitHeaders.RATE_LIMIT_RESOURCE_HEADER, resource)
    exchange.responseHeaders.add(GHRateLimitHeaders.RATE_LIMIT_REMAINING_HEADER, "0")
    exchange.responseHeaders.add(GHRateLimitHeaders.RATE_LIMIT_RESET_HEADER, resetAt.epochSeconds.toString())
  }

  private fun respond(exchange: HttpExchange, code: Int, body: String) {
    val bytes = body.toByteArray()
    exchange.responseHeaders.add("Content-Type", GithubApiContentHelper.JSON_MIME_TYPE)
    exchange.sendResponseHeaders(code, bytes.size.toLong())
    exchange.responseBody.write(bytes)
  }
}

/**
 * A GraphQL query without a query file, the server answer doesn't matter for the rate limits.
 */
private class TestGQLQuery(url: String) : GithubApiRequest.Post.GQLQuery<Unit>(url, "test", emptyMap()) {
  override val clazz: Class<*> = Unit::class.java
  override val body: String = "{}"

  override fun extractResultWithCost(response: GithubApiResponse): Pair<Unit, GHGQLRateLimit?> = Unit to null
}

/**
 * A response with the [headers] and an empty body, for the cases that run the limiter without a server.
 */
private class TestResponse(headers: Map<String, String>) : HttpResponse<InputStream> {
  private val headers = HttpHeaders.of(headers.mapValues { listOf(it.value) }) { _, _ -> true }

  override fun statusCode(): Int = 200
  override fun request(): HttpRequest = throw UnsupportedOperationException()
  override fun previousResponse(): Optional<HttpResponse<InputStream>> = Optional.empty()
  override fun headers(): HttpHeaders = headers
  override fun body(): InputStream = InputStream.nullInputStream()
  override fun sslSession(): Optional<SSLSession> = Optional.empty()
  override fun uri(): URI = throw UnsupportedOperationException()
  override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
}
