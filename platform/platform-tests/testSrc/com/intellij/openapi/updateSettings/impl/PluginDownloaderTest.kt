// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.PluginNode
import com.intellij.idea.TestFor
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.http.localhostHttpServer
import com.intellij.testFramework.junit5.http.url
import com.intellij.util.io.Compressor
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

@TestFixtures
class PluginDownloaderTest {
  private val serverFixture = localhostHttpServer()
  private val server get() = serverFixture.get()

  @Test
  @TestFor(issues = ["IJPL-148728"])
  fun testDownloadPluginWithSpaceInPath() {
    val emptyPluginData = ByteArrayOutputStream().let {
      Compressor.Zip(it).withLevel(0).use { zip ->
        zip.addFile("plugin/hello.txt", "hello".toByteArray())
      }
      it.toByteArray()
    }
    server.createContext("/empty plugin.zip") { ex ->
      ex.sendResponseHeaders(200, emptyPluginData.size.toLong())
      ex.responseBody.write(emptyPluginData)
      ex.close()
    }
    val desc = PluginNode(PluginId.getId("com.example.empty-plugin"))

    desc.downloadUrl = "empty%20plugin.zip"
    assertThat(PluginDownloader.createDownloader(desc, server.url, null).tryDownloadPlugin(null))
      .exists()
      .hasSize(emptyPluginData.size.toLong())

    desc.downloadUrl = "empty plugin.zip"
    assertThat(PluginDownloader.createDownloader(desc, server.url, null).tryDownloadPlugin(null))
      .exists()
      .hasSize(emptyPluginData.size.toLong())
  }
}
