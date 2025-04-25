// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.util.io.HttpRequests
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.net.HttpURLConnection
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class PlatformHttpClientTest {
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
}
