// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream

internal data class TestRepo(val name: String)

private const val TOKEN = "test-token"

internal enum class TestedClient(val registryValue: Boolean) {
  JDK_11_HTTP(true),
  HTTP_REQUESTS(false)
}

internal enum class TestedAuth {
  TOKEN_AUTH,
  NO_AUTH
}

/**
 * Checks the contract of [GithubApiRequestExecutor] against a local HTTP server.
 *
 * Every case runs against both implementations and both authentications, see [TestedClient] and [TestedAuth].
 */
@TestApplication
@ParameterizedClass
@MethodSource("executors")
internal class GithubApiRequestExecutorTest(private val client: TestedClient, private val auth: TestedAuth) {
  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var server: HttpServer
  private lateinit var serverPath: GithubServerPath
  private lateinit var executor: GithubApiRequestExecutor

  private val received = CopyOnWriteArrayList<ReceivedRequest>()

  @Volatile
  private var answer: (HttpExchange) -> Unit = { respond(it, 200, "{}") }

  @BeforeEach
  fun startServer() {
    Registry.get(GithubApiRequestExecutor.Factory.JDK11_CLIENT_REGISTRY_KEY).setValue(client.registryValue, testDisposable)
    server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/") { exchange ->
      exchange.use {
        received.add(ReceivedRequest(it.requestMethod, it.requestURI.toString(), it.requestHeaders,
                                     it.requestBody.readBytes().toString(Charsets.UTF_8)))
        answer(it)
      }
    }
    server.start()
    serverPath = GithubServerPath(true, "127.0.0.1", server.address.port, null)
    val factory = GithubApiRequestExecutor.Factory.getInstance()
    executor = when (auth) {
      TestedAuth.TOKEN_AUTH -> factory.create(serverPath, TOKEN)
      TestedAuth.NO_AUTH -> factory.create()
    }
  }

  @AfterEach
  fun stopServer() {
    server.stop(0)
  }

  @Test
  fun `the registry key and the token select the implementation`() {
    assertInstanceOf(when {
                       client == TestedClient.JDK_11_HTTP -> GithubApiHelperRequestExecutor::class.java
                       auth == TestedAuth.TOKEN_AUTH -> GithubApiRequestExecutor.WithTokenAuth::class.java
                       else -> GithubApiRequestExecutor.NoAuth::class.java
                     }, executor)
  }

  @Test
  fun `a request carries the accept header and the user agent`(): Unit = timeoutRunBlocking {
    answer = { respond(it, 200, """{"name":"repo"}""") }

    val result = executor.execute(GithubApiRequest.Get.Json(url("/repos/owner/repo"), TestRepo::class.java))

    assertEquals(TestRepo("repo"), result)
    val request = received.single()
    assertEquals("GET", request.method)
    assertEquals("/api/v3/repos/owner/repo", request.uri)
    assertEquals(GithubApiContentHelper.V3_JSON_MIME_TYPE, request.header("Accept"))
    assertEquals("gzip", request.header("Accept-Encoding"))
    assertTrue(request.header("User-Agent").orEmpty().startsWith("IntelliJ-GitHub-Plugin"))
  }

  @Test
  fun `a request carries the token of the executor`(): Unit = timeoutRunBlocking {
    answer = { respond(it, 200, """{"name":"repo"}""") }

    executor.execute(GithubApiRequest.Get.Json(url("/repos/owner/repo"), TestRepo::class.java))

    if (auth == TestedAuth.TOKEN_AUTH) {
      assertEquals("Bearer $TOKEN", received.single().header("Authorization"))
    }
    else {
      assertEquals(null, received.single().header("Authorization"))
    }
  }

  @Test
  fun `a request to another host carries no token`(): Unit = timeoutRunBlocking {
    answer = { respond(it, 200, """{"name":"repo"}""") }

    // the server path holds the address, so the name of the same host is a foreign host
    val url = "http://localhost:${server.address.port}/repos/owner/repo"
    executor.execute(GithubApiRequest.Get.Json(url, TestRepo::class.java))

    assertTrue(received.single().header("Authorization").isNullOrEmpty())
  }

  @Test
  fun `a post request sends the body and the content type`(): Unit = timeoutRunBlocking {
    answer = { respond(it, 201, """{"name":"created"}""") }

    val result = executor.execute(GithubApiRequest.Post.Json(url("/repos"), TestRepo("new"), TestRepo::class.java))

    assertEquals(TestRepo("created"), result)
    val request = received.single()
    assertEquals("POST", request.method)
    assertEquals("""{"name":"new"}""", request.body)
    assertEquals(GithubApiContentHelper.JSON_MIME_TYPE, request.header("Content-Type"))
  }

  @Test
  fun `an optional request returns null on a missing resource`(): Unit = timeoutRunBlocking {
    answer = { respond(it, 404, """{"message":"Not Found"}""") }

    val result = executor.execute(GithubApiRequest.Get.Optional.Json(url("/repos/owner/repo"), TestRepo::class.java))

    assertNull(result)
  }

  @Test
  fun `a failed request reports the message of the server`(): Unit = timeoutRunBlocking {
    answer = { respond(it, 500, """{"message":"Server is down"}""") }

    val error = assertThrows<GithubStatusCodeException> {
      executor.execute(GithubApiRequest.Get.Json(url("/repos/owner/repo"), TestRepo::class.java))
    }

    assertEquals(500, error.statusCode)
    assertTrue(error.message.orEmpty().contains("Server is down"))
  }

  @Test
  fun `a rejected request reports an authentication error`(): Unit = timeoutRunBlocking {
    answer = { respond(it, 401, """{"message":"Bad credentials"}""") }

    val error = assertThrows<IOException> {
      executor.execute(GithubApiRequest.Get.Json(url("/repos/owner/repo"), TestRepo::class.java))
    }

    assertInstanceOf(GithubAuthenticationException::class.java, error)
  }

  @Test
  fun `a page request reads the link to the next page`(): Unit = timeoutRunBlocking {
    val nextUrl = url("/repos/owner/repo/issues?page=2")
    answer = {
      it.responseHeaders.add("Link", """<$nextUrl>; rel="next"""")
      respond(it, 200, """[{"name":"first"}]""")
    }

    val page = executor.execute(GithubApiRequest.Get.JsonPage(url("/repos/owner/repo/issues"), TestRepo::class.java))

    assertEquals(listOf(TestRepo("first")), page.items)
    assertEquals(nextUrl, page.nextLink)
  }

  @Test
  fun `a compressed answer is read`(): Unit = timeoutRunBlocking {
    val body = ByteArrayOutputStream()
    GZIPOutputStream(body).use { it.write("""{"name":"compressed"}""".toByteArray()) }
    answer = {
      it.responseHeaders.add("Content-Encoding", "gzip")
      respond(it, 200, body.toByteArray())
    }

    val result = executor.execute(GithubApiRequest.Get.Json(url("/repos/owner/repo"), TestRepo::class.java))

    assertEquals(TestRepo("compressed"), result)
  }

  @Test
  fun `the blocking call runs the request`() {
    answer = { respond(it, 200, """{"name":"repo"}""") }

    val result = executor.execute(EmptyProgressIndicator(),
                                  GithubApiRequest.Get.Json(url("/repos/owner/repo"), TestRepo::class.java))

    assertEquals(TestRepo("repo"), result)
  }

  private fun url(path: String): String = "${serverPath.toApiUrl()}$path"

  private fun respond(exchange: HttpExchange, code: Int, body: String) = respond(exchange, code, body.toByteArray())

  private fun respond(exchange: HttpExchange, code: Int, body: ByteArray) {
    exchange.responseHeaders.add("Content-Type", GithubApiContentHelper.JSON_MIME_TYPE)
    exchange.sendResponseHeaders(code, body.size.toLong())
    exchange.responseBody.write(body)
  }

  private class ReceivedRequest(val method: String, val uri: String, private val headers: Headers, val body: String) {
    fun header(name: String): String? = headers.getFirst(name)
  }

  companion object {
    @JvmStatic
    fun executors(): List<Arguments> =
      TestedClient.entries.flatMap { client -> TestedAuth.entries.map { auth -> Arguments.of(client, auth) } }
  }
}
