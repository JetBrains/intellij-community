// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.platform.pluginManager.shared.rpc.PluginManagerApi
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.InitSessionResult
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginManagerSessionService
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendPluginManagerApi : PluginManagerApi {
  override suspend fun getPlugins(): List<PluginDto> {
    return PluginManagerCore.plugins.map(PluginDescriptorConverter::toPluginDto)
  }

  override suspend fun getPluginById(pluginId: PluginId): PluginDto? {
    return PluginManagerCore.getPlugin(pluginId)?.let { PluginDescriptorConverter.toPluginDto(it) }
  }

  override suspend fun findPlugin(pluginId: PluginId): PluginDto? {
    return DefaultUiPluginManagerController.findPlugin(pluginId)?.let { PluginDescriptorConverter.toPluginDto(it.getDescriptor()) }
  }

  override suspend fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginDto> {
    return PluginManager.getVisiblePlugins(showImplementationDetails).map { PluginDescriptorConverter.toPluginDto(it) }.toList()
  }

  override suspend fun getInstalledPlugins(): List<PluginDto> {
    return InstalledPluginsState.getInstance().installedPlugins.map(PluginDescriptorConverter::toPluginDto)
  }

  override suspend fun getUpdates(): List<PluginDto> {
    return DefaultUiPluginManagerController.getUpdates().map { PluginDto.fromModel(it) }
  }

  override suspend fun setEnabledState(sessionId: String, pluginIds: List<PluginId>, enable: Boolean) {
    DefaultUiPluginManagerController.setPluginStatus(sessionId, pluginIds, enable)
  }

  override suspend fun isPluginRequiresUltimateButItIsDisabled(sessionId: String, pluginId: PluginId): Boolean {
    return DefaultUiPluginManagerController.isPluginRequiresUltimateButItIsDisabled(sessionId, pluginId)
  }

  override suspend fun isDisabledInDiff(sessionId: String, pluginId: PluginId): Boolean {
    return DefaultUiPluginManagerController.isDisabledInDiff(sessionId, pluginId)
  }

  override suspend fun filterPluginsRequiresUltimateButItsDisabled(pluginIds: List<PluginId>): List<PluginId> {
    return DefaultUiPluginManagerController.filterPluginsRequiringUltimateButItsDisabled(pluginIds)
  }

  override suspend fun findPluginNames(pluginIds: List<PluginId>): List<String> {
    return DefaultUiPluginManagerController.findPluginNames(pluginIds)
  }

  override suspend fun isPluginInstalled(pluginId: PluginId): Boolean {
    return DefaultUiPluginManagerController.isPluginInstalled(pluginId)
  }

  override suspend fun hasPluginsAvailableForEnableDisable(pluginIds: List<PluginId>): Boolean {
    return DefaultUiPluginManagerController.hasPluginsAvailableForEnableDisable(pluginIds)
  }

  override suspend fun getPluginInstallationState(pluginId: PluginId): PluginInstallationState {
    return DefaultUiPluginManagerController.getPluginInstallationState(pluginId)
  }

  override suspend fun getPluginInstallationStates(): Map<PluginId, PluginInstallationState> {
    return DefaultUiPluginManagerController.getPluginInstallationStates()
  }

  override suspend fun findInstalledPlugins(plugins: Set<PluginId>): Map<PluginId, PluginDto> {
    return DefaultUiPluginManagerController.findInstalledPlugins(plugins).mapValues { PluginDto.fromModel(it.value) }
  }

  override suspend fun getCustomRepoPlugins(): List<PluginDto> {
    return DefaultUiPluginManagerController.getCustomRepoPlugins().map { PluginDto.fromModel(it) }
  }

  override suspend fun getCustomRepositoryPluginMap(): Map<String, List<PluginDto>> {
    return DefaultUiPluginManagerController.getCustomRepositoryPluginMap().mapValues { entry ->
      entry.value.map { PluginDto.fromModel(it) }
    }
  }

  override suspend fun enableRequiredPlugins(sessionId: String, pluginId: PluginId): Set<PluginId> {
    return DefaultUiPluginManagerController.enableRequiredPlugins(sessionId, pluginId)
  }

  override suspend fun hasPluginRequiresUltimateButItsDisabled(ids: List<PluginId>): Boolean {
    return DefaultUiPluginManagerController.hasPluginRequiresUltimateButItsDisabled(ids)
  }

  override suspend fun isBundledUpdate(pluginIds: List<PluginId>): Boolean {
    return DefaultUiPluginManagerController.isBundledUpdate(pluginIds)
  }

  override suspend fun enablePlugins(sessionId: String, ids: List<PluginId>, bool: Boolean, id: ProjectId?): SetEnabledStateResult {
    return withContext(Dispatchers.EDT) {
      DefaultUiPluginManagerController.enablePlugins(sessionId, ids, bool, id?.findProjectOrNull())
    }
  }

  override suspend fun closeSession(sessionId: String) {
    DefaultUiPluginManagerController.closeSession(sessionId)
  }

  override suspend fun executeMarketplaceQuery(query: String, count: Int, includeIncompatible: Boolean): PluginSearchResult {
    return DefaultUiPluginManagerController.executePluginsSearch(query, count, includeIncompatible)
  }

  override suspend fun isPluginDisabled(pluginId: PluginId): Boolean {
    return PluginManagerCore.isDisabled(pluginId)
  }

  override suspend fun loadMetadata(model: PluginDto): PluginDto? {
    val pluginDetails = DefaultUiPluginManagerController.loadPluginDetails(model) ?: return null
    return PluginDto.fromModel(pluginDetails)
  }

  override suspend fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>? {
    return DefaultUiPluginManagerController.loadPluginReviews(pluginId, page)
  }

  override suspend fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata? {
    return DefaultUiPluginManagerController.loadPluginMetadata(externalPluginId)
  }

  override suspend fun getAllPluginsTags(): Set<String> {
    return DefaultUiPluginManagerController.getAllPluginsTags()
  }

  override suspend fun getAllVendors(): Set<String> {
    return DefaultUiPluginManagerController.getAllVendors()
  }

  override suspend fun updateDescriptorsForInstalledPlugins() {
    DefaultUiPluginManagerController.updateDescriptorsForInstalledPlugins()
  }

  override suspend fun getLastCompatiblePluginUpdateModel(pluginId: PluginId, buildNumber: String?): PluginDto? {
    val model = DefaultUiPluginManagerController.getLastCompatiblePluginUpdateModel(pluginId, buildNumber, null) ?: return null
    return PluginDto.fromModel(model)
  }

  override suspend fun getLastCompatiblePluginUpdate(allIds: Set<PluginId>, throwExceptions: Boolean, buildNumber: String?): List<IdeCompatibleUpdate> {
    return DefaultUiPluginManagerController.getLastCompatiblePluginUpdate(allIds, throwExceptions, buildNumber)
  }

  override suspend fun isNeedUpdate(pluginId: PluginId): Boolean {
    return DefaultUiPluginManagerController.isNeedUpdate(pluginId)
  }

  override suspend fun subscribeToUpdatesCount(sessionId: String): Flow<Int?> {
    return channelFlow {
      DefaultUiPluginManagerController.connectToUpdateServiceWithCounter(sessionId) {
        trySend(it)
      }
      awaitClose()
    }
  }

  override suspend fun subscribeToPluginUpdates(sessionId: String): Flow<List<PluginDto>> {
    return channelFlow {
      val session = PluginManagerSessionService.getInstance().getSession(sessionId)
      session?.updateService?.calculateUpdates { result ->
        val pluginDtos = result?.map { PluginDto.fromModel(it) } ?: emptyList()
        trySend(pluginDtos)
      }
      awaitClose()
    }
  }

  override suspend fun recalculatePluginUpdates(sessionId: String) {
    PluginManagerSessionService.getInstance().getSession(sessionId)?.updateService?.recalculateUpdates()
  }

  override suspend fun disposeUpdaterService(sessionId: String) {
    PluginManagerSessionService.getInstance().getSession(sessionId)?.updateService?.dispose()
  }


  override suspend fun notifyUpdateFinished(sessionId: String) {
    PluginManagerSessionService.getInstance().getSession(sessionId)?.updateService?.finishUpdate()
  }

  override suspend fun checkPluginCanBeDownloaded(plugin: PluginDto): Boolean {
    return DefaultUiPluginManagerController.checkPluginCanBeDownloaded(plugin, null)
  }

  override suspend fun loadErrors(sessionId: String): Map<PluginId, CheckErrorsResult> {
    return DefaultUiPluginManagerController.loadErrors(sessionId)
  }

  override suspend fun initSession(sessionId: String): InitSessionResult {
    val initSessionResult = DefaultUiPluginManagerController.initSession(sessionId)
    return InitSessionResult(initSessionResult.visiblePlugins.map { PluginDto.fromModel(it) }, initSessionResult.pluginStates)
  }

  override suspend fun isPluginEnabled(pluginId: PluginId): Boolean {
    return DefaultUiPluginManagerController.isPluginEnabled(pluginId)
  }
}