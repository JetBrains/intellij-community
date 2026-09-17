// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.HttpRequests
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.plugins.github.api.GithubApiRequest.Post.GQLQuery
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException
import org.jetbrains.plugins.github.util.GithubSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * IJPL-254836: the executor must back off locally once the GitHub API rate limit is exhausted and must cap
 * the number of concurrent GraphQL requests, so that a burst (e.g. the pull request view fan-out) can neither
 * overshoot the budget nor be re-fired on every retry.
 *
 * No network is involved: the executor is wired to a fake connection and the number of "network" calls is counted.
 */
@TestApplication
internal class GithubApiRequestExecutorRateLimitTest {

  private val clock = MutableClock(Instant.parse("2026-09-10T10:00:00Z"))
  private val server = GithubServerPath.DEFAULT_SERVER

  private val resetAt: Instant = clock.instant().plus(Duration.ofHours(1))

  private fun gqlRequest(): GithubApiRequest<Any> =
    GQLQuery.Parsed(server.toGraphQLUrl(), GHGQLQueries.findPullRequestId,
                    mapOf("repoOwner" to "owner", "repoName" to "repo", "number" to 1), Any::class.java)

  private fun restRequest(): GithubApiRequest<String> =
    object : GithubApiRequest.Get<String>(server.toApiUrl() + "/user") {
      override fun extractResult(response: GithubApiResponse): String = response.readBody { it.readText() }
    }

  private fun rateLimitedResponse(resource: String, headers: Map<String, String> = rateLimitHeaders(resource, remaining = 0)) =
    FakeResponse(HttpURLConnection.HTTP_FORBIDDEN, headers, RATE_LIMIT_ERROR_BODY)

  private fun rateLimitHeaders(resource: String, remaining: Int): Map<String, String> =
    mapOf("x-ratelimit-remaining" to remaining.toString(),
          "x-ratelimit-reset" to resetAt.epochSecond.toString(),
          "x-ratelimit-resource" to resource)

  private fun gqlResponse(remaining: Int, headers: Map<String, String> = emptyMap()) =
    FakeResponse(HttpURLConnection.HTTP_OK, headers, """
      {"data": {"repository": {"pullRequest": {"id": "PR_1", "number": 1}},
                "rateLimit": {"cost": 1, "remaining": $remaining, "resetAt": "$resetAt"}}}
      """.trimIndent())

  @Test
  fun `rate limited response stops all further requests until reset without touching the network`() {
    val executor = FakeExecutor(clock) { rateLimitedResponse("graphql") }

    assertThatThrownBy { executor.execute(gqlRequest()) }
      .isInstanceOf(GithubRateLimitExceededException::class.java)
      .extracting { (it as GithubRateLimitExceededException).resetAt }.isEqualTo(resetAt)

    repeat(20) {
      assertThatThrownBy { executor.execute(gqlRequest()) }
        .isInstanceOf(GithubRateLimitExceededException::class.java)
        .hasMessageContaining("rate limit")
        .extracting { (it as GithubRateLimitExceededException).resetAt }.isEqualTo(resetAt)
    }

    assertThat(executor.networkCalls.get()).describedAs("network calls").isEqualTo(1)
  }

  @Test
  fun `requests resume after the reset instant`() {
    var rateLimited = true
    val executor = FakeExecutor(clock) { if (rateLimited) rateLimitedResponse("graphql") else gqlResponse(remaining = 4999) }

    assertThatThrownBy { executor.execute(gqlRequest()) }.isInstanceOf(GithubRateLimitExceededException::class.java)
    assertThatThrownBy { executor.execute(gqlRequest()) }.isInstanceOf(GithubRateLimitExceededException::class.java)
    assertThat(executor.networkCalls.get()).isEqualTo(1)

    rateLimited = false
    clock.advance(Duration.ofHours(1).plusSeconds(1))

    executor.execute(gqlRequest())
    assertThat(executor.networkCalls.get()).isEqualTo(2)
  }

  @Test
  fun `successful GraphQL response reporting an exhausted budget puts the executor into back-off`() {
    val executor = FakeExecutor(clock) { gqlResponse(remaining = 0) }

    executor.execute(gqlRequest())

    assertThatThrownBy { executor.execute(gqlRequest()) }
      .isInstanceOf(GithubRateLimitExceededException::class.java)
      .extracting { (it as GithubRateLimitExceededException).resetAt }.isEqualTo(resetAt)
    assertThat(executor.networkCalls.get()).isEqualTo(1)
  }

