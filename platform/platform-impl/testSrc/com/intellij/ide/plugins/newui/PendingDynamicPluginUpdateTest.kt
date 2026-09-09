// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PendingDynamicPluginInstall
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class PendingDynamicPluginUpdateTest {
  @Test
  fun `a prepared dynamic update stages the old and new descriptors together`() {
    val pluginId = PluginId.getId("dynamic.update.plugin")
    val installedDescriptor = descriptor(pluginId, "Installed Plugin")
    val updateDescriptor = descriptor(pluginId, "Plugin Update")
    val pendingInstall = PendingDynamicPluginInstall(Path.of("update.zip"), updateDescriptor)
    val session = PluginManagerSession("test-session")
    val result = successfulDynamicResult()

    stagePreparedDynamicUpdate(session, installedDescriptor, listOf(pendingInstall), result)

    assertThat(session.dynamicPluginsToUninstall).containsExactly(installedDescriptor)
    assertThat(session.dynamicPluginsToInstall).containsEntry(pluginId, pendingInstall)
    assertThat(session.uninstalledPlugins).isEmpty()
    assertThat(installedDescriptor.isDeleted).isFalse()
  }

  @Test
  fun `a restart update does not stage a dynamic replacement`() {
    val pluginId = PluginId.getId("restart.update.plugin")
    val installedDescriptor = descriptor(pluginId, "Installed Plugin")
    val updateDescriptor = descriptor(pluginId, "Plugin Update")
    val pendingInstall = PendingDynamicPluginInstall(Path.of("update.zip"), updateDescriptor)
    val session = PluginManagerSession("test-session")
    val result = successfulDynamicResult().apply { restartRequired = true }

    stagePreparedDynamicUpdate(session, installedDescriptor, listOf(pendingInstall), result)

    assertThat(session.dynamicPluginsToUninstall).isEmpty()
    assertThat(session.dynamicPluginsToInstall).isEmpty()
  }

  private fun descriptor(pluginId: PluginId, name: String): PluginMainDescriptor {
    val builder = PluginDescriptorBuilder.builder()
    builder.id = pluginId.idString
    builder.name = name
    return PluginMainDescriptor(builder.build(), Path.of(pluginId.idString), false)
  }

  private fun successfulDynamicResult(): InstallPluginResult {
    return InstallPluginResult().apply {
      success = true
      cancel = false
      restartRequired = false
    }
  }
}
