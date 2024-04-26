// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.util.TimeoutUtil
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Condition
import org.junit.Rule
import org.junit.Test
import org.junit.rules.DisableOnDebug
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals

class HttpRequestsTest {
  private val LOCALHOST = "127.0.0.1"

  private lateinit var server: HttpServer
  private lateinit var url: String

  @JvmField
  @Rule(order = 1)
  val serverRule: TestRule = object : TestRule, AutoCloseable {
    override fun apply(base: Statement, description: Description): Statement {
      return object : Statement() {
        override fun evaluate() {
          start()
          use {
            base.evaluate()
          }
        }

        private fun start() {
          if (!::server.isInitialized) {
            server = HttpServer.create()
            server.bind(InetSocketAddress(LOCALHOST, 0), 1)
          }
          server.start()
          url = "http://$LOCALHOST:${server.address.port}"
        }
      }
    }

    override fun close() {
      if (::server.isInitialized) {
        server.stop(0)
      }
    }
  }

  @JvmField
  @Rule(order = 2)
  val timeout: TestRule = DisableOnDebug(Timeout(5, TimeUnit.SECONDS))

  @Test
  fun redirectLimit() {
    val requested = AtomicInteger()
    // infinite redirect
    server.createContext("/") { ex ->
      requested.incrementAndGet()
      ex.responseHeaders.add("Location", url)
      ex.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1)
      ex.close()
    }
    assertThatExceptionOfType(IOException::class.java)
      .isThrownBy { HttpRequests.request(url).redirectLimit(2).readString(null) }
      .withMessage(IdeCoreBundle.message("error.connection.failed.redirects"))
    assertEquals(2, requested.get())
  }

  @Test
  fun redirectWithSimplifiedLocation() {
    val requested1 = AtomicInteger()
    val requested2 = AtomicInteger()
    server.createContext("/") { ex ->
      requested1.incrementAndGet()
      ex.responseHeaders.add("Location", "/ok")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1)
      ex.close()
    }
    server.createContext("/ok") { ex ->
      requested2.incrementAndGet()
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.close()
    }
    assertThat(HttpRequests.request(url).redirectLimit(2).readString(null)).isEqualTo("")
    assertEquals(1, requested1.get())
    assertEquals(1, requested2.get())
  }

  @Test
  fun redirectLimitPositive() {
    assertThatExceptionOfType(IllegalArgumentException::class.java)
      .isThrownBy { HttpRequests.request("").redirectLimit(0).readString(null) }
      .withMessage("Redirect limit should be positive")
  }

  @Test
  fun readTimeout() {
    server.createContext("/") { ex ->
      TimeoutUtil.sleep(1000)
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.close()
    }
    assertThatExceptionOfType(SocketTimeoutException::class.java)
      .isThrownBy { HttpRequests.request(url).readTimeout(50).readString(null) }
  }

  @Suppress("NonAsciiCharacters", "SpellCheckingInspection")
  @Test
  fun readContent() {
    server.createContext("/") { ex ->
      ex.responseHeaders.add("Content-Type", "text/plain; charset=koi8-r; boundary=something")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.responseBody.write("hello кодировочки".toByteArray(charset("koi8-r")))
      ex.close()
    }
    assertThat(HttpRequests.request(url).readString(null)).isEqualTo("hello кодировочки")
  }

  @Suppress("NonAsciiCharacters", "SpellCheckingInspection")
  @Test
  fun gzippedContent() {
    server.createContext("/") { ex ->
      ex.responseHeaders.add("Content-Type", "text/plain; charset=koi8-r")
      ex.responseHeaders.add("Content-Encoding", "gzip")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      GZIPOutputStream(ex.responseBody).use { gzipOutputStream ->
        gzipOutputStream.write("hello кодировочки".toByteArray(charset("koi8-r")))
      }
      ex.close()
    }
    assertThat(HttpRequests.request(url).readString(null)).isEqualTo("hello кодировочки")
    assertThat(HttpRequests.request(url).gzip(false).readBytes(null)).startsWith(0x1f, 0x8b) // GZIP magic
  }

  @Test
  fun tuning() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(if ("HEAD" == ex.requestMethod) HttpURLConnection.HTTP_NO_CONTENT else HttpURLConnection.HTTP_NOT_IMPLEMENTED, -1)
      ex.close()
    }
    assertThat(HttpRequests.request(url).tuner { (it as HttpURLConnection).requestMethod = "HEAD" }.tryConnect())
      .isEqualTo(HttpURLConnection.HTTP_NO_CONTENT)
  }

  @Test
  fun putNotAllowed() {
    assertThatExceptionOfType(AssertionError::class.java)
      .isThrownBy { HttpRequests.request(url).tuner { (it as HttpURLConnection).requestMethod = "PUT" }.tryConnect() }
      .withMessageContaining("'PUT' not supported")
  }

  @Test
  fun post() {
    val receivedData = Ref.create<String>()
    server.createContext("/") { ex ->
      receivedData.set(StreamUtil.readText(InputStreamReader(ex.requestBody, StandardCharsets.UTF_8)))
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1)
      ex.close()
    }
    HttpRequests.post(url, null).write("hello")
    assertThat(receivedData.get()).isEqualTo("hello")
  }

  @Test
  fun postNotFound() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1)
      ex.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.post(url, null).write("hello") }
      .withMessage(IdeCoreBundle.message("error.connection.failed.status", HttpURLConnection.HTTP_NOT_FOUND))
      .`is`(statusCode(HttpURLConnection.HTTP_NOT_FOUND))
  }

  @Test
  fun postNotFoundWithResponse() {
    val serverErrorText = "use another url"
    server.createContext("/") { ex ->
      val bytes = serverErrorText.toByteArray(StandardCharsets.UTF_8)
      ex.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, bytes.size.toLong())
      ex.responseBody.write(bytes)
      ex.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.post(url, null).isReadResponseOnError(true).write("hello") }
      .withMessage(serverErrorText)
  }

  @Test
  fun notModified() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      ex.close()
    }
    assertThat(HttpRequests.request(url).readBytes(null)).isEmpty()
  }

  @Test
  fun permissionDenied() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_UNAUTHORIZED, -1)
      ex.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.request(url).productNameAsUserAgent().readString(null) }
      .`is`(statusCode(HttpURLConnection.HTTP_UNAUTHORIZED))
  }

  @Test
  fun invalidHeader() {
    assertThatExceptionOfType(AssertionError::class.java)
      .isThrownBy { HttpRequests.request(url).tuner { it.setRequestProperty("X-Custom", "c-str\u0000") }.readString(null) }
      .withMessageContaining("value contains NUL bytes")
  }

  @Test
  fun emptyResponseError() {
    server.createContext("/emptyNotFound") { exchange ->
      val bytes = "1".toByteArray(StandardCharsets.UTF_8)
      exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, bytes.size.toLong())
      exchange.responseBody.write(bytes)
      exchange.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.request(url).isReadResponseOnError(true).readString(null) }
      .`is`(statusCode(HttpURLConnection.HTTP_NOT_FOUND))
  }

  @Test
  fun customErrorMessage() {
    val message = UUID.randomUUID().toString()
    server.createContext("/") { ex ->
      ex.responseHeaders.add("error-message", message)
      ex.sendResponseHeaders(HttpRequests.CUSTOM_ERROR_CODE, 0)
      ex.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.request(url).readString(null) }
      .withMessage(message)
  }

  private fun statusCode(expected: Int) =
    Condition<HttpRequests.HttpStatusException>(Predicate { it.statusCode == expected }, "HttpStatusException(${expected})")
}