  @Test
  fun `REST headers reporting an exhausted budget put the executor into back-off for that resource only`() {
    val executor = FakeExecutor(clock) { request ->
      if (request is GQLQuery) gqlResponse(remaining = 4999)
      else FakeResponse(HttpURLConnection.HTTP_OK, rateLimitHeaders("core", remaining = 0), """{"login": "user"}""")
    }

    executor.execute(restRequest())
    assertThatThrownBy { executor.execute(restRequest()) }.isInstanceOf(GithubRateLimitExceededException::class.java)
    assertThat(executor.networkCalls.get()).isEqualTo(1)

    // the GraphQL budget is separate and still available
    executor.execute(gqlRequest())
    assertThat(executor.networkCalls.get()).isEqualTo(2)
  }

  @Test
  fun `rate limited response without reset headers backs off for the default cooldown`() {
    val executor = FakeExecutor(clock) { rateLimitedResponse("core", headers = emptyMap()) }
    val expectedResetAt = clock.instant().plus(GithubApiRequestExecutor.RateLimitState.DEFAULT_COOLDOWN)

    assertThatThrownBy { executor.execute(restRequest()) }
      .isInstanceOf(GithubRateLimitExceededException::class.java)
      .extracting { (it as GithubRateLimitExceededException).resetAt }.isEqualTo(expectedResetAt)
    assertThatThrownBy { executor.execute(restRequest()) }.isInstanceOf(GithubRateLimitExceededException::class.java)
    assertThat(executor.networkCalls.get()).isEqualTo(1)

    clock.advance(GithubApiRequestExecutor.RateLimitState.DEFAULT_COOLDOWN.plusSeconds(1))
    assertThatThrownBy { executor.execute(restRequest()) }.isInstanceOf(GithubRateLimitExceededException::class.java)
    assertThat(executor.networkCalls.get()).describedAs("request is re-sent after the cooldown").isEqualTo(2)
  }

  @Test
  fun `Retry-After header is used when the reset header is absent`() {
    val retryAfterSeconds = 120L
    val executor = FakeExecutor(clock) {
      rateLimitedResponse("core", headers = mapOf("Retry-After" to retryAfterSeconds.toString()))
    }
    val expectedResetAt = clock.instant().plusSeconds(retryAfterSeconds)

    assertThatThrownBy { executor.execute(restRequest()) }
      .isInstanceOf(GithubRateLimitExceededException::class.java)
      .extracting { (it as GithubRateLimitExceededException).resetAt }.isEqualTo(expectedResetAt)
    assertThatThrownBy { executor.execute(restRequest()) }.isInstanceOf(GithubRateLimitExceededException::class.java)
    assertThat(executor.networkCalls.get()).isEqualTo(1)

    clock.advance(Duration.ofSeconds(retryAfterSeconds + 1))
    assertThatThrownBy { executor.execute(restRequest()) }.isInstanceOf(GithubRateLimitExceededException::class.java)
    assertThat(executor.networkCalls.get()).isEqualTo(2)
  }

  @Test
  fun `known reset instant is kept when no reset headers are available`() {
    val executor = FakeExecutor(clock) { rateLimitedResponse("core") }

    assertThatThrownBy { executor.execute(restRequest()) }
      .extracting { (it as GithubRateLimitExceededException).resetAt }.isEqualTo(resetAt)

    clock.advance(Duration.ofMinutes(30))
    val state = executor.rateLimitState
    assertThat(state.markExhausted(GithubApiRequestExecutor.RateLimitState.RESOURCE_CORE, null)).isEqualTo(resetAt)
    assertThatThrownBy { executor.execute(restRequest()) }
      .extracting { (it as GithubRateLimitExceededException).resetAt }.isEqualTo(resetAt)
    assertThat(executor.networkCalls.get()).isEqualTo(1)
  }

  @Test
  fun `missing rate limit information never blocks requests`() {
    val executor = FakeExecutor(clock) { FakeResponse(HttpURLConnection.HTTP_OK, emptyMap(), """{"login": "user"}""") }

    repeat(5) { executor.execute(restRequest()) }

    assertThat(executor.networkCalls.get()).isEqualTo(5)
  }

