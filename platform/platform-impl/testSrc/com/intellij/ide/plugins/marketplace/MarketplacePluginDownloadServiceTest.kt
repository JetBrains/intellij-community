// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.junit5.TestApplication
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID
import kotlin.io.path.name

@TestApplication
class MarketplacePluginDownloadServiceTest {
  @Test
  fun `downloads plugin to target directory`(@TempDir tempDir: Path) {
    val pluginData = "plugin data".toByteArray()
    val targetDir = tempDir.resolve("downloads")
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val fileName = "downloaded-plugin.zip"
    server.createContext("/download") { exchange ->
      exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"$fileName\"")
      exchange.sendResponseHeaders(200, pluginData.size.toLong())
      exchange.responseBody.use { it.write(pluginData) }
    }
    server.start()

    try {
      val downloadedPlugin = MarketplacePluginDownloadService(targetDir)
        .downloadPlugin("http://127.0.0.1:${server.address.port}/download", null)

      assertThat(downloadedPlugin.parent).isEqualTo(targetDir)
      assertThat(downloadedPlugin.name).isEqualTo(fileName)
      assertThat(downloadedPlugin).exists().hasSize(pluginData.size.toLong())
      assertThat(Files.readAllBytes(downloadedPlugin)).isEqualTo(pluginData)
    }
    finally {
      server.stop(0)
    }
  }

  @Test
  fun `downloads whole plugin when previous archive is missing`(@TempDir tempDir: Path) {
    val pluginUrl = "https://example.com/plugin.zip"
    val previousPlugin = tempDir.resolve("previous-plugin-${UUID.randomUUID()}")
    val expectedPreviousArchive = PathManager.getStartupScriptDir().resolve("${previousPlugin.fileName}.zip")
    val downloadedPlugin = tempDir.resolve("downloaded-plugin.zip")
    val indicator = EmptyProgressIndicator()
    val service = RecordingDownloadService(downloadedPlugin)

    assertThat(expectedPreviousArchive).doesNotExist()

    assertThat(service.downloadPluginViaBlockMap(pluginUrl, previousPlugin, indicator)).isEqualTo(downloadedPlugin)
    assertThat(service.downloadedPluginUrl).isEqualTo(pluginUrl)
    assertThat(service.downloadIndicator).isSameAs(indicator)
  }

  @Test
  fun `download via block map looks for previous archive in target directory`(@TempDir tempDir: Path) {
    val pluginUrl = "http://127.0.0.1"
    val targetDir = tempDir.resolve("target")
    val previousPlugin = tempDir.resolve("previous-plugin-${UUID.randomUUID()}")
    val previousArchiveInTargetDir = targetDir.resolve("${previousPlugin.fileName}.zip")
    val downloadedPlugin = tempDir.resolve("downloaded-plugin.zip")
    val requestedPaths = CopyOnWriteArrayList<String>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/plugin.zip") { exchange ->
      requestedPaths.add(exchange.requestURI.path)
      exchange.sendResponseHeaders(200, 0)
      exchange.responseBody.close()
    }
    server.createContext("/plugin.zip.blockmap.zip") { exchange ->
      requestedPaths.add(exchange.requestURI.path)
      exchange.sendResponseHeaders(404, -1)
    }
    server.start()

    try {
      Files.createDirectories(targetDir)
      Files.writeString(previousArchiveInTargetDir, "archive")
      val service = RecordingDownloadService(downloadedPlugin, targetDir)

      val actualPlugin = service.downloadPluginViaBlockMap("$pluginUrl:${server.address.port}/plugin.zip", previousPlugin, null)

      assertThat(actualPlugin).isEqualTo(downloadedPlugin)
      assertThat(requestedPaths).containsExactly("/plugin.zip", "/plugin.zip.blockmap.zip")
      assertThat(service.downloadedPluginUrl).isEqualTo("$pluginUrl:${server.address.port}/plugin.zip")
    }
    finally {
      server.stop(0)
    }
  }

  private class RecordingDownloadService(
    private val downloadedPlugin: Path,
    targetDir: Path = PathManager.getStartupScriptDir(),
  ) : MarketplacePluginDownloadService(targetDir) {
    var downloadedPluginUrl: String? = null
      private set

    var downloadIndicator: ProgressIndicator? = null
      private set

    override fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator?): Path {
      downloadedPluginUrl = pluginUrl
      downloadIndicator = indicator
      return downloadedPlugin
    }
  }
}