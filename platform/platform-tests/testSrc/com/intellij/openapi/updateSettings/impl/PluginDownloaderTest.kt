// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.http.localhostHttpServer
import com.intellij.testFramework.junit5.http.url
import com.intellij.util.io.Compressor
import com.intellij.util.io.HttpRequests
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

@TestFixtures
class PluginDownloaderTest {
  val server: TestFixture<HttpServer> = localhostHttpServer()

  // IJPL-148728
  @Test
  fun testDownloadPluginWithSpaceInPath() {
    val emptyPluginData = ByteArrayOutputStream().let {
      Compressor.Zip(it).withLevel(0)
        .apply { addFile("plugin/hello.txt", "hello".toByteArray()) }
        .close()
      it.toByteArray()
    }
    server.get().createContext("/empty plugin.zip") { ex ->
      ex.sendResponseHeaders(200, emptyPluginData.size.toLong())
      ex.responseBody.write(emptyPluginData)
      ex.close()
    }
    val desc = PluginNode(PluginId.getId("com.example.empty-plugin"))

    desc.downloadUrl = "empty%20plugin.zip"
    val path = PluginDownloader.createDownloader(desc, server.get().url, null).tryDownloadPlugin(null)
    assertThat(path)
      .isNotNull
      .exists()
      .hasSize(emptyPluginData.size.toLong())

    desc.downloadUrl = "empty plugin.zip"
    Assertions.assertThatThrownBy {
      PluginDownloader.createDownloader(desc, server.get().url, null).tryDownloadPlugin(null)
    }.isInstanceOf(HttpRequests.HttpStatusException::class.java)
      .matches { (it as HttpRequests.HttpStatusException).statusCode == 404 }
  }
}