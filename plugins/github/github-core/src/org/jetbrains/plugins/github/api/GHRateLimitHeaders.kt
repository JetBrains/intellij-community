// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import java.net.http.HttpResponse
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The rate limit headers of a GitHub API response.
 * A header that is missing or can't be parsed is `null`.
 *
 * See https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2026-03-10#checking-the-status-of-your-rate-limit.
 */
internal class GHRateLimitHeaders(
  val resource: String?,
  val remaining: Int?,
  val resetAt: Instant?,
  val retryAfter: Duration?,
) {
  companion object {
    const val RATE_LIMIT_REMAINING_HEADER: String = "X-RateLimit-Remaining"
    const val RATE_LIMIT_RESOURCE_HEADER: String = "X-RateLimit-Resource"
    const val RATE_LIMIT_RESET_HEADER: String = "X-RateLimit-Reset"
    const val RETRY_AFTER_HEADER: String = "Retry-After"

    fun parse(header: (String) -> String?): GHRateLimitHeaders = GHRateLimitHeaders(
      resource = header(RATE_LIMIT_RESOURCE_HEADER),
      remaining = header(RATE_LIMIT_REMAINING_HEADER)?.toIntOrNull(),
      resetAt = header(RATE_LIMIT_RESET_HEADER)?.toLongOrNull()?.let(Instant::fromEpochSeconds),
      // GitHub sends the number of seconds, not an HTTP date
      retryAfter = header(RETRY_AFTER_HEADER)?.toLongOrNull()?.seconds,
    )

    fun parse(response: HttpResponse<*>): GHRateLimitHeaders = parse { response.headers().firstValue(it).getOrNull() }
  }
}
