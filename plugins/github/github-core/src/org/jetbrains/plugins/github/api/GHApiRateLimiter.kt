// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.DigestUtil
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException
import org.jetbrains.plugins.github.exceptions.GithubSecondaryRateLimitExceededException
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.io.InputStream
import java.net.URI
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.TimeSource

internal const val MAX_CONCURRENT_REQUESTS_REGISTRY_KEY = "github.api.max.concurrent.requests"

/**
 * The default maximum number of concurrent requests.
 * GitHub allows no more than 100 concurrent requests:
 * https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2026-03-10#about-secondary-rate-limits.
 * The default stays below that limit.
 */
internal const val DEFAULT_MAX_CONCURRENT_REQUESTS = 50

/**
 * The time that the requests of a budget fail without a call to the server
 * after a primary rate limit error that doesn't report the reset time.
 */
private val PRIMARY_RATE_LIMIT_COOLDOWN: Duration = 1.minutes

/**
 * The pause after a secondary rate limit error without the `Retry-After` header.
 * GitHub asks to wait at least one minute:
 * https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api?apiVersion=2026-03-10#handle-rate-limit-errors-appropriately.
 */
private val SECONDARY_RATE_LIMIT_WAIT: Duration = 1.minutes

/**
 * Limits GitHub API requests to one server with one token.
 *
 * All executors share the limits, so they hold no matter how many executors the plugin creates.
 * The host and the port of the request URL identify the server.
 * GitHub counts the limits for each user, and for each IP address without a token,
 * so each token and the requests without a token have limits of their own.
 * Two tokens of the same user have separate limits here, then the responses of each token correct its budget.
 *
 * Three limits apply:
 * * the number of concurrent requests, which the registry key [MAX_CONCURRENT_REQUESTS_REGISTRY_KEY] sets;
 * * the primary rate limit of GitHub, separately for GraphQL and core REST requests.
 *   When a response reports that no requests remain, the next requests of the same kind fail without a call to the server until
 *   the limit resets.
 *   A rate limit error without the reset time blocks them for [PRIMARY_RATE_LIMIT_COOLDOWN].
 *   See https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2026-03-10#about-primary-rate-limits.
 * * the secondary rate limits of GitHub.
 *   After a secondary rate limit error, the next requests to the server with the same token fail without a call to the server
 *   for the time that GitHub asks for.
 *   The failed request isn't sent again.
 *   See https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2026-03-10#about-secondary-rate-limits.
 *
 * The GraphQL API reports both rate limit errors in a response with the status 200, so the errors of the response tell about them:
 * https://docs.github.com/en/graphql/overview/rate-limits-and-query-limits-for-the-graphql-api#exceeding-the-rate-limit.
 */
@Service
internal class GHApiRateLimiter {
  /**
   * The limit reads the registry for each request, so a change of the registry key needs no restart.
   */
  private val maxConcurrentRequests: Int
    get() = Registry.intValue(MAX_CONCURRENT_REQUESTS_REGISTRY_KEY, DEFAULT_MAX_CONCURRENT_REQUESTS).coerceAtLeast(1)

  private val servers = ConcurrentHashMap<String, ServerState>()

  /**
   * Sends the [request] with the [token], or without a token if it is `null`, and then parses the response.
   *
   * [send] sends the request and returns the response before the body is read.
   * The limiter records the budget from the rate limit headers of the response, also for a response with an error.
   * Then [parse] checks the status and reads the body.
   * The limiter records the rate limit errors that [parse] throws.
   */
  @Throws(GithubRateLimitExceededException::class)
  suspend fun <T> execute(
    request: GithubApiRequest<T>,
    token: String?,
    send: suspend () -> HttpResponse<out InputStream>,
    parse: suspend (response: HttpResponse<out InputStream>, headers: GHRateLimitHeaders) -> T,
  ): T {
    val server = serverState(request.url, token)
    // a request without a budget fails at once, before it waits for a permit
    server.checkPause()
    server.checkBudget(request)
    return server.semaphore(maxConcurrentRequests).withPermit {
      // the checks run again, because a response may start a pause or exhaust the budget during the wait for the permit
      server.checkPause()
      server.checkBudget(request)
      val response = send()
      val headers = GHRateLimitHeaders.parse(response)
      server.recordBudget(request, headers)
      try {
        parse(response, headers)
      }
      catch (e: GithubRateLimitExceededException) {
        if (e is GithubSecondaryRateLimitExceededException) {
          // the request fails, and the next requests to the server with the token fail until the pause ends
          server.pause(e.retryAfter ?: SECONDARY_RATE_LIMIT_WAIT)
        }
        else {
          // a 403, a 429, or the errors of a GraphQL response with the status 200
          server.recordPrimaryRateLimitError(request, headers)
        }
        throw e
      }
    }
  }

  private fun serverState(url: String, token: String?): ServerState {
    val tokenKey = tokenKey(token)
    val key = if (tokenKey == null) serverKey(url) else "${serverKey(url)} $tokenKey"
    return servers.computeIfAbsent(key) { ServerState(maxConcurrentRequests) }
  }

  /**
   * The map of the states keeps a hash of the token, not the token itself.
   *
   * @return the hash of the [token], or `null` for a request without a token
   */
  private fun tokenKey(token: String?): String? {
    if (token == null) return null
    return DigestUtil.sha256Hex(token.toByteArray())
  }

