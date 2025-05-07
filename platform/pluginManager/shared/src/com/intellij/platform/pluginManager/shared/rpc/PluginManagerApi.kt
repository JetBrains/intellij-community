// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.shared.rpc

import com.intellij.ide.plugins.marketplace.MarketplaceSearchPluginData
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.IntellijUpdateMetadata
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface PluginManagerApi: RemoteApi<Unit> {
  suspend fun getPlugins(): List<PluginDto>
  suspend fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginDto>
  suspend fun getInstalledPlugins(): List<PluginDto>
  suspend fun executeMarketplaceQuery(query: String, count: Int, includeIncompatible: Boolean): List<MarketplaceSearchPluginData>
  suspend fun isPluginDisabled(pluginId: PluginId): Boolean
  suspend fun loadMetadata(xmlId: String, ideCompatibleUpdate: IdeCompatibleUpdate): IntellijUpdateMetadata
  suspend fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>?

  companion object {
    suspend fun getInstance(): PluginManagerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<PluginManagerApi>())
    }
  }
}