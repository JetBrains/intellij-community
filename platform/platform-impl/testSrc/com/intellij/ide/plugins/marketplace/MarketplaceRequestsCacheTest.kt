// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.openapi.progress.ProcessCanceledException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class MarketplaceRequestsCacheTest {
  @Test
  fun `cancelled download preserves published cache`(@TempDir tempDir: Path) {
    val cacheFile = tempDir.resolve("plugins.json")
    Files.writeString(cacheFile, "old cache")

    assertThatThrownBy {
      MarketplaceRequests.updateCacheFile(cacheFile, { temporaryFile ->
        Files.writeString(temporaryFile, "partial download")
        throw ProcessCanceledException()
      }, ::readText)
    }.isInstanceOf(ProcessCanceledException::class.java)

    assertThat(Files.readString(cacheFile)).isEqualTo("old cache")
  }

  @Test
  fun `invalid download preserves published cache`(@TempDir tempDir: Path) {
    val cacheFile = tempDir.resolve("plugins.json")
    Files.writeString(cacheFile, "old cache")

    assertThatThrownBy {
      MarketplaceRequests.updateCacheFile(cacheFile, { temporaryFile ->
        Files.writeString(temporaryFile, "invalid download")
      }) {
        throw IOException("invalid cache")
      }
    }.isInstanceOf(IOException::class.java)

    assertThat(Files.readString(cacheFile)).isEqualTo("old cache")
  }

  @Test
  fun `cache is published after validation`(@TempDir tempDir: Path) {
    val cacheFile = tempDir.resolve("plugins.json")
    Files.writeString(cacheFile, "old cache")
    var cachedContentDuringValidation: String? = null

    val result = MarketplaceRequests.updateCacheFile(cacheFile, { temporaryFile ->
      Files.writeString(temporaryFile, "new cache")
    }) { input ->
      cachedContentDuringValidation = MarketplaceRequests.readCacheFile(cacheFile, ::readText)
      readText(input)
    }

    assertThat(cachedContentDuringValidation).isEqualTo("old cache")
    assertThat(result).isEqualTo("new cache")
    assertThat(Files.readString(cacheFile)).isEqualTo("new cache")
  }

  @Test
  fun `invalid cached response and ETag are removed`(@TempDir tempDir: Path) {
    val cacheFile = tempDir.resolve("plugins.json")
    val eTagFile = tempDir.resolve("plugins.json.etag")
    Files.writeString(cacheFile, "[\"truncated")
    Files.writeString(eTagFile, "current-etag")
    val objectMapper = JsonMapper.builder().build()

    val result = MarketplaceRequests.readCacheFile(cacheFile) { input ->
      objectMapper.readValue(input, object : TypeReference<Set<String>>() {})
    }

    assertThat(result).isNull()
    assertThat(cacheFile).doesNotExist()
    assertThat(eTagFile).doesNotExist()
  }

  private fun readText(input: java.io.InputStream): String = input.bufferedReader().readText()
}