  /**
   * The host and the port of the [url] identify the server.
   * The scheme only gives the default port.
   */
  private fun serverKey(url: String): String {
    val uri = URI.create(url)
    val port = if (uri.port != -1) {
      uri.port
    }
    else when (uri.scheme?.lowercase()) {
      "http" -> 80
      "https" -> 443
      else -> null
    }
    val host = uri.host?.lowercase()
    return if (port == null) "$host" else "$host:$port"
  }
}

private fun GithubApiRequest<*>.budget(): Budget? = when (this) {
  is GithubApiRequest.Post.GQLQuery -> Budget.GRAPHQL
  is GithubApiRequest.Get.JsonSearchPage<*> -> null
  else -> Budget.REST
}

/**
 * The budget that the response [headers] of the [request] report.
 * Only the `core` and the `graphql` resources count, other resources (such as `search`) have budgets of their own.
 * When the headers don't name the resource, the kind of the request selects the budget.
 *
 * @return the budget, or `null` if no budget counts the request
 */
private fun budgetOf(request: GithubApiRequest<*>, headers: GHRateLimitHeaders): Budget? = when (headers.resource) {
  null -> request.budget()
  "core" -> Budget.REST
  "graphql" -> Budget.GRAPHQL
  else -> null
}

private class ServerState(maxConcurrentRequests: Int) {
  private val permits = AtomicReference(Permits(maxConcurrentRequests))

  /**
   * The semaphore with [maxConcurrentRequests] permits.
   * A new limit replaces the semaphore.
   * The requests that run or wait keep the old semaphore, so for a short time more requests than the new limit can run.
   */
  fun semaphore(maxConcurrentRequests: Int): Semaphore =
    permits.updateAndGet { if (it.count == maxConcurrentRequests) it else Permits(maxConcurrentRequests) }.semaphore

  /**
   * The time until which each budget has no requests left.
   */
  private val exhaustedUntil = ConcurrentHashMap<Budget, Instant>()

  /**
   * The end of the pause after a secondary rate limit error.
   * The monotonic time source keeps the pause correct when the system clock changes.
   */
  private val pausedUntil = AtomicReference<TimeSource.Monotonic.ValueTimeMark?>()

  fun pause(wait: Duration) {
    val until = TimeSource.Monotonic.markNow() + wait
    pausedUntil.updateAndGet { current -> if (current == null || until > current) until else current }
  }

  /**
   * Fails the request without a call to the server while the pause after a secondary rate limit error lasts.
   * The error reports the time left.
   */
  fun checkPause() {
    val until = pausedUntil.get() ?: return
    val timeLeft = -until.elapsedNow()
    if (!timeLeft.isPositive()) return
    val time = DateFormatUtil.formatTime((Clock.System.now() + timeLeft).toEpochMilliseconds())
    throw GithubSecondaryRateLimitExceededException(GithubBundle.message("rate.limit.secondary.exceeded.until", time), timeLeft)
  }

  /**
   * Records the budget of the [request] as exhausted until the reset when the response [headers] report no remaining requests.
   * [budgetOf] selects the budget.
   *
   * A response with remaining requests doesn't unblock the budget.
   * Such a response arrives late from the window that the block covers, because no new requests go out during the block.
   */
  fun recordBudget(request: GithubApiRequest<*>, headers: GHRateLimitHeaders) {
    val budget = budgetOf(request, headers) ?: return
    if (headers.remaining != 0) return
    val resetAt = headers.resetAt ?: return
    exhaustedUntil.merge(budget, resetAt, ::maxOf)
  }

  /**
   * Records the primary rate limit error of the [request] when the response [headers] don't report the full budget,
   * as on some GitHub Enterprise versions.
   * Then the budget counts as exhausted until the reset that is known, or for [PRIMARY_RATE_LIMIT_COOLDOWN].
   * An error about another resource (such as `search`) doesn't block requests.
   */
  fun recordPrimaryRateLimitError(request: GithubApiRequest<*>, headers: GHRateLimitHeaders) {
    val budget = budgetOf(request, headers) ?: return
    // recordBudget has already recorded the budget
    if (headers.remaining == 0 && headers.resetAt != null) return
    markExhausted(budget, headers.resetAt)
  }

  /**
   * Marks the [budget] as exhausted until [resetAt], or until the end of the current block, or for [PRIMARY_RATE_LIMIT_COOLDOWN].
   */
  private fun markExhausted(budget: Budget, resetAt: Instant?) {
    val now = Clock.System.now()
    exhaustedUntil.compute(budget) { _, current ->
      resetAt?.takeIf { it > now }
      ?: current?.takeIf { it > now }
      ?: (now + PRIMARY_RATE_LIMIT_COOLDOWN)
    }
  }

  fun checkBudget(request: GithubApiRequest<*>) {
    val budget = request.budget() ?: return
    val until = exhaustedUntil[budget] ?: return
    if (Clock.System.now() >= until) return
    val resetTime = DateFormatUtil.formatTime(until.toEpochMilliseconds())
    throw GithubRateLimitExceededException(GithubBundle.message("rate.limit.exceeded.until", resetTime), until)
  }
}

private enum class Budget { GRAPHQL, REST }

/**
 * A semaphore with the [count] of its permits.
 */
private class Permits(val count: Int) {
  val semaphore = Semaphore(count)
}
