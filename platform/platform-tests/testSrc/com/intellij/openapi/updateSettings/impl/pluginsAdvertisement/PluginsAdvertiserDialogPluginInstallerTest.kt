// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.function.Consumer

@TestApplication
class PluginsAdvertiserDialogPluginInstallerTest {
  @Test
  fun `doInstallPlugins calls the finish function with false when there is nothing to install or enable`() {
    val results = mutableListOf<Boolean>()
    val installer = PluginsAdvertiserDialogPluginInstaller(null, emptyList(), emptyList(), Consumer { results.add(it) })

    val started = installer.doInstallPlugins({ true }, ModalityState.nonModal())

    assertTrue(started)
    assertEquals(listOf(false), results)
  }

  @Test
  fun `doInstallPlugins calls the finish function with false when downloadPlugins throws IOException`() {
    val results = mutableListOf<Boolean>()
    val downloadError = IOException("download failed")
    val installer = object : PluginsAdvertiserDialogPluginInstaller(null, listOf(testDownloader()), emptyList(), Consumer { results.add(it) }) {
      override fun downloadPlugins(
        plugins: List<PluginNode>,
        customPlugins: Collection<PluginNode>,
        onSuccess: Runnable?,
        modalityState: ModalityState,
        function: Consumer<Boolean>?,
      ) {
        throw downloadError
      }
    }

    var started = false
    val loggedError = LoggedErrorProcessor.executeAndReturnLoggedError {
      started = installer.doInstallPlugins({ true }, ModalityState.nonModal())
    }

    assertSame(downloadError, loggedError)
    assertTrue(started)
    assertEquals(listOf(false), results)
  }

  private fun testDownloader(): PluginDownloader {
    val node = PluginNode(PluginId.getId("test.advertiser.plugin"))
    node.name = "Test Advertiser Plugin"
    node.version = "1.0"
    node.vendor = "JetBrains"
    node.downloadUrl = "file:///test-advertiser-plugin.zip"
    return PluginDownloader.createDownloader(node)
  }
}
