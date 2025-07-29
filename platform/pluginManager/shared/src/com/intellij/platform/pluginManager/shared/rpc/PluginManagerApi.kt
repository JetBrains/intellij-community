// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.shared.rpc

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.InitSessionResult
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface PluginManagerApi : RemoteApi<Unit> {
  suspend fun getPlugins(): List<PluginDto>
  suspend fun getPluginById(pluginId: PluginId): PluginDto?
  suspend fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginDto>
  suspend fun getInstalledPlugins(): List<PluginDto>
  suspend fun getUpdates(): List<PluginDto>
  suspend fun findPlugin(pluginId: PluginId): PluginDto?
  suspend fun getLastCompatiblePluginUpdateModel(pluginId: PluginId, buildNumber: String?): PluginDto?
  suspend fun getLastCompatiblePluginUpdate(allIds: Set<PluginId>, throwExceptions: Boolean, buildNumber: String?): List<IdeCompatibleUpdate>
  suspend fun executeMarketplaceQuery(query: String, count: Int, includeIncompatible: Boolean): PluginSearchResult
  suspend fun isPluginDisabled(pluginId: PluginId): Boolean
  suspend fun loadMetadata(model: PluginDto): PluginDto?
  suspend fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>?
  suspend fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata?
  suspend fun getAllPluginsTags(): Set<String>
  suspend fun getAllVendors(): Set<String>
  suspend fun updateDescriptorsForInstalledPlugins()
  suspend fun closeSession(sessionId: String)
  suspend fun setEnabledState(sessionId: String, pluginIds: List<PluginId>, enable: Boolean)
  suspend fun enablePlugins(sessionId: String, ids: List<PluginId>, bool: Boolean, id: ProjectId?): SetEnabledStateResult
  suspend fun isBundledUpdate(pluginIds: List<PluginId>): Boolean
  suspend fun isPluginRequiresUltimateButItIsDisabled(sessionId: String, pluginId: PluginId): Boolean
  suspend fun hasPluginRequiresUltimateButItsDisabled(ids: List<PluginId>): Boolean
  suspend fun enableRequiredPlugins(sessionId: String, pluginId: PluginId): Set<PluginId>
  suspend fun getCustomRepoPlugins(): List<PluginDto>
  suspend fun getCustomRepositoryPluginMap(): Map<String, List<PluginDto>>
  suspend fun isDisabledInDiff(sessionId: String, pluginId: PluginId): Boolean
  suspend fun isPluginInstalled(pluginId: PluginId): Boolean
  suspend fun hasPluginsAvailableForEnableDisable(pluginIds: List<PluginId>): Boolean
  suspend fun filterPluginsRequiresUltimateButItsDisabled(pluginIds: List<PluginId>): List<PluginId>
  suspend fun findPluginNames(pluginIds: List<PluginId>): List<String>
  suspend fun isNeedUpdate(pluginId: PluginId): Boolean
  suspend fun subscribeToPluginUpdates(sessionId: String): Flow<List<PluginDto>>
  suspend fun subscribeToUpdatesCount(sessionId: String): Flow<Int?>
  suspend fun recalculatePluginUpdates(sessionId: String)
  suspend fun disposeUpdaterService(sessionId: String)
  suspend fun notifyUpdateFinished(sessionId: String)
  suspend fun getPluginInstallationState(pluginId: PluginId): PluginInstallationState
  suspend fun getPluginInstallationStates(): Map<PluginId, PluginInstallationState>
  suspend fun checkPluginCanBeDownloaded(plugin: PluginDto): Boolean
  suspend fun loadErrors(sessionId: String): Map<PluginId, CheckErrorsResult>
  suspend fun initSession(sessionId: String): InitSessionResult
  suspend fun isPluginEnabled(pluginId: PluginId): Boolean
  suspend fun findInstalledPlugins(plugins: Set<PluginId>): Map<PluginId, PluginDto>


  companion object {
    suspend fun getInstance(): PluginManagerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<PluginManagerApi>())
    }
  }
}