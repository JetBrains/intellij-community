// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.util.TimeoutUtil
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InputStreamReader
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream
import kotlin.properties.Delegates

private const val LOCALHOST = "127.0.0.1"

class HttpRequestsTest {
  private var server: HttpServer by Delegates.notNull()
  private var url: String by Delegates.notNull()

  @Before
  fun setUp() {
    server = HttpServer.create()
    server.bind(InetSocketAddress(LOCALHOST, 0), 1)
    server.start()
    url = "http://$LOCALHOST:${server.address.port}"
  }

  @After
  fun tearDown() {
    server.stop(0)
  }

  @Test(timeout = 5000)
  fun redirectLimit() {
    try {
      HttpRequests.request("").redirectLimit(0).readString(null)
      Assert.fail()
    }
    catch (e: IOException) {
      assertThat(e.message).isEqualTo(IdeBundle.message("error.connection.failed.redirects"))
    }
  }

  @Test(timeout = 5000, expected = SocketTimeoutException::class)
  @Throws(IOException::class)
  fun readTimeout() {
    server.createContext("/") { ex: HttpExchange ->
      TimeoutUtil.sleep(1000)
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.close()
    }
    HttpRequests.request(url).readTimeout(50).readString(null)
    Assert.fail()
  }

  @Test(timeout = 5000)
  @Throws(IOException::class)
  fun readContent() {
    server.createContext("/") { ex: HttpExchange ->
      ex.responseHeaders.add("Content-Type", "text/plain; charset=koi8-r")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.responseBody.write("hello кодировочки".toByteArray(charset("koi8-r")))
      ex.close()
    }
    assertThat(HttpRequests.request(url).readString(null)).isEqualTo("hello кодировочки")
  }

  @Test(timeout = 5000)
  @Throws(IOException::class)
  fun gzippedContent() {
    server.createContext("/") { ex: HttpExchange ->
      ex.responseHeaders.add("Content-Type", "text/plain; charset=koi8-r")
      ex.responseHeaders.add("Content-Encoding", "gzip")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      GZIPOutputStream(ex.responseBody).use { gzipOutputStream ->
        gzipOutputStream.write("hello кодировочки".toByteArray(charset("koi8-r")))
      }
      ex.close()
    }
    assertThat(HttpRequests.request(url).readString(null)).isEqualTo("hello кодировочки")
    val bytes = HttpRequests.request(url).gzip(false).readBytes(null)
    assertThat(bytes).startsWith(0x1f, 0x8b) // GZIP magic
  }

  @Test(timeout = 5000)
  @Throws(IOException::class)
  fun tuning() {
    server.createContext("/") { ex: HttpExchange ->
      ex.sendResponseHeaders(if ("HEAD" == ex.requestMethod) HttpURLConnection.HTTP_NO_CONTENT else HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                             -1)
      ex.close()
    }
    Assert.assertEquals(HttpURLConnection.HTTP_NO_CONTENT.toLong(), HttpRequests.request(url)
      .tuner { c: URLConnection -> (c as HttpURLConnection).requestMethod = "HEAD" }
      .tryConnect().toLong())
  }

  @Test(timeout = 5000, expected = AssertionError::class)
  @Throws(IOException::class)
  fun putNotAllowed() {
    HttpRequests.request(url)
      .tuner { c: URLConnection -> (c as HttpURLConnection).requestMethod = "PUT" }
      .tryConnect()
    Assert.fail()
  }

  @Test(timeout = 5000)
  fun post() {
    val receivedData = Ref.create<String>()
    server.createContext("/") { ex: HttpExchange ->
      receivedData.set(StreamUtil.readText(InputStreamReader(ex.requestBody, StandardCharsets.UTF_8)))
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1)
      ex.close()
    }
    HttpRequests.post(url, null).write("hello")
    assertThat(receivedData.get()).isEqualTo("hello")
  }

  @Test(timeout = 5000)
  fun postNotFound() {
    server.createContext("/") { ex: HttpExchange ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1)
      ex.close()
    }
    try {
      HttpRequests
        .post(url, null)
        .write("hello")
      Assert.fail()
    }
    catch (e: SocketException) {
      // java.net.SocketException: Software caused connection abort: recv failed
      assertThat(e.message).contains("recv failed")
    }
    catch (e: HttpRequests.HttpStatusException) {
      assertThat(e.message).isEqualTo("Request failed with status code 404")
      assertThat(e.statusCode).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND)
    }
  }

  @Test(timeout = 5000)
  fun postNotFoundWithResponse() {
    val serverErrorText = "use another url"
    server.createContext("/") { ex: HttpExchange ->
      val bytes = serverErrorText.toByteArray(StandardCharsets.UTF_8)
      ex.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, bytes.size.toLong())
      ex.responseBody.write(bytes)
      ex.close()
    }
    try {
      HttpRequests
        .post(url, null)
        .isReadResponseOnError(true)
        .write("hello")
      Assert.fail()
    }
    catch (e: HttpRequests.HttpStatusException) {
      assertThat(e.message).isEqualTo(serverErrorText)
    }
  }

  @Test(timeout = 5000)
  fun notModified() {
    server.createContext("/") { ex: HttpExchange ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      ex.close()
    }
    val bytes = HttpRequests.request(url).readBytes(null)
    assertThat(bytes).isEmpty()
  }

  @Test(timeout = 5000)
  fun permissionDenied() {
    try {
      server.createContext("/") { ex: HttpExchange ->
        ex.sendResponseHeaders(HttpURLConnection.HTTP_UNAUTHORIZED, -1)
        ex.close()
      }
      HttpRequests.request(url).productNameAsUserAgent().readString(null)
      Assert.fail()
    }
    catch (e: HttpRequests.HttpStatusException) {
      assertThat(e.statusCode).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED)
    }
  }

  @Test(timeout = 5000)
  fun invalidHeader() {
    try {
      HttpRequests.request(url).tuner { connection: URLConnection ->
        connection.setRequestProperty("X-Custom", "c-str\u0000")
      }.readString(null)
      Assert.fail()
    }
    catch (e: AssertionError) {
      assertThat(e.message).contains("value contains NUL bytes")
    }
  }

  @Test(timeout = 5000)
  fun `empty response and error`() {
    try {
      server.createContext("/emptyNotFound") { exchange ->
        val bytes = "1".toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        exchange.close()
      }

      HttpRequests.request(url)
        .isReadResponseOnError(true)
        .readString(null)
      Assert.fail()
    }
    catch (e: HttpRequests.HttpStatusException) {
      assertThat(e.statusCode).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND)
    }
  }
}