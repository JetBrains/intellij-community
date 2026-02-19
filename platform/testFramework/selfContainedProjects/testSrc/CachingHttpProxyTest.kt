// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects

import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CachingHttpProxyTest {

  private lateinit var mockServer: HttpServer
  private var mockPort: Int = 0
  private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  @TempDir
  lateinit var cacheDir: Path

  @BeforeAll
  fun startMockServer() {
    mockServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    mockPort = mockServer.address.port

    // /status/200 - returns 200 with body
    mockServer.createContext("/status/200") { exchange ->
      val body = "OK response body".toByteArray()
      exchange.responseHeaders.add("X-Custom-Header", "test-value")
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }

    // /status/404 - returns 404 with body
    mockServer.createContext("/status/404") { exchange ->
      val body = "Not found".toByteArray()
      exchange.sendResponseHeaders(404, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }

    // /status/500 - returns 500 server error
    mockServer.createContext("/status/500") { exchange ->
      val body = "Internal server error".toByteArray()
      exchange.sendResponseHeaders(500, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }

    // /redirect - returns 302 redirects to /status/200
    mockServer.createContext("/redirect") { exchange ->
      exchange.responseHeaders.add("Location", "http://127.0.0.1:$mockPort/status/200")
      exchange.sendResponseHeaders(302, -1)
      exchange.responseBody.close()
    }

    // /with-headers - returns 200 with multiple custom headers
    mockServer.createContext("/with-headers") { exchange ->
      val body = "response with headers".toByteArray()
      exchange.responseHeaders.add("X-Custom-One", "value-one")
      exchange.responseHeaders.add("X-Custom-Two", "value-two")
      exchange.responseHeaders.add("X-Custom-Three", "value-three")
      exchange.responseHeaders.add("Cache-Control", "max-age=3600")
      exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }

    // /auth-check - returns 200 only if Authorization header is present
    mockServer.createContext("/auth-check") { exchange ->
      val authHeader = exchange.requestHeaders.getFirst("Authorization")
      if (authHeader != null && authHeader == "Bearer secret-token") {
        val body = "Authorized".toByteArray()
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
      } else {
        val body = "Unauthorized".toByteArray()
        exchange.sendResponseHeaders(401, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
      }
    }

    mockServer.executor = Executors.newFixedThreadPool(4)
    mockServer.start()
  }

  @AfterAll
  fun stopMockServer() {
    mockServer.stop(0)
  }

  @AfterEach
  fun cleanup() {
    stopProxy()
  }

  private var proxyHolder: CachingHttpProxy? = null
  private val proxy: CachingHttpProxy
    get() = proxyHolder ?: throw IllegalStateException("Proxy not initialized yet")

  private fun startProxy(offline: Boolean, allowHttp: Boolean) {
    stopProxy()

    proxyHolder = CachingHttpProxy(
      cacheDir = cacheDir,
      listen = InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
      useInsecureHttp = allowHttp,
      offline = offline,
    )
  }

  @AfterEach
  fun stopProxy() {
    proxyHolder?.close()
    proxyHolder = null
  }

  private fun makeRequest(path: String, method: String = "GET", headers: Map<String, String> = emptyMap()): HttpResponse<String> {
    val url = "http://127.0.0.1:${proxy.port}/proxy/127.0.0.1:$mockPort$path"
    val requestBuilder = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .timeout(Duration.ofSeconds(10))

    headers.forEach { (name, value) ->
      requestBuilder.header(name, value)
    }

    when (method.uppercase()) {
      "GET" -> requestBuilder.GET()
      "HEAD" -> requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody())
      else -> throw IllegalArgumentException("Unsupported method: $method")
    }

    return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
  }

  private fun getCachePath(path: String): Path {
    return cacheDir.resolve("127.0.0.1:$mockPort$path")
  }

  private fun getHeadersPath(path: String): Path {
    return getCachePath("$path.headers")
  }

  @Test
  fun `200 response is cached`() {
    startProxy(offline = false, allowHttp = true)

    val response = makeRequest("/status/200")
    assertEquals(200, response.statusCode())
    assertEquals("OK response body", response.body())

    assertFileContent(getCachePath("/status/200"), "OK response body")
    assertFileContent(getHeadersPath("/status/200"), """
      SELFCONTAINED-PROXY-X-Cached-Status-Code: 200
      x-custom-header: test-value
    """.trimIndent())

    assertFalse(proxy.hadErrors)
  }

  @Test
  fun `404 response is cached`() {
    startProxy(offline = false, allowHttp = true)

    val response = makeRequest("/status/404")
    assertEquals(404, response.statusCode())
    assertEquals("Not found", response.body())

    // Verify cache file exists (404 is non-transient, should be cached)
    assertFileContent(getCachePath("/status/404"), "Not found")
    assertFileContent(getHeadersPath("/status/404"), """
      SELFCONTAINED-PROXY-X-Cached-Status-Code: 404
    """.trimIndent())

    assertFalse(proxy.hadErrors)
  }

  @Test
  fun `500 response is not cached`() {
    startProxy(offline = false, allowHttp = true)

    val response = makeRequest("/status/500")
    assertEquals(500, response.statusCode())

    // Verify cache file does NOT exist (5xx is transient, should not be cached)
    val cachePath = getCachePath("/status/500")
    assertFalse(Files.exists(cachePath), "Cache file should NOT exist for 500")

    val headersPath = getHeadersPath("/status/500")
    assertFalse(Files.exists(headersPath), "Headers file should NOT exist for 500")

    assertTrue(proxy.hadErrors)
  }

  @Test
  fun `HEAD request returns content-length without body`() {
    startProxy(offline = false, allowHttp = true)

    // First, make a GET to populate cache
    val getResponse = makeRequest("/status/200", "GET")
    assertEquals(200, getResponse.statusCode())

    // Now make HEAD request
    val headResponse = makeRequest("/status/200", "HEAD")
    assertEquals(200, headResponse.statusCode())
    assertTrue(headResponse.body().isEmpty(), "HEAD response should have no body")

    // Verify Content-Length header is present
    val contentLength = headResponse.headers().firstValue("content-length")
    assertTrue(contentLength.isPresent, "Content-Length header should be present")
    assertEquals("16", contentLength.get()) // "OK response body" is 16 bytes

    assertFalse(proxy.hadErrors)
  }

  @Test
  fun `HEAD request is successful even if not in cache`() {
    startProxy(offline = false, allowHttp = true)

    // Make HEAD request directly (no prior GET)
    val headResponse = makeRequest("/status/200", "HEAD")
    assertEquals(200, headResponse.statusCode())
    assertTrue(headResponse.body().isEmpty(), "HEAD response should have no body")

    // Verify Content-Length header is present
    val contentLength = headResponse.headers().firstValue("content-length")
    assertTrue(contentLength.isPresent, "Content-Length header should be present")
    assertEquals("16", contentLength.get()) // "OK response body" is 16 bytes

    assertFalse(proxy.hadErrors)
  }

  @Test
  fun `offline mode serves cached content`() {
    // First populate cache with network access
    startProxy(offline = false, allowHttp = true)

    val response1 = makeRequest("/status/200")
    assertEquals(200, response1.statusCode())

    assertFalse(proxy.hadErrors)

    // Now start in offline mode and verify cache hit
    startProxy(offline = true, allowHttp = true)

    val response2 = makeRequest("/status/200")
    assertEquals(200, response2.statusCode())
    assertEquals("OK response body", response2.body())

    assertFalse(proxy.hadErrors)
  }

  @Test
  fun `offline mode returns 503 for cache miss`() {
    // Start in offline mode without populating cache
    startProxy(offline = true, allowHttp = true)

    val response = makeRequest("/status/200")
    assertEquals(503, response.statusCode())
    assertTrue(response.body().contains("Not found in cache"), "Should return offline error message")

    assertTrue(proxy.hadErrors)
  }

  @Test
  fun `redirects are followed and cached under original url`() {
    startProxy(offline = false, allowHttp = true)

    // Request the redirect URL - proxy should follow it and return final content
    val response = makeRequest("/redirect")
    // Note: The JDK HttpClient in proxy follows redirects, so we get 200 with final content
    assertEquals(200, response.statusCode())
    assertEquals("OK response body", response.body())

    // Verify content is cached under the ORIGINAL redirect URL (not the final URL)
    val cachePath = getCachePath("/redirect")
    assertTrue(Files.exists(cachePath), "Cache file should exist under original redirect URL")
    assertEquals("OK response body", Files.readString(cachePath))

    assertFalse(proxy.hadErrors)
  }

  @Test
  fun `upstream headers are stored in headers sidecar file`() {
    startProxy(offline = false, allowHttp = true)

    val response = makeRequest("/with-headers")
    assertEquals(200, response.statusCode())

    assertFileContent(getCachePath("/with-headers"), "response with headers")
    assertFileContent(getHeadersPath("/with-headers"), """
      SELFCONTAINED-PROXY-X-Cached-Status-Code: 200
      cache-control: max-age=3600
      content-type: text/plain; charset=utf-8
      x-custom-one: value-one
      x-custom-three: value-three
      x-custom-two: value-two
    """.trimIndent())

    assertFalse(proxy.hadErrors)
  }

  @Test
  fun `cached headers are served on cache hit`() {
    startProxy(offline = false, allowHttp = true)

    // First request - populates cache
    val response1 = makeRequest("/with-headers")
    assertEquals(200, response1.statusCode())

    // Verify first response has headers
    assertTrue(response1.headers().firstValue("x-custom-one").isPresent, "First response should have X-Custom-One")
    assertEquals("value-one", response1.headers().firstValue("x-custom-one").get())

    assertFalse(proxy.hadErrors)

    startProxy(offline = true, allowHttp = true)

    // Second request - should be served from cache with same headers
    val response2 = makeRequest("/with-headers")
    assertEquals(200, response2.statusCode())
    assertEquals("response with headers", response2.body())

    // Verify cached response has the same custom headers
    assertTrue(response2.headers().firstValue("x-custom-one").isPresent, "Cached response should have X-Custom-One")
    assertEquals("value-one", response2.headers().firstValue("x-custom-one").get())

    assertTrue(response2.headers().firstValue("x-custom-two").isPresent, "Cached response should have X-Custom-Two")
    assertEquals("value-two", response2.headers().firstValue("x-custom-two").get())

    assertTrue(response2.headers().firstValue("cache-control").isPresent, "Cached response should have Cache-Control")
    assertEquals("max-age=3600", response2.headers().firstValue("cache-control").get())

    assertFalse(proxy.hadErrors)
  }

  @Test
  fun `authorization header is bypassed to upstream in online mode`() {
    startProxy(offline = false, allowHttp = true)

    // Request with incorrect token
    val response1 = makeRequest("/auth-check", headers = mapOf("Authorization" to "Bearer wrong-token"))
    assertEquals(401, response1.statusCode())
    assertEquals("Unauthorized", response1.body())
    assertFalse(proxy.hadErrors)


    // Request with correct token
    val response2 = makeRequest("/auth-check", headers = mapOf("Authorization" to "Bearer secret-token"))
    assertEquals(200, response2.statusCode())
    assertEquals("Authorized", response2.body())
    assertFalse(proxy.hadErrors)
  }

  private fun assertFileContent(path: Path, expectedContent: String) {
    assertTrue(Files.exists(path), "File should exist at $path")
    if (path.readText() != expectedContent) {
      throw FileComparisonFailedError(
        "File content mismatch",
        expected = expectedContent,
        actual = Files.readString(path),
      )
    }
  }
}