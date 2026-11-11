// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRateLimit
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException
import org.jetbrains.plugins.github.exceptions.GithubSecondaryRateLimitExceededException
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
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLSession
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val REPO_PATH = "/repos/owner/repo"
private const val SEARCH_PATH = "/search/issues"
private const val TOKEN = "test-token"
private const val OTHER_TOKEN = "other-token"
private const val PRIMARY_RATE_LIMIT_BODY = """{"message":"API rate limit exceeded for user ID 1."}"""

/**
 * Checks the primary rate limit budgets and the secondary rate limit pauses of [GHApiRateLimiter] against a local HTTP server.
 *
 * Each case starts its own server on a new port, so the budgets and the pauses of the cases don't mix in the shared [GHApiRateLimiter].
 * The limits are separate for each token, and for the requests without a token.
 * The secondary rate limit errors ask for short waits, so the cases with retries take a few seconds.
 */
@TestApplication
internal class GHApiRateLimiterTest {
  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var server: HttpServer
  private lateinit var serverPath: GithubServerPath
  private lateinit var executor: GithubApiRequestExecutor

  private val received = AtomicInteger()

  /**
   * The [System.nanoTime] of each received request.
   */
  private val receivedAt = CopyOnWriteArrayList<Long>()

  @Volatile
  private var answer: (HttpExchange) -> Unit = { respond(it, 200, "{}") }

