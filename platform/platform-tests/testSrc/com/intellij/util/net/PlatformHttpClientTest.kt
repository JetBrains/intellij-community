// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.http.localhostHttpServer
import com.intellij.testFramework.junit5.http.url
import com.intellij.util.io.HttpRequests
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import kotlin.io.path.writeText

@TestFixtures
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class PlatformHttpClientTest {
  private val serverFixture: TestFixture<HttpServer> = localhostHttpServer()
  private val server: HttpServer get() = serverFixture.get()
  private val serverRequest: HttpRequest get() = PlatformHttpClient.request(URI(server.url))
  private val client = PlatformHttpClient.client()

  @Test fun fileUrl(@TempDir tempDir: Path) {
    val data = "data"
    val tempFile = Files.createTempFile(tempDir, "test.", ".txt").apply { writeText(data) }
    val request = PlatformHttpClient.request(tempFile.toUri())
    val response = PlatformHttpClient.checkResponse(client.send(request, HttpResponse.BodyHandlers.ofString()))
    assertThat(response.body()).isEqualTo(data)
  }

  @Test fun missingFileResponse(@TempDir tempDir: Path) {
    val missingFile = tempDir.resolve("no_such_file")
    assertThat(missingFile).doesNotExist()
    val request = PlatformHttpClient.request(missingFile.toUri())
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND)
    assertThat(response.body()).isEmpty()
    assertThatThrownBy { PlatformHttpClient.checkResponse(response) }.isInstanceOf(HttpRequests.HttpStatusException::class.java)
  }

  @Test fun redirectLimit() {
    server.createContext("/") { ex ->
      ex.responseHeaders.add("Location", server.url)  // infinite redirect
      ex.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1)
      ex.close()
    }
    val response = client.send(serverRequest, HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode()).isNotEqualTo(HttpURLConnection.HTTP_OK)
    assertThat(response.body()).isEmpty()
  }

  @Test fun redirectWithSimplifiedLocation() {
    val requested = AtomicInteger()
    server.createContext("/") { ex ->
      requested.incrementAndGet()
      ex.responseHeaders.add("Location", "/ok")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1)
      ex.close()
    }
    server.createContext("/ok") { ex ->
      requested.incrementAndGet()
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.close()
    }
    val response = PlatformHttpClient.checkResponse(client.send(serverRequest, HttpResponse.BodyHandlers.ofString()))
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK)
    assertThat(response.body()).isEmpty()
    assertThat(requested.get()).isEqualTo(2)
  }

  @Suppress("NonAsciiCharacters")
  @Test fun readContent() {
    val text = "hello кодировочки"
    server.createContext("/") { ex ->
      ex.responseHeaders.add("Content-Type", "text/plain; charset=koi8-r")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      ex.responseBody.write(text.toByteArray(charset("koi8-r")))
      ex.close()
    }
    val response = PlatformHttpClient.checkResponse(client.send(serverRequest, HttpResponse.BodyHandlers.ofString()))
    assertThat(response.body()).isEqualTo(text)
  }

  @Suppress("NonAsciiCharacters")
  @Test fun gzippedContent() {
    val text = "hello кодировочки"
    server.createContext("/") { ex ->
      ex.responseHeaders.add("Content-Type", "text/plain; charset=koi8-r")
      ex.responseHeaders.add("Content-Encoding", "gzip")
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
      GZIPOutputStream(ex.responseBody).use { stream ->
        stream.write(text.toByteArray(charset("koi8-r")))
      }
      ex.close()
    }
    val request = PlatformHttpClient.requestBuilder(URI(server.url))
      .header("Accept-Encoding", "gzip")
      .build()
    val rawResponse = PlatformHttpClient.checkResponse(client.send(request, HttpResponse.BodyHandlers.ofByteArray()))
    assertThat(rawResponse.body()).startsWith(0x1f, 0x8b) // GZIP magic
    val decodedResponse = PlatformHttpClient.checkResponse(client.send(request, PlatformHttpClient.gzipStringBodyHandler()))
    assertThat(decodedResponse.body()).isEqualTo(text)
  }

  @Test fun tuning() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(if ("HEAD" == ex.requestMethod) HttpURLConnection.HTTP_NO_CONTENT else HttpURLConnection.HTTP_NOT_IMPLEMENTED, -1)
      ex.close()
    }
    val request = PlatformHttpClient.requestBuilder(URI(server.url))
      .method("HEAD", HttpRequest.BodyPublishers.noBody())
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT)
  }

  @Test fun post() {
    val receivedData = Ref.create<String>()
    server.createContext("/") { ex ->
      receivedData.set(StreamUtil.readText(InputStreamReader(ex.requestBody, StandardCharsets.UTF_8)))
      ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1)
      ex.close()
    }
    val text = "hello"
    val request = PlatformHttpClient.requestBuilder(URI(server.url))
      .POST(HttpRequest.BodyPublishers.ofString(text))
      .build()
    PlatformHttpClient.checkResponse(client.send(request, HttpResponse.BodyHandlers.ofString()))
    assertThat(receivedData.get()).isEqualTo(text)
  }

  @Test fun postNotFoundWithResponse() {
    val serverErrorText = "use another url"
    server.createContext("/") { ex ->
      val bytes = serverErrorText.toByteArray(StandardCharsets.UTF_8)
      ex.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, bytes.size.toLong())
      ex.responseBody.write(bytes)
      ex.close()
    }
    val request = PlatformHttpClient.requestBuilder(URI(server.url))
      .POST(HttpRequest.BodyPublishers.ofString("hello"))
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE)
    assertThat(response.body()).isEqualTo(serverErrorText)
  }

  @Test fun notModified() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1)
      ex.close()
    }
    val response = client.send(serverRequest, HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_NOT_MODIFIED)
    assertThat(response.body()).isEmpty()
  }

  @Test fun permissionDenied() {
    server.createContext("/") { ex ->
      ex.sendResponseHeaders(HttpURLConnection.HTTP_UNAUTHORIZED, -1)
      ex.close()
    }
    val response = client.send(serverRequest, HttpResponse.BodyHandlers.ofString())
    assertThatThrownBy { PlatformHttpClient.checkResponse(response) }
      .isInstanceOf(HttpRequests.HttpStatusException::class.java)
      .extracting { (it as? HttpRequests.HttpStatusException)?.statusCode }
      .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED)
  }

  @Test
  fun invalidHeader() {
    assertThatThrownBy { PlatformHttpClient.requestBuilder(URI(server.url)).header("X-Custom", "c-str\u0000").build() }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test fun emptyResponseError() {
    server.createContext("/emptyNotFound") { exchange ->
      exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0)
      exchange.close()
    }
    val response = client.send(serverRequest, HttpResponse.BodyHandlers.ofString())
    assertThatThrownBy { PlatformHttpClient.checkResponse(response) }
      .isInstanceOf(HttpRequests.HttpStatusException::class.java)
      .extracting { (it as? HttpRequests.HttpStatusException)?.statusCode }
      .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND)
  }

  @Test fun customErrorMessage() {
    val message = UUID.randomUUID().toString()
    server.createContext("/") { ex ->
      ex.responseHeaders.add("error-message", message)
      ex.sendResponseHeaders(HttpRequests.CUSTOM_ERROR_CODE, 0)
      ex.close()
    }
    val response = client.send(serverRequest, HttpResponse.BodyHandlers.ofString())
    assertThatThrownBy { PlatformHttpClient.checkResponse(response) }
      .isInstanceOf(HttpRequests.HttpStatusException::class.java)
      .hasMessageContaining(message)
  }
}
