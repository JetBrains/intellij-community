// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.MarketplaceSearchPluginData
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.platform.pluginManager.shared.rpc.PluginManagerApi
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.IntellijUpdateMetadata
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendPluginManagerApi : PluginManagerApi {
  override suspend fun getPlugins(): List<PluginDto> {
    return PluginManagerCore.plugins.map(PluginDescriptorConverter::toPluginDto)
  }

  override suspend fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginDto> {
    return PluginManager.getVisiblePlugins(showImplementationDetails).map { PluginDescriptorConverter.toPluginDto(it) }.toList()
  }

  override suspend fun getInstalledPlugins(): List<PluginDto> {
    return InstalledPluginsState.getInstance().installedPlugins.map(PluginDescriptorConverter::toPluginDto)
  }

  override suspend fun executeMarketplaceQuery(query: String, count: Int, includeIncompatible: Boolean): List<MarketplaceSearchPluginData> {
    return DefaultUiPluginManagerController.executeMarketplaceQuery(query, count, includeIncompatible)
  }

  override suspend fun isPluginDisabled(pluginId: PluginId): Boolean {
    return PluginManagerCore.isDisabled(pluginId)
  }

  override suspend fun loadMetadata(xmlId: String, ideCompatibleUpdate: IdeCompatibleUpdate): IntellijUpdateMetadata {
    return DefaultUiPluginManagerController.loadUpdateMetadata(xmlId, ideCompatibleUpdate)
  }
}