  @Test
  @Timeout(30)
  fun `concurrent GraphQL requests are capped`() {
    val requests = 50
    val inFlight = AtomicInteger()
    val maxInFlight = AtomicInteger()
    val executor = FakeExecutor(clock) {
      val current = inFlight.incrementAndGet()
      maxInFlight.accumulateAndGet(current, Math::max)
      Thread.sleep(50)
      inFlight.decrementAndGet()
      gqlResponse(remaining = 4999)
    }

    val pool = Executors.newFixedThreadPool(requests)
    try {
      val start = CountDownLatch(1)
      val futures = (1..requests).map {
        pool.submit(Callable {
          start.await()
          executor.execute(gqlRequest())
        })
      }
      start.countDown()
      futures.forEach { it.get(20, TimeUnit.SECONDS) }
    }
    finally {
      pool.shutdownNow()
    }

    assertThat(executor.networkCalls.get()).isEqualTo(requests)
    assertThat(maxInFlight.get())
      .describedAs("max in-flight GraphQL requests")
      .isGreaterThan(1)
      .isLessThanOrEqualTo(GithubApiRequestExecutor.Base.MAX_CONCURRENT_GRAPHQL_REQUESTS)
  }

  private class FakeResponse(val code: Int, val headers: Map<String, String>, val body: String)

  /**
   * An executor whose network layer is replaced by [responder]; every call to it is counted in [networkCalls].
   */
  private class FakeExecutor(
    clock: Clock,
    private val responder: (GithubApiRequest<*>) -> FakeResponse,
  ) : GithubApiRequestExecutor.Base(GithubSettings(), clock) {
    val networkCalls = AtomicInteger()

    override fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T =
      executeRequest(request, indicator) { processor ->
        networkCalls.incrementAndGet()
        processor.process(FakeRequest(request, responder(request)))
      }
  }

  private class FakeRequest(request: GithubApiRequest<*>, response: FakeResponse) : HttpRequests.Request {
    private val url = URL(request.url)
    private val connection = FakeConnection(url, if (request is GithubApiRequest.WithBody) "POST" else "GET", response)

    override fun getURL(): String = url.toString()
    override fun getConnection(): URLConnection = connection
    override fun getInputStream(): InputStream = connection.inputStream
    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
    override fun getReader(indicator: ProgressIndicator?): BufferedReader = reader
    override fun saveToFile(file: Path, indicator: ProgressIndicator?): Path = throw UnsupportedOperationException()
    override fun saveToFile(file: Path, indicator: ProgressIndicator?, progressDescription: Boolean): Path =
      throw UnsupportedOperationException()
    override fun readBytes(indicator: ProgressIndicator?): ByteArray = inputStream.readAllBytes()
    override fun readString(indicator: ProgressIndicator?): String = readBytes(indicator).toString(Charsets.UTF_8)
    override fun readChars(indicator: ProgressIndicator?): CharSequence = readString(indicator)
    override fun readError(): String? = connection.errorStream?.readAllBytes()?.toString(Charsets.UTF_8)
    override fun write(data: ByteArray) = Unit
  }

  private class FakeConnection(url: URL, private val requestMethod: String, private val response: FakeResponse) : HttpURLConnection(url) {
    private val headers = response.headers.mapKeys { it.key.lowercase() } + ("content-type" to GithubApiContentHelper.JSON_MIME_TYPE)

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun getRequestMethod(): String = requestMethod
    override fun getResponseCode(): Int = response.code
    override fun getResponseMessage(): String = if (response.code >= 400) "Error" else "OK"
    override fun getHeaderField(name: String?): String? = name?.let { headers[it.lowercase()] }
    override fun getInputStream(): InputStream =
      if (response.code < 400) ByteArrayInputStream(response.body.toByteArray()) else throw IOException("HTTP ${response.code}")
    override fun getErrorStream(): InputStream? =
      if (response.code >= 400) ByteArrayInputStream(response.body.toByteArray()) else null
  }

  private class MutableClock(@Volatile private var now: Instant) : Clock() {
    fun advance(duration: Duration) {
      now = now.plus(duration)
    }

    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
  }

  companion object {
    private const val RATE_LIMIT_ERROR_BODY =
      """{"message": "API rate limit exceeded for user ID 1. If you reach out to GitHub Support for help, please include the request ID.",
          "documentation_url": "https://docs.github.com/graphql/overview/rate-limits-and-node-limits-for-the-graphql-api"}"""
  }
}
