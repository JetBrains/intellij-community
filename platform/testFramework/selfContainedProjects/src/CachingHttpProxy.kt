// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects

import com.google.common.util.concurrent.Striped
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.withLock
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

@OptIn(ExperimentalAtomicApi::class)
class CachingHttpProxy(
  listen: InetSocketAddress,
  private val cacheDir: Path,
  var offline: Boolean,
  private val useInsecureHttp: Boolean = false,
): Closeable {
  private val LOG = LoggerFactory.getLogger("CachingHttpProxy")

  init {
      if (useInsecureHttp) {
        require(isJUnitTest()) { "useInsecureHttp=true is only allowed in JUnit tests" }
      }
  }

  private val hadErrorsHolder = AtomicBoolean(false)

  /**
   * Check this in tests to be 100% sure proxy works
   */
  val hadErrors: Boolean
    get() = hadErrorsHolder.load()

  private val fetchCountHolder = AtomicLong(0)
  val fetchCount: Long
    get() = fetchCountHolder.load()

  private val hitCountHolder = AtomicLong(0)
  val hitCount: Long
    get() = hitCountHolder.load()

  private val proxyPath = "proxy"

  private val server = HttpServer.create(listen, 0).also { server ->
    server.createContext("/$proxyPath") { exchange ->
      try {
        handleProxyRequest(exchange)
      }
      catch (e: Exception) {
        try {
          sendError(exchange, 500, "Internal proxy error: ${e.message}", e)
        }
        catch (_: Exception) {
          // Response may already be sent
        }
      }
    }

    server.executor = Executors.newFixedThreadPool(10)
    server.start()
  }

  override fun close() {
    server.stop(0)
  }

  val port: Int
    get() = server.address.port

  fun sendError(exchange: HttpExchange, httpCode: Int, message: String, e: Throwable? = null) {
    LOG.error("Error handling ${exchange.requestURI}: $httpCode $message", e)
    hadErrorsHolder.store(true)

    val errorBody = message.toByteArray()
    exchange.sendResponseHeaders(httpCode, errorBody.size.toLong())
    exchange.responseBody.use { it.write(errorBody) }
  }

  val proxyUrl: String by lazy {
    val host = server.address.address.hostAddress.let {
      if (it.contains(":")) {
        "[$it]"
      } else {
        it
      }
    }
    "http://$host:${server.address.port}/$proxyPath"
  }

  private fun handleProxyRequest(exchange: HttpExchange) {
    val rawPath = exchange.requestURI.path.removePrefixStrict("/proxy/")

    val protocol = if (useInsecureHttp) "http" else "https"

    // Strip query string, it's not used for purposes of maven repositories caching
    val cleanPath = rawPath.substringBefore("?")
    val targetUrl = "$protocol://$cleanPath"

    val isHeadRequest = exchange.requestMethod.equals("HEAD", ignoreCase = true)

    LOG.debug("REQUEST: ${exchange.requestMethod} $targetUrl")

    val cachePath = getCachePath(cleanPath)

    val lock = Striped.lock(256)
    lock.get(cachePath).withLock {
      val headersPath = getHeadersPath(cleanPath)

      when {
        Files.exists(cachePath) -> {
          // CACHE HIT
          LOG.debug("CACHE HIT: $targetUrl (isHead=$isHeadRequest)")
          hitCountHolder.incrementAndFetch()
          serveCached(exchange, cachePath, headersPath, isHeadRequest)
        }

        offline -> {
          // OFFLINE - cache miss is an error
          LOG.error("CACHE MISS (offline): $targetUrl")
          hadErrorsHolder.store(true)

          sendError(exchange, 503, """
              Not found in cache: $targetUrl at $cachePath
              This proxy is running in OFFLINE mode.
            """.trimIndent())
        }

        else -> {
          // CACHE MISS - fetch from upstream
          LOG.info("CACHE MISS (fetching): $targetUrl (isHead=$isHeadRequest)")
          fetchCountHolder.incrementAndFetch()
          fetchAndCache(exchange, targetUrl, cleanPath, cachePath, headersPath, isHeadRequest)
        }
      }
      LOG.debug("RESPONSE COMPLETED: ${exchange.requestMethod} $targetUrl")
    }
  }

  private fun serveCached(exchange: HttpExchange, cachePath: Path, headersPath: Path, isHeadRequest: Boolean) {
    val bytes = Files.readAllBytes(cachePath)

    LOG.debug("serveCached: path={}, size={}, isHead={}", cachePath, bytes.size, isHeadRequest)

    var cachedStatusCode: Int? = null
    Files.readAllLines(headersPath).forEach { line ->
      val parts = line.split(": ", limit = 2)
      check(parts.size == 2) {
        "Invalid header line: $line in $headersPath"
      }

      val headerName = parts[0]
      val headerValue = parts[1]

      // Skip headers that we'll set ourselves, cause issues, or are HTTP/2 pseudo-headers
      if (!headerName.equals("content-length", ignoreCase = true) &&
          !headerName.equals("transfer-encoding", ignoreCase = true) &&
          !headerName.startsWith(":")) {  // HTTP/2 pseudo-headers like :status
        if (headerName == CACHED_STATUS_CODE_FAKE_HEADER) {
          cachedStatusCode = headerValue.toIntOrNull() ?: 200
        }
        else {
          exchange.responseHeaders.add(headerName, headerValue)
        }
      }
    }

    if (cachedStatusCode == null) {
      sendError(exchange, 500, "Cached response has no $CACHED_STATUS_CODE_FAKE_HEADER header in $headersPath")
      return
    }

    // JDK HttpServer bug workaround: when passing content-length to sendResponseHeaders for HEAD,
    // it logs a warning and STRIPS the Content-Length header from the response.
    // Solution: manually set Content-Length header and use -1 for sendResponseHeaders length.
    if (isHeadRequest) {
      exchange.responseHeaders["Content-Length"] = listOf(bytes.size.toString())
      LOG.debug("serveCached: HEAD - set Content-Length=${bytes.size}, calling sendResponseHeaders(status=$cachedStatusCode, length=-1)")
      exchange.sendResponseHeaders(cachedStatusCode, -1)
      exchange.responseBody.close()  // Close to finalize the response
    }
    else {
      LOG.debug("serveCached: GET - calling sendResponseHeaders(status=$cachedStatusCode, length=${bytes.size})")
      exchange.sendResponseHeaders(cachedStatusCode, bytes.size.toLong())
      LOG.debug("serveCached: writing ${bytes.size} bytes to response body")
      exchange.responseBody.use { it.write(bytes) }
    }
    LOG.debug("serveCached: done")
  }

  private fun fetchAndCache(
    exchange: HttpExchange,
    targetUrl: String,
    cleanPath: String,
    cachePath: Path,
    headersPath: Path,
    isHeadRequest: Boolean,
  ) {
    val requestBuilder = HttpRequest.newBuilder()
      .uri(URI.create(targetUrl))
      .timeout(Duration.ofMinutes(5))

    // Forward Authorization header from client
    exchange.requestHeaders.getFirst("Authorization")?.let {
      requestBuilder.header("Authorization", it)
    }

    // Always fetch with GET to get content for caching
    val request = requestBuilder.GET().build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
    val statusCode = response.statusCode()
    val bytes = response.body()

    val forwardedHeaders = response.headers().map().filterKeys { header ->
      !header.equals("transfer-encoding", ignoreCase = true) &&
      !header.equals("content-length", ignoreCase = true) &&
      !header.equals("date", ignoreCase = true) &&
      // HTTP/2 pseudo-headers like :status
      !header.startsWith(":")
    }

    // Only cache non-transient responses (not 5xx server errors)
    // Cache 200 OK, 404 Not Found, and other client errors the same way
    // Do NOT cache 401 Unauthorized or 407 Proxy Authentication Required as they are part of auth handshakes
    val authRequired = statusCode == 401 || statusCode == 407
    val shouldCache = statusCode < 500 && !authRequired

    if (shouldCache) {
      cachePath.createParentDirectories().writeBytes(bytes)

      // Cache headers including our custom status code header
      val headers = mutableListOf("$CACHED_STATUS_CODE_FAKE_HEADER: $statusCode")
      forwardedHeaders.forEach { (header, values) ->
        for (value in values) {
          headers.add("$header: $value")
        }
      }
      headersPath.writeText(headers.sorted().joinToString("\n"))

      LOG.debug("CACHED: $cleanPath (${bytes.size} bytes, status $statusCode)")
    }
    else {
      LOG.warn("NOT CACHING (transient error): $cleanPath (status $statusCode)")
      if (!authRequired) {
        hadErrorsHolder.store(true)
      }
    }

    // Forward headers to client
    forwardedHeaders.forEach { (header, values) ->
      for (value in values) {
        exchange.responseHeaders.add(header, value)
      }
    }

    // JDK HttpServer bug workaround: manually set Content-Length for HEAD requests
    if (isHeadRequest) {
      exchange.responseHeaders["Content-Length"] = listOf(bytes.size.toString())
      LOG.debug("fetchAndCache: HEAD - set Content-Length=${bytes.size}, calling sendResponseHeaders(status=$statusCode, length=-1)")
      exchange.sendResponseHeaders(statusCode, -1)
      exchange.responseBody.close()
    }
    else {
      LOG.debug("fetchAndCache: GET - calling sendResponseHeaders(status=$statusCode, length=${bytes.size})")
      exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
  }

  private fun getCachePath(urlPath: String): Path = cacheDir.resolve(urlPath)
  private fun getHeadersPath(urlPath: String): Path = cacheDir.resolve("$urlPath.headers")

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val proxy = CachingHttpProxy(InetSocketAddress(8080), Path.of("/Users/jetbrains/work/tmp/cache"), false)
      System.`in`.read()
    }
  }
}

private const val CACHED_STATUS_CODE_FAKE_HEADER = "SELFCONTAINED-PROXY-X-Cached-Status-Code"

private fun String.removePrefixStrict(prefix: String): String {
  val result = removePrefix(prefix)
  check(result != this) {
    "String must start with $prefix but was: $this"
  }
  return result
}

private fun isJUnitTest(): Boolean {
  for (element in Thread.currentThread().stackTrace) {
    if (element.className.startsWith("org.junit.")) {
      return true
    }
  }
  return false
}

private val httpClient: HttpClient = HttpClient.newBuilder()
  .followRedirects(HttpClient.Redirect.ALWAYS)
  .connectTimeout(Duration.ofSeconds(30))
  .build()
