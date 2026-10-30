// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUiModelAdapter
import com.intellij.openapi.extensions.PluginId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class InstallAndEnableTaskTest {
  @Test
  fun `descriptor resolved to a custom repository is not replaced by the marketplace model`() {
    val customNode = PluginNode(PluginId.getId(PLUGIN_ID))
    customNode.repositoryName = "https://plugins.example.com/plugins.xml"
    customNode.description = "custom description"
    val customModel = PluginUiModelAdapter(customNode)
    val descriptors = mutableListOf<PluginUiModel>(customModel)

    var detailsRequested = false
    applyMarketplaceDetails(descriptors, listOf(marketplaceUpdate())) {
      detailsRequested = true
      marketplaceModel()
    }

    assertSame(customModel, descriptors.single())
    assertFalse(detailsRequested)
    assertNull(customModel.externalPluginId)
    assertNull(customModel.externalUpdateId)
    assertEquals("custom description", customModel.description)
  }

  @Test
  fun `marketplace descriptor is replaced by the detailed marketplace model`() {
    val descriptors = mutableListOf(marketplaceModel())

    val update = marketplaceUpdate()
    val detailedModel = marketplaceModel()
    applyMarketplaceDetails(descriptors, listOf(update)) { descriptor ->
      assertEquals(update.externalPluginId, descriptor.externalPluginId)
      assertEquals(update.externalUpdateId, descriptor.externalUpdateId)
      detailedModel
    }

    assertSame(detailedModel, descriptors.single())
  }

  private fun marketplaceUpdate(): IdeCompatibleUpdate =
    IdeCompatibleUpdate(externalUpdateId = "999", externalPluginId = "888", pluginId = PLUGIN_ID, version = "1.0")

  private fun marketplaceModel(): PluginUiModel = PluginUiModelAdapter(PluginNode(PluginId.getId(PLUGIN_ID)))
}

private const val PLUGIN_ID = "com.example.plugin"
