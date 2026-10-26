// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.net.PlatformHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class SeDecisionsActionsRankerSessionTest {
  private lateinit var server: HttpServer
  private lateinit var client: HttpClient

  @Volatile
  private var responseStatus: Int = 200

  @Volatile
  private var responseBody: String = """{"answers":[]}"""

  @Volatile
  private var requestPath: String? = null

  @Volatile
  private var requestKey: String? = null

  @Volatile
  private var requestAuthorization: String? = null

  @Volatile
  private var requestBody: String = ""

  private val requestCount = AtomicInteger()

  @BeforeEach
  fun startServer() {
    server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/") { exchange ->
      requestCount.incrementAndGet()
      requestPath = exchange.requestURI.path
      requestKey = exchange.requestHeaders.getFirst(KEY_HEADER)
      requestAuthorization = exchange.requestHeaders.getFirst("Authorization")
      requestBody = exchange.requestBody.readAllBytes().decodeToString()
      val bytes = responseBody.encodeToByteArray()
      if (responseStatus == 401) {
        // The OpenAI API sends this challenge. Without it, the JDK client with an authenticator throws an IOException instead of the status.
        exchange.responseHeaders.add("WWW-Authenticate", """Bearer realm="OpenAI API"""")
      }
      exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
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
  fun `sends one choice question with a choice for each candidate and one for no action`() = runBlocking {
    createSession(this).rank(QUERY, CANDIDATES)

    assertEquals("/v1/decisions", requestPath)
    assertEquals(KEY_VALUE, requestKey)
    assertTrue(requestBody.contains(""""model":"test-model""""), requestBody)
    assertTrue(requestBody.contains(""""input":"$QUERY""""), requestBody)
    assertTrue(requestBody.contains(""""type":"choice","name":"action""""), requestBody)
    assertTrue(requestBody.contains("""{"value":"a0","description":"Zoom In: Increase the IDE scale"}"""), requestBody)
    assertTrue(requestBody.contains("""{"value":"a1","description":"Zoom Out"}"""), requestBody)
    assertTrue(requestBody.contains("""{"value":"a2","description":"Find: Find text in the file"}"""), requestBody)
    assertTrue(requestBody.contains("""{"value":"none","""), requestBody)
  }

  @Test
  fun `a request with the maximum number of candidates fits the choice limit of the Decision API`() = runBlocking {
    val session = createSession(this)
    val candidates = List(session.maxCandidates) { SeActionsRankerCandidate("Action$it", "Action $it", null) }

    session.rank(QUERY, candidates)

    assertEquals(255, Regex("""\{"value":""").findAll(requestBody).count())
  }

  @Test
  fun `the Authorization header of the lease reaches the endpoint`() = runBlocking {
    val lease = TestLease(endpointUrl(), mapOf("Authorization" to "Bearer seat-token"))

    SeDecisionsActionsRankerSession(lease, "test-model", client, this).rank(QUERY, CANDIDATES)

    assertEquals("Bearer seat-token", requestAuthorization)
  }

  @Test
  fun `orders the candidates by probability and drops the unlikely and unknown choices`() = runBlocking {
    responseBody = """
      {"answers":[{"name":"action","choice":"a0","confidence":0.9,"probabilities":[
        {"value":"a2","probability":0.08},
        {"value":"none","probability":0.2},
        {"value":"a0","probability":0.7},
        {"value":"a1","probability":0.005},
        {"value":"unknown","probability":0.015}
      ]}]}
    """.trimIndent()

    val ranked = createSession(this).rank(QUERY, CANDIDATES)

    assertEquals(listOf(SeActionsRankerMatch("ZoomIn", 0.7), SeActionsRankerMatch("Find", 0.08)), ranked)
  }

  @Test
  fun `uses the choice when the answer has no probabilities`() = runBlocking {
    responseBody = """{"answers":[{"name":"action","choice":"a1"}]}"""

    val ranked = createSession(this).rank(QUERY, CANDIDATES)

    assertEquals(listOf(SeActionsRankerMatch("ZoomOut", probability = null)), ranked)
  }

  @Test
  fun `returns nothing for a refusal`() = runBlocking {
    responseBody = """{"answers":[{"name":"action","type":"refusal","refusal":"The request is not allowed."}]}"""

    val ranked = createSession(this).rank(QUERY, CANDIDATES)

    assertEquals(emptyList<SeActionsRankerMatch>(), ranked)
  }

  @Test
  fun `returns nothing when the endpoint refuses the request`() = runBlocking {
    responseStatus = 401
    responseBody = """{"error":"Unauthorized"}"""

    val ranked = createSession(this).rank(QUERY, CANDIDATES)

    assertEquals(emptyList<SeActionsRankerMatch>(), ranked)
  }

  @Test
  fun `a refusing status tells the lease once and stops the requests of the popup`() = runBlocking {
    for (status in listOf(401, 403, 429)) {
      responseStatus = status
      requestCount.set(0)
      val lease = TestLease(endpointUrl())
      val session = SeDecisionsActionsRankerSession(lease, "test-model", client, this)

      session.rank(QUERY, CANDIDATES)
      session.rank(QUERY, CANDIDATES)

      assertEquals(listOf(status), lease.refusedStatusCodes, "status $status")
      assertEquals(1, requestCount.get(), "status $status")
    }
  }

  @Test
  fun `a server error does not stop the requests of the popup`() = runBlocking {
    responseStatus = 500
    val lease = TestLease(endpointUrl())
    val session = SeDecisionsActionsRankerSession(lease, "test-model", client, this)

    session.rank(QUERY, CANDIDATES)
    session.rank(QUERY, CANDIDATES)

    assertEquals(emptyList<Int>(), lease.refusedStatusCodes)
    assertEquals(2, requestCount.get())
  }

  @Test
  fun `a header value that is not valid sends nothing and reaches no log`() = runBlocking {
    val secret = "sk-\u200Bsecret"
    val lease = TestLease(endpointUrl(), mapOf("Authorization" to "Bearer $secret"))
    val logged = CopyOnWriteArrayList<String>()
    val processor = object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        logged += message
        logged += t?.stackTraceToString().orEmpty()
        return false
      }
    }

    val ranked = LoggedErrorProcessor.executeWith(processor).use {
      SeDecisionsActionsRankerSession(lease, "test-model", client, this).rank(QUERY, CANDIDATES)
    }

    assertEquals(emptyList<SeActionsRankerMatch>(), ranked)
    assertEquals(0, requestCount.get())
    assertTrue(logged.isNotEmpty())
    assertTrue(logged.none { it.contains("secret") }, logged.toString())
  }

  @Test
  fun `the System One request takes the query in the state and the choices as criteria`() = runBlocking {
    createSession(this, SeDecisionsApi.TYPESAFE).rank(QUERY, CANDIDATES)

    assertEquals("/v1/systemone", requestPath)
    assertEquals(KEY_VALUE, requestKey)
    assertTrue(requestBody.contains(""""model":"test-model""""), requestBody)
    assertTrue(requestBody.contains(""""state":{"request":"$QUERY"}"""), requestBody)
    assertTrue(requestBody.contains(""""questions":{"action":{"type":"choice","instructions":"""), requestBody)
    assertTrue(requestBody.contains(""""criteria":{"a0":"Zoom In: Increase the IDE scale","a1":"Zoom Out","a2":"""), requestBody)
    assertTrue(requestBody.contains(""""a2":"Find: Find text in the file","none":"""), requestBody)
  }

  @Test
  fun `the OpenRouter request goes to the decisions operation with the System One body`() = runBlocking {
    createSession(this, SeDecisionsApi.OPENROUTER).rank(QUERY, CANDIDATES)

    assertEquals("/v1/decisions", requestPath)
    assertTrue(requestBody.contains(""""state":{"request":"$QUERY"}"""), requestBody)
    assertTrue(requestBody.contains(""""criteria":{"""), requestBody)
  }

  @Test
  fun `orders the System One answer by probability and drops the unlikely and unknown choices`() = runBlocking {
    responseBody = """
      {"answers":{"action":{"type":"choice","choice":"a0","confidence":0.7,"probabilities":{
        "a2":0.08,"none":0.2,"a0":0.7,"a1":0.005,"unknown":0.015
      }}},"model":"jev-1.14","usage":{"input_tokens":476,"output_tokens":70}}
    """.trimIndent()

    val ranked = createSession(this, SeDecisionsApi.TYPESAFE).rank(QUERY, CANDIDATES)

    assertEquals(listOf(SeActionsRankerMatch("ZoomIn", 0.7), SeActionsRankerMatch("Find", 0.08)), ranked)
  }

  @Test
  fun `returns nothing for a System One refusal`() = runBlocking {
    responseBody = """{"answers":{"action":{"type":"refusal"}},"model":"jev-1.14"}"""

    val ranked = createSession(this, SeDecisionsApi.TYPESAFE).rank(QUERY, CANDIDATES)

    assertEquals(emptyList<SeActionsRankerMatch>(), ranked)
  }

  @Test
  fun `the disposal closes the lease`() {
    val lease = TestLease(endpointUrl())

    // The scope of runBlocking waits for the coroutine that closes the lease.
    runBlocking {
      Disposer.dispose(SeDecisionsActionsRankerSession(lease, "test-model", client, this))
    }

    assertTrue(lease.closed)
  }

  private fun createSession(scope: CoroutineScope, api: SeDecisionsApi = SeDecisionsApi.OPENAI): SeDecisionsActionsRankerSession {
    return SeDecisionsActionsRankerSession(TestLease(endpointUrl()), "test-model", client, scope, api)
  }

  private fun endpointUrl(): String = "http://127.0.0.1:${server.address.port}/v1"

  private class TestLease(
    override val url: String,
    override val headers: Map<String, String> = mapOf(KEY_HEADER to KEY_VALUE),
  ) : SeDecisionsEndpointLease {

    @Volatile
    var closed: Boolean = false
      private set

    val refusedStatusCodes: MutableList<Int> = CopyOnWriteArrayList()

    override suspend fun close() {
      closed = true
    }

    override fun refused(statusCode: Int) {
      refusedStatusCodes += statusCode
    }
  }

  private companion object {
    const val KEY_HEADER: String = "x-test-proxy-key"
    const val KEY_VALUE: String = "test-secret"
    const val QUERY: String = "too small, cannot see anything"

    val CANDIDATES: List<SeActionsRankerCandidate> = listOf(
      SeActionsRankerCandidate("ZoomIn", "Zoom In", "Increase the IDE scale"),
      SeActionsRankerCandidate("ZoomOut", "Zoom Out", null),
      SeActionsRankerCandidate("Find", "Find", "Find text in the file"),
    )
  }
}
