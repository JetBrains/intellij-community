// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal const val MAX_CONCURRENT_REQUESTS_REGISTRY_KEY = "github.api.max.concurrent.requests"

/**
 * The default maximum number of concurrent requests.
 * GitHub allows no more than 100 concurrent requests:
 * https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2026-03-10#about-secondary-rate-limits.
 * The default stays below that limit.
 */
internal const val DEFAULT_MAX_CONCURRENT_REQUESTS = 50

/**
 * Limits GitHub API requests to one server.
 *
 * All executors share the limits, so they hold no matter how many executors the plugin creates.
 * The host and the port of the request URL identify the server.
 *
 * One limit applies:
 * * the number of concurrent requests, which the registry key [MAX_CONCURRENT_REQUESTS_REGISTRY_KEY] sets.
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
   * Runs the [action] that sends the [request].
   */
  suspend fun <T> execute(request: GithubApiRequest<T>, action: suspend () -> T): T =
    serverState(request.url).semaphore(maxConcurrentRequests).withPermit {
      action()
    }

  private fun serverState(url: String): ServerState = servers.computeIfAbsent(serverKey(url)) { ServerState(maxConcurrentRequests) }

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

private class ServerState(maxConcurrentRequests: Int) {
  private val permits = AtomicReference(Permits(maxConcurrentRequests))

  /**
   * The semaphore with [maxConcurrentRequests] permits.
   * A new limit replaces the semaphore.
   * The requests that run or wait keep the old semaphore, so for a short time more requests than the new limit can run.
   */
  fun semaphore(maxConcurrentRequests: Int): Semaphore =
    permits.updateAndGet { if (it.count == maxConcurrentRequests) it else Permits(maxConcurrentRequests) }.semaphore
}

/**
 * A semaphore with the [count] of its permits.
 */
private class Permits(val count: Int) {
  val semaphore = Semaphore(count)
}
