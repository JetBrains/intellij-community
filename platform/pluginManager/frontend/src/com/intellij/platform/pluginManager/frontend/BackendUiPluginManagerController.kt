// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.frontend

import com.intellij.ide.plugins.InstallPluginRequest
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.marketplace.ApplyPluginsStateResult
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.InitSessionResult
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.PrepareToUninstallResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesService
import com.intellij.ide.plugins.newui.UiPluginManagerController
import com.intellij.ide.ui.search.TraverseUIMode
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.pluginManager.shared.rpc.PluginInstallerApi
import com.intellij.platform.pluginManager.shared.rpc.PluginManagerApi
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class BackendUiPluginManagerController() : UiPluginManagerController {
  override fun isEnabled(): Boolean {
    return !TraverseUIMode.getInstance().isActive()
  }

  override fun getTarget(): PluginSource = PluginSource.REMOTE

  override suspend fun getPlugins(): List<PluginUiModel> {
    return PluginManagerApi.getInstance().getPlugins().withSource()
  }

  override fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getVisiblePlugins(showImplementationDetails).withSource() }
  }

  override suspend fun initSession(sessionId: String): InitSessionResult {
    return PluginManagerApi.getInstance().initSession(sessionId)
  }

  override fun getInstalledPlugins(): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getInstalledPlugins().withSource() }
  }

  override fun getUpdates(): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getUpdates().withSource() }
  }

  override fun getPlugin(id: PluginId): PluginUiModel? {
    return awaitForResult { PluginManagerApi.getInstance().getPluginById(id)?.withSource() }
  }

  override suspend fun findPlugin(pluginId: PluginId): PluginUiModel? {
    return PluginManagerApi.getInstance().findPlugin(pluginId)?.withSource()
  }

  override fun getLastCompatiblePluginUpdateModel(pluginId: PluginId, buildNumber: String?, indicator: ProgressIndicator?): PluginUiModel? {
    return awaitForResult { PluginManagerApi.getInstance().getLastCompatiblePluginUpdateModel(pluginId, buildNumber)?.withSource() }
  }

  override fun getLastCompatiblePluginUpdate(allIds: Set<PluginId>, throwExceptions: Boolean, buildNumber: String?): List<IdeCompatibleUpdate> {
    return awaitForResult { PluginManagerApi.getInstance().getLastCompatiblePluginUpdate(allIds, throwExceptions, buildNumber) }
  }

  override fun allowLoadUnloadSynchronously(id: PluginId): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().allowLoadUnloadSynchronously(id) }
  }

  override fun performUninstall(sessionId: String, pluginId: PluginId): Boolean {
    return runBlockingCancellable { PluginInstallerApi.getInstance().performUninstall(sessionId, pluginId) }
  }

  override fun performInstallOperation(installPluginRequest: InstallPluginRequest, parentComponent: JComponent?, modalityState: ModalityState?, progressIndicator: ProgressIndicator?, pluginEnabler: PluginEnabler, installCallback: (InstallPluginResult) -> Unit) {
    service<BackendRpcCoroutineContext>().coroutineScope.launch {
      installCallback(PluginInstallerApi.getInstance().performInstallOperation(installPluginRequest))
    }
  }

  override suspend fun isModified(sessionId: String): Boolean {
    return PluginInstallerApi.getInstance().isModified(sessionId)
  }

  override fun enablePlugins(sessionId: String, descriptorIds: List<PluginId>, enable: Boolean, project: Project?): SetEnabledStateResult {
    return awaitForResult { PluginManagerApi.getInstance().enablePlugins(sessionId, descriptorIds, enable, project?.projectId()) }
  }

  override fun isPluginRequiresUltimateButItIsDisabled(sessionId: String, pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isPluginRequiresUltimateButItIsDisabled(sessionId, pluginId) }
  }

  override suspend fun isDisabledInDiff(sessionId: String, pluginId: PluginId): Boolean {
    return PluginManagerApi.getInstance().isDisabledInDiff(sessionId, pluginId)
  }

  override suspend fun findInstalledPlugins(plugins: Set<PluginId>): Map<PluginId, PluginUiModel> {
    return PluginManagerApi.getInstance().findInstalledPlugins(plugins)
  }

  override suspend fun isPluginEnabled(pluginId: PluginId): Boolean {
    return PluginManagerApi.getInstance().isPluginEnabled(pluginId)
  }

  override fun isPluginInstalled(pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isPluginInstalled(pluginId) }
  }

  override fun getPluginInstallationState(pluginId: PluginId): PluginInstallationState {
    return awaitForResult { PluginManagerApi.getInstance().getPluginInstallationState(pluginId) }
  }

  override fun getPluginInstallationStates(): Map<PluginId, PluginInstallationState> {
    return awaitForResult { PluginManagerApi.getInstance().getPluginInstallationStates() }
  }

  override suspend fun checkPluginCanBeDownloaded(pluginUiModel: PluginUiModel, progressIndicator: ProgressIndicator?): Boolean {
    return PluginManagerApi.getInstance().checkPluginCanBeDownloaded(PluginDto.fromModel(pluginUiModel))
  }

  override suspend fun loadErrors(sessionId: String): Map<PluginId, CheckErrorsResult> {
    return PluginManagerApi.getInstance().loadErrors(sessionId)
  }

  override fun hasPluginsAvailableForEnableDisable(pluginIds: List<PluginId>): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().hasPluginsAvailableForEnableDisable(pluginIds) }
  }

  override fun connectToUpdateServiceWithCounter(sessionId: String, callback: (Int?) -> Unit): PluginUpdatesService {
    service<BackendRpcCoroutineContext>().coroutineScope.launch {
      PluginManagerApi.getInstance().subscribeToUpdatesCount(sessionId).collectLatest {
        callback(it)
      }
    }
    return RemotePluginUpdatesService(sessionId)
  }

  override fun filterPluginsRequiringUltimateButItsDisabled(pluginIds: List<PluginId>): List<PluginId> {
    return awaitForResult { PluginManagerApi.getInstance().filterPluginsRequiresUltimateButItsDisabled(pluginIds) }
  }

  override fun findPluginNames(pluginIds: List<PluginId>): List<String> {
    return awaitForResult { PluginManagerApi.getInstance().findPluginNames(pluginIds) }
  }

  override fun setEnableStateForDependencies(sessionId: String, descriptorIds: Set<PluginId>, enable: Boolean): SetEnabledStateResult {
    return awaitForResult { PluginInstallerApi.getInstance().setEnableStateForDependencies(sessionId, descriptorIds, enable) }
  }

  override suspend fun getErrors(sessionId: String, pluginId: PluginId): CheckErrorsResult {
    return PluginInstallerApi.getInstance().getErrors(sessionId, pluginId)
  }

  override fun enableRequiredPlugins(sessionId: String, pluginId: PluginId): Set<PluginId> {
    return awaitForResult { PluginManagerApi.getInstance().enableRequiredPlugins(sessionId, pluginId) }
  }

  override fun getCustomRepoPlugins(): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getCustomRepoPlugins().withSource() }
  }

  override suspend fun getCustomRepositoryPluginMap(): Map<String, List<PluginUiModel>> {
    return PluginManagerApi.getInstance().getCustomRepositoryPluginMap()
  }

  override fun hasPluginRequiresUltimateButItsDisabled(pluginIds: List<PluginId>): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().hasPluginRequiresUltimateButItsDisabled(pluginIds) }
  }

  override suspend fun isBundledUpdate(pluginIds: List<PluginId>): Boolean {
    return PluginManagerApi.getInstance().isBundledUpdate(pluginIds)
  }

  override fun prepareToUninstall(pluginsToUninstall: List<PluginId>): PrepareToUninstallResult {
    return awaitForResult { PluginInstallerApi.getInstance().prepareToUninstall(pluginsToUninstall) }
  }

  override suspend fun resetSession(sessionId: String, removeSession: Boolean, parentComponent: JComponent?): Map<PluginId, Boolean> {
    return PluginInstallerApi.getInstance().resetSession(sessionId, removeSession)
  }

  override fun setPluginStatus(sessionId: String, pluginIds: List<PluginId>, enable: Boolean) {
    awaitForResult { PluginManagerApi.getInstance().setEnabledState(sessionId, pluginIds, enable) }
  }

  override fun getApplyError(sessionId: String): String? {
    return awaitForResult { PluginInstallerApi.getInstance().getApplySessionError(sessionId) }
  }

  override fun applySession(sessionId: String, parent: JComponent?, project: Project?): ApplyPluginsStateResult {
    return awaitForResult { PluginInstallerApi.getInstance().applyPluginSession(sessionId, project?.projectId()) }
  }

  override suspend fun updatePluginDependencies(sessionId: String): Set<PluginId> {
    return PluginInstallerApi.getInstance().updatePluginDependencies(sessionId)
  }

  override fun allowLoadUnloadWithoutRestart(pluginId: PluginId): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().allowLoadUnloadWithoutRestart(pluginId.idString) }
  }

  override fun isPluginDisabled(pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isPluginDisabled(pluginId) }
  }

  override fun executePluginsSearch(query: String, count: Int, includeIncompatible: Boolean): PluginSearchResult {
    return awaitForResult { PluginManagerApi.getInstance().executeMarketplaceQuery(query, count, includeIncompatible) }
  }

  override fun loadPluginDetails(model: PluginUiModel): PluginUiModel? {
    return awaitForResult { PluginManagerApi.getInstance().loadMetadata(PluginDto.fromModel(model)) }
  }

  override fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>? {
    return awaitForResult { PluginManagerApi.getInstance().loadPluginReviews(pluginId, page) }
  }

  override fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata? {
    return awaitForResult { PluginManagerApi.getInstance().loadPluginMetadata(externalPluginId) }
  }

  override fun getAllPluginsTags(): Set<String> {
    return awaitForResult { PluginManagerApi.getInstance().getAllPluginsTags() }
  }

  override fun getAllVendors(): Set<String> {
    return awaitForResult { PluginManagerApi.getInstance().getAllVendors() }
  }

  override fun updateDescriptorsForInstalledPlugins() {
    service<BackendRpcCoroutineContext>().coroutineScope.launch {
      PluginManagerApi.getInstance().updateDescriptorsForInstalledPlugins()
    }
  }

  override fun uninstallDynamicPlugin(parentComponent: JComponent?, sessionId: String, pluginId: PluginId, isUpdate: Boolean): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().uninstallDynamicPlugin(sessionId, pluginId, isUpdate) }
  }

  override fun deletePluginFiles(pluginId: PluginId) {
    awaitForResult { PluginInstallerApi.getInstance().deletePluginFiles(pluginId) }
  }

  override fun isNeedUpdate(pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isNeedUpdate(pluginId) }
  }

  override suspend fun closeSession(sessionId: String) {
    service<BackendRpcCoroutineContext>().coroutineScope.launch {
      PluginManagerApi.getInstance().closeSession(sessionId)
    }
  }

  private fun List<PluginUiModel>.withSource(): List<PluginUiModel> {
    forEach { it.source = PluginSource.REMOTE }
    return this
  }

  private fun PluginUiModel.withSource(): PluginUiModel {
    source = PluginSource.REMOTE
    return this
  }

  @Deprecated("Test method ")
  private fun <T> awaitForResult(body: suspend () -> T): T {
    val deferred = CompletableDeferred<T>()
    service<BackendRpcCoroutineContext>().coroutineScope.launch(Dispatchers.IO) {
      deferred.complete(body())
    }
    return runBlocking { deferred.await() }
  }
}


@Service
@ApiStatus.Internal
class BackendRpcCoroutineContext(val coroutineScope: CoroutineScope)