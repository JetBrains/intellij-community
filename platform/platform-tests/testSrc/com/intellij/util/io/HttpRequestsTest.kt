// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.http.localhostHttpServer
import com.intellij.testFramework.junit5.http.url
import com.intellij.testFramework.rethrowLoggedErrorsIn
import com.intellij.util.TimeoutUtil
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.zip.GZIPOutputStream

@TestFixtures
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class HttpRequestsTest {
  private val serverFixture: TestFixture<HttpServer> = localhostHttpServer()
  private val server: HttpServer get() = serverFixture.get()

  @Test fun redirectLimit() {
    val requested = AtomicInteger()
    // infinite redirect
    server.createContext("/") { ex ->
      requested.incrementAndGet()
      ex.responseHeaders.add("Location", server.url)
      ex.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1)
      ex.close()
    }
    assertThatExceptionOfType(IOException::class.java)
      .isThrownBy { HttpRequests.request(server.url).redirectLimit(2).readString(null) }
      .withMessage(IdeCoreBundle.message("error.connection.failed.redirects"))
    assertThat(requested.get()).isEqualTo(2)
  }

  @Test fun redirectWithSimplifiedLocation() {
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
    assertThat(HttpRequests.request(server.url).redirectLimit(2).readString(null)).isEqualTo("")
    assertThat(requested1.get()).isEqualTo(1)
    assertThat(requested2.get()).isEqualTo(1)
  }

  @Test fun redirectLimitPositive() {
    assertThatExceptionOfType(IllegalArgumentException::class.java)
      .isThrownBy { HttpRequests.request("").redirectLimit(0).readString(null) }
      .withMessage("Redirect limit should be positive")
  }

  @Test fun readTimeout() {
    server.createContext("/") { ex ->
      TimeoutUtil.sleep(1000)
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.close()
    }
    assertThatExceptionOfType(SocketTimeoutException::class.java)
      .isThrownBy { HttpRequests.request(server.url).readTimeout(50).readString(null) }
  }

  @Suppress("NonAsciiCharacters")
  @Test fun readContent() {
    server.createContext("/") { ex ->
      ex.responseHeaders.add("Content-Type", "text/plain; charset=koi8-r; boundary=something")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.responseBody.write("hello кодировочки".toByteArray(charset("koi8-r")))
      ex.close()
    }
    assertThat(HttpRequests.request(server.url).readString(null)).isEqualTo("hello кодировочки")
  }

  @Suppress("NonAsciiCharacters")
  @Test fun gzippedContent() {
    server.createContext("/") { ex ->
      ex.responseHeaders.add("Content-Type", "text/plain; charset=koi8-r")
      ex.responseHeaders.add("Content-Encoding", "gzip")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      GZIPOutputStream(ex.responseBody).use { gzipOutputStream ->
        gzipOutputStream.write("hello кодировочки".toByteArray(charset("koi8-r")))
      }
      ex.close()
    }
    assertThat(HttpRequests.request(server.url).readString(null)).isEqualTo("hello кодировочки")
    assertThat(HttpRequests.request(server.url).gzip(false).readBytes(null)).startsWith(0x1f, 0x8b) // GZIP magic
  }

  @Test fun tuning() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(if ("HEAD" == ex.requestMethod) HttpURLConnection.HTTP_NO_CONTENT else HttpURLConnection.HTTP_NOT_IMPLEMENTED, -1)
      ex.close()
    }
    assertThat(HttpRequests.request(server.url).tuner { (it as HttpURLConnection).requestMethod = "HEAD" }.tryConnect())
      .isEqualTo(HttpURLConnection.HTTP_NO_CONTENT)
  }

  @Test
  @Suppress("DEPRECATION")
  fun putNotAllowed(): Unit = rethrowLoggedErrorsIn {
    assertThatExceptionOfType(AssertionError::class.java)
      .isThrownBy { HttpRequests.request(server.url).tuner { (it as HttpURLConnection).requestMethod = "PUT" }.tryConnect() }
      .withMessageContaining("'PUT' not supported")
  }

  @Test fun post() {
    val receivedData = Ref.create<String>()
    server.createContext("/") { ex ->
      receivedData.set(StreamUtil.readText(InputStreamReader(ex.requestBody, StandardCharsets.UTF_8)))
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1)
      ex.close()
    }
    HttpRequests.post(server.url, null).write("hello")
    assertThat(receivedData.get()).isEqualTo("hello")
  }

  @Test fun postNotFound() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1)
      ex.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.post(server.url, null).write("hello") }
      .withMessage(IdeCoreBundle.message("error.connection.failed.status", HttpURLConnection.HTTP_NOT_FOUND))
      .`is`(statusCode(HttpURLConnection.HTTP_NOT_FOUND))
  }

  @Test fun postNotFoundWithResponse() {
    val serverErrorText = "use another url"
    server.createContext("/") { ex ->
      val bytes = serverErrorText.toByteArray(StandardCharsets.UTF_8)
      ex.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, bytes.size.toLong())
      ex.responseBody.write(bytes)
      ex.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.post(server.url, null).isReadResponseOnError(true).write("hello") }
      .withMessage(serverErrorText)
  }

  @Test fun notModified() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      ex.close()
    }
    assertThat(HttpRequests.request(server.url).readBytes(null)).isEmpty()
  }

  @Test fun permissionDenied() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_UNAUTHORIZED, -1)
      ex.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.request(server.url).productNameAsUserAgent().readString(null) }
      .`is`(statusCode(HttpURLConnection.HTTP_UNAUTHORIZED))
  }

  @Test
  @Suppress("DEPRECATION")
  fun invalidHeader(): Unit = rethrowLoggedErrorsIn {
    assertThatExceptionOfType(AssertionError::class.java)
      .isThrownBy { HttpRequests.request(server.url).tuner { it.setRequestProperty("X-Custom", "c-str\u0000") }.readString(null) }
      .withMessageContaining("value contains NUL bytes")
  }

  @Test fun emptyResponseError() {
    server.createContext("/emptyNotFound") { exchange ->
      val bytes = "1".toByteArray(StandardCharsets.UTF_8)
      exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, bytes.size.toLong())
      exchange.responseBody.write(bytes)
      exchange.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.request(server.url).isReadResponseOnError(true).readString(null) }
      .`is`(statusCode(HttpURLConnection.HTTP_NOT_FOUND))
  }

  @Test fun customErrorMessage() {
    val message = UUID.randomUUID().toString()
    server.createContext("/") { ex ->
      ex.responseHeaders.add("error-message", message)
      ex.sendResponseHeaders(HttpRequests.CUSTOM_ERROR_CODE, 0)
      ex.close()
    }
    assertThatExceptionOfType(HttpRequests.HttpStatusException::class.java)
      .isThrownBy { HttpRequests.request(server.url).readString(null) }
      .withMessage(message)
  }

  private fun statusCode(expected: Int) =
    Condition<HttpRequests.HttpStatusException>(Predicate { it.statusCode == expected }, "HttpStatusException(${expected})")
}