  @BeforeEach
  fun startServer() {
    Registry.get(GithubApiRequestExecutor.Factory.JDK11_CLIENT_REGISTRY_KEY).setValue(true, testDisposable)
    server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/") { exchange ->
      exchange.use {
        receivedAt.add(System.nanoTime())
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
        TOKEN,
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
      limiter.execute(restRequest(), TOKEN, send = { fail("The request reached the server with an exhausted budget") }, parse = { _, _ -> })
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

    assertFalse(error is GithubSecondaryRateLimitExceededException)
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
  fun `a GraphQL rate limit error with the status 200 fails the GraphQL requests until the reset`(): Unit = timeoutRunBlocking {
    val resetAt = inOneHour()
    // the shape that GitHub sends now: https://github.com/github/docs/issues/22607
    answer = {
      addExhaustedBudgetHeaders(it, "graphql", resetAt)
      respond(it, 200, graphQLErrors("RATE_LIMIT", "graphql_rate_limit", "API rate limit already exceeded for user ID 1."))
    }

    val error = assertThrows<GithubRateLimitExceededException> {
      executor.execute(parsingGraphQLRequest())
    }
    assertThrows<GithubRateLimitExceededException> {
      executor.execute(parsingGraphQLRequest())
    }

    assertFalse(error is GithubSecondaryRateLimitExceededException)
    assertEquals(resetAt, error.resetAt)
    assertEquals(1, received.get())
  }

  @Test
  fun `a GraphQL rate limit error of the older type without the headers blocks the GraphQL requests`(): Unit = timeoutRunBlocking {
    answer = { respond(it, 200, graphQLErrors("RATE_LIMITED", null, "API rate limit exceeded for user ID 1.")) }

    assertThrows<GithubRateLimitExceededException> {
      executor.execute(parsingGraphQLRequest())
    }
    assertThrows<GithubRateLimitExceededException> {
      executor.execute(parsingGraphQLRequest())
    }

    assertEquals(1, received.get())
  }

  @Test
  fun `a GraphQL secondary rate limit error with the status 200 fails the next queries until the pause ends`(): Unit = timeoutRunBlocking {
    answer = {
      if (received.get() == 1) {
        it.responseHeaders.add(GHRateLimitHeaders.RETRY_AFTER_HEADER, "1")
        respond(it,
                200,
                graphQLErrors(null, null, "You have exceeded a secondary rate limit. Please wait a few minutes before you try again."))
      }
      else respond(it, 200, """{"data":{}}""")
    }

    // This call reaches the server
    assertThrows<GithubSecondaryRateLimitExceededException> {
      executor.execute(parsingGraphQLRequest())
    }
    // This call never reaches the server
    assertThrows<GithubSecondaryRateLimitExceededException> {
      executor.execute(parsingGraphQLRequest())
    }
    assertEquals(1, received.get())

    delay(1100.milliseconds)
    executor.execute(parsingGraphQLRequest())

    assertEquals(2, received.get())
    assertWaitedBetweenRequests(1.seconds)
  }

  @Test
  fun `a secondary rate limit error fails the next requests without a call to the server until the pause ends`(): Unit =
    timeoutRunBlocking {
      answer = { if (received.get() == 1) respondSecondaryLimit(it, retryAfterSeconds = 1) else respondRepo(it) }

      assertThrows<GithubSecondaryRateLimitExceededException> {
        executor.execute(restRequest())
      }
      assertThrows<GithubSecondaryRateLimitExceededException> {
        executor.execute(restRequest())
      }
      assertEquals(1, received.get())

      delay(1100.milliseconds)
      val result = executor.execute(restRequest())

      assertEquals(TestRepo("repo"), result)
      assertEquals(2, received.get())
      assertWaitedBetweenRequests(1.seconds)
    }

  @Test
  @RegistryKey(key = "github.api.max.concurrent.requests", value = "1")
  fun `a queued request observes the pause when a permit is released`(): Unit = timeoutRunBlocking {
    val scope = this
    val limiter = GHApiRateLimiter()

    assertThrows<GithubSecondaryRateLimitExceededException> {
      limiter.executeLocally(restRequest()) {
        // Resume the queued request during the release of the permit.
        scope.launch(Dispatchers.Unconfined) {
          assertThrows<GithubSecondaryRateLimitExceededException> {
            limiter.execute(restRequest(), TOKEN, send = { fail("The queued request reached the server during the pause") }, parse = { _, _ -> })
          }
        }
        throw GithubSecondaryRateLimitExceededException("secondary rate limit", 2.minutes)
      }
    }
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

  @Test
  fun `a request during the pause reports the time left`(): Unit = timeoutRunBlocking {
    answer = {
      it.responseHeaders.add(GHRateLimitHeaders.RETRY_AFTER_HEADER, "120")
      respond(it, 403, """{"message":"You have exceeded a secondary rate limit. Please wait a few minutes before you try again."}""")
    }

    assertThrows<GithubSecondaryRateLimitExceededException> {
      executor.execute(restRequest())
    }
    val error = assertThrows<GithubSecondaryRateLimitExceededException> {
      executor.execute(graphQLRequest())
    }

    assertEquals(1, received.get())
    val timeLeft = error.retryAfter!!
    assertTrue(timeLeft > 1.minutes && timeLeft <= 2.minutes) { "Unexpected time left $timeLeft" }
  }

  @Test
  fun `executors with the same token share the budget`(): Unit = timeoutRunBlocking {
    answer = { respondExhausted(it, "core", inOneHour()) }
    executor.execute(restRequest())
    val sameTokenExecutor = GithubApiRequestExecutor.Factory.getInstance().create(serverPath, TOKEN)

    assertThrows<GithubRateLimitExceededException> {
      sameTokenExecutor.execute(restRequest())
    }

    assertEquals(1, received.get())
  }

  @Test
  fun `an exhausted budget of a token doesn't block requests with another token`(): Unit = timeoutRunBlocking {
    answer = { respondExhausted(it, "core", inOneHour()) }
    executor.execute(restRequest())
    val otherExecutor = GithubApiRequestExecutor.Factory.getInstance().create(serverPath, OTHER_TOKEN)

    assertThrows<GithubRateLimitExceededException> {
      executor.execute(restRequest())
    }
    otherExecutor.execute(restRequest())

    assertEquals(2, received.get())
  }

  @Test
  fun `an exhausted budget without a token doesn't block requests with a token`(): Unit = timeoutRunBlocking {
    answer = { respondExhausted(it, "core", inOneHour()) }
    val anonymousExecutor = GithubApiRequestExecutor.Factory.getInstance().create()
    anonymousExecutor.execute(restRequest())

    assertThrows<GithubRateLimitExceededException> {
      anonymousExecutor.execute(restRequest())
    }
    executor.execute(restRequest())

    assertEquals(2, received.get())
  }

  @Test
  fun `a secondary rate limit pause of a token doesn't pause requests with another token`(): Unit = timeoutRunBlocking {
    answer = { if (received.get() == 1) respondSecondaryLimit(it, retryAfterSeconds = 30) else respondRepo(it) }
    // the request fails at once and leaves the pause of the token behind
    assertThrows<GithubSecondaryRateLimitExceededException> {
      executor.execute(restRequest())
    }
    val otherExecutor = GithubApiRequestExecutor.Factory.getInstance().create(serverPath, OTHER_TOKEN)

    withTimeout(5.seconds) {
      otherExecutor.execute(restRequest())
    }

    assertEquals(2, received.get())
  }

  @Test
  fun `the old executor reports a secondary rate limit error`(): Unit = timeoutRunBlocking {
    Registry.get(GithubApiRequestExecutor.Factory.JDK11_CLIENT_REGISTRY_KEY).setValue(false, testDisposable)
    val oldExecutor = GithubApiRequestExecutor.Factory.getInstance().create(serverPath, TOKEN)
    answer = { respondSecondaryLimit(it, retryAfterSeconds = 1) }

    val error = assertThrows<GithubSecondaryRateLimitExceededException> {
      oldExecutor.execute(restRequest())
    }

    assertEquals(1L, error.retryAfter?.inWholeSeconds)
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
  ): T = execute(request, TOKEN, send = { TestResponse(headers) }, parse = { _, _ -> result() })

  private fun coreBudgetHeaders(remaining: Int, resetAt: Instant): Map<String, String> = mapOf(
    GHRateLimitHeaders.RATE_LIMIT_RESOURCE_HEADER to "core",
    GHRateLimitHeaders.RATE_LIMIT_REMAINING_HEADER to remaining.toString(),
    GHRateLimitHeaders.RATE_LIMIT_RESET_HEADER to resetAt.epochSeconds.toString(),
  )

  private fun assertWaitedBetweenRequests(wait: Duration) {
    val (first, second) = receivedAt
    val waited = (second - first).nanoseconds
    assertTrue(waited >= wait) { "The second request came $waited after the first one, expected at least $wait" }
  }

  private fun restRequest(): GithubApiRequest<TestRepo> =
    GithubApiRequest.Get.Json("${serverPath.toApiUrl()}$REPO_PATH", TestRepo::class.java)

  private fun searchRequest(): GithubApiRequest<*> =
    GithubApiRequests.Search.Issues.get("${serverPath.toApiUrl()}$SEARCH_PATH?q=repo:owner/repo")

  private fun graphQLRequest(): GithubApiRequest<Unit> = TestGQLQuery(serverPath.toGraphQLUrl())

  private fun parsingGraphQLRequest(): GithubApiRequest<Unit> = ParsingTestGQLQuery(serverPath.toGraphQLUrl())

  private fun graphQLErrors(type: String?, code: String?, message: String): String {
    val fields = listOfNotNull(type?.let { "\"type\":\"$it\"" }, code?.let { "\"code\":\"$it\"" }, "\"message\":\"$message\"")
    return """{"errors":[{${fields.joinToString(",")}}]}"""
  }

  // the header holds whole seconds
  private fun inOneHour(): Instant = Instant.fromEpochSeconds(Clock.System.now().epochSeconds + 3600)

  private fun respondExhausted(exchange: HttpExchange, resource: String?, resetAt: Instant) {
    addExhaustedBudgetHeaders(exchange, resource, resetAt)
    respondRepo(exchange)
  }

  private fun respondRepo(exchange: HttpExchange) = respond(exchange, 200, """{"name":"repo"}""")

  private fun respondSecondaryLimit(exchange: HttpExchange, retryAfterSeconds: Int) {
    exchange.responseHeaders.add(GHRateLimitHeaders.RETRY_AFTER_HEADER, retryAfterSeconds.toString())
    respond(exchange, 429, """{"message":"You have exceeded a secondary rate limit."}""")
  }

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
 * A bundled GraphQL operation that parses errors and ignores the response data.
 */
private class ParsingTestGQLQuery(url: String) : GithubApiRequest.Post.GQLQuery<Unit>(url, GHGQLQueries.findRepository, emptyMap()) {
  override val clazz: Class<*> = Unit::class.java

  override fun extractResultWithCost(response: GithubApiResponse): Pair<Unit, GHGQLRateLimit?> =
    parseResponse(response, arrayOf()) { } ?: (Unit to null)
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
