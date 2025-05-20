// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.frontend

import com.intellij.ide.plugins.InstallPluginRequest
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.marketplace.ApplyPluginsStateResult
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.IntellijUpdateMetadata
import com.intellij.ide.plugins.marketplace.MarketplaceSearchPluginData
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.PrepareToUninstallResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManagerController
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.pluginManager.shared.rpc.PluginInstallerApi
import com.intellij.platform.pluginManager.shared.rpc.PluginManagerApi
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class BackendUiPluginManagerController() : UiPluginManagerController {
  override fun getPlugins(): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getPlugins() }
  }

  override fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getVisiblePlugins(showImplementationDetails) }
  }

  override fun getInstalledPlugins(): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getInstalledPlugins() }
  }

  override fun getPlugin(id: PluginId): PluginUiModel? {
    return awaitForResult { PluginManagerApi.getInstance().getPluginById(id) }
  }
  
  override fun findPlugin(pluginId: PluginId): PluginUiModel? {
    return awaitForResult { PluginManagerApi.getInstance().findPlugin(pluginId) }
  }
  
  override fun getLastCompatiblePluginUpdateModel(pluginId: PluginId, buildNumber: String?, indicator: ProgressIndicator?): PluginUiModel? {
    return awaitForResult { PluginManagerApi.getInstance().getLastCompatiblePluginUpdateModel(pluginId, buildNumber) }
  }
  
  override fun getLastCompatiblePluginUpdate(allIds: Set<PluginId>, throwExceptions: Boolean, buildNumber: String?): List<IdeCompatibleUpdate> {
    return awaitForResult { PluginManagerApi.getInstance().getLastCompatiblePluginUpdate(allIds, throwExceptions, buildNumber) }
  }

  override fun allowLoadUnloadSynchronously(id: PluginId): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().allowLoadUnloadSynchronously(id) }
  }

  override fun performUninstall(sessionId: String, pluginId: PluginId): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().performUninstall(sessionId, pluginId) }
  }

  override fun performInstallOperation(installPluginRequest: InstallPluginRequest, parentComponent: JComponent?, modalityState: ModalityState?, progressIndicator: ProgressIndicator?, pluginEnabler: PluginEnabler, installCallback: (InstallPluginResult) -> Unit) {
    service<BackendRpcCoroutineContext>().coroutineScope.launch {
      installCallback(PluginInstallerApi.getInstance().performInstallOperation(installPluginRequest))
    }
  }

  override fun isModified(sessionId: String): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().isModified(sessionId) }
  }

  override fun enablePlugins(sessionId: String, descriptorIds: List<PluginId>, enable: Boolean, project: Project?): SetEnabledStateResult {
    return awaitForResult { PluginManagerApi.getInstance().enablePlugins(sessionId, descriptorIds, enable, project?.projectId()) }
  }

  override fun isPluginRequiresUltimateButItIsDisabled(pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isPluginRequiresUltimateButItIsDisabled(pluginId) }
  }

  override fun isDisabledInDiff(sessionId: String, pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isDisabledInDiff(sessionId, pluginId) }
  }

  override fun isPluginInstalled(pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isPluginInstalled(pluginId) }
  }
  
  override fun hasPluginsAvailableForEnableDisable(pluginIds: List<PluginId>): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().hasPluginsAvailableForEnableDisable(pluginIds) }
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

  override fun getErrors(sessionId: String, pluginId: PluginId): CheckErrorsResult {
    return awaitForResult { PluginInstallerApi.getInstance().getErrors(sessionId, pluginId) }
  }

  override fun enableRequiredPlugins(sessionId: String, pluginId: PluginId): Set<PluginId> {
    return awaitForResult { PluginManagerApi.getInstance().enableRequiredPlugins(sessionId, pluginId) }
  }

  override fun getCustomRepoPlugins(): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getCustomRepoPlugins() }
  }

  override fun hasPluginRequiresUltimateButItsDisabled(pluginIds: List<PluginId>): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().hasPluginRequiresUltimateButItsDisabled(pluginIds) }
  }

  override fun isBundledUpdate(pluginIds: List<PluginId>): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isBundledUpdate(pluginIds) }
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

  override fun applySession(sessionId: String, parent: JComponent?, project: Project?): ApplyPluginsStateResult {
    return awaitForResult { PluginInstallerApi.getInstance().applyPluginSession(sessionId, project?.projectId()) }
  }

  override fun updatePluginDependencies(sessionId: String): Set<PluginId> {
    return awaitForResult { PluginInstallerApi.getInstance().updatePluginDependencies(sessionId) }
  }

  override fun allowLoadUnloadWithoutRestart(pluginId: PluginId): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().allowLoadUnloadWithoutRestart(pluginId.idString) }
  }

  override fun isPluginDisabled(pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isPluginDisabled(pluginId) }
  }

  override fun executeMarketplaceQuery(query: String, count: Int, includeIncompatible: Boolean): List<MarketplaceSearchPluginData> {
    return awaitForResult { PluginManagerApi.getInstance().executeMarketplaceQuery(query, count, includeIncompatible) }
  }

  override fun loadUpdateMetadata(xmlId: String, ideCompatibleUpdate: IdeCompatibleUpdate, indicator: ProgressIndicator?): IntellijUpdateMetadata {
    return awaitForResult { PluginManagerApi.getInstance().loadMetadata(xmlId, ideCompatibleUpdate) }
  }

  override fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>? {
    return awaitForResult { PluginManagerApi.getInstance().loadPluginReviews(pluginId, page) }
  }
  
  override fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata? {
    return awaitForResult { PluginManagerApi.getInstance().loadPluginMetadata(externalPluginId) }
  }
  
  override fun getPluginManagerUrl(): String {
    return awaitForResult { PluginManagerApi.getInstance().getPluginManagerUrl() }
  }
  
  override fun updateDescriptorsForInstalledPlugins() {
    service<BackendRpcCoroutineContext>().coroutineScope.launch {
      PluginManagerApi.getInstance().updateDescriptorsForInstalledPlugins()
    }
  }

  override fun unloadDynamicPlugin(parentComponent: JComponent?, pluginId: PluginId, isUpdate: Boolean): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().unloadDynamicPlugin(pluginId, isUpdate) }
  }

  override fun uninstallDynamicPlugin(parentComponent: JComponent?, pluginId: PluginId, isUpdate: Boolean): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().uninstallDynamicPlugin(pluginId, isUpdate) }
  }

  override fun deletePluginFiles(pluginId: PluginId) {
    awaitForResult { PluginInstallerApi.getInstance().deletePluginFiles(pluginId) }
  }

  override fun tryUnloadPluginIfAllowed(
    parentComponent: JComponent?, pluginId: PluginId, isUpdate: Boolean,
  ): Boolean {
    return awaitForResult { PluginInstallerApi.getInstance().allowLoadUnloadWithoutRestart(pluginId.idString) }
  }
  
  override fun isNeedUpdate(pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isNeedUpdate(pluginId) }
  }

  override fun createSession(sessionId: String) {
    awaitForResult {
      PluginManagerApi.getInstance().createSession(sessionId)
    }
  }

  override fun closeSession(sessionId: String) {
    service<BackendRpcCoroutineContext>().coroutineScope.launch {
      PluginManagerApi.getInstance().closeSession(sessionId)
    }
  }

  @Deprecated("Test method ")
  fun <T> awaitForResult(body: suspend () -> T): T {
    val deferred = CompletableDeferred<T>()
    service<BackendRpcCoroutineContext>().coroutineScope.launch {
      deferred.complete(body())
    }
    return runBlocking { deferred.await() }
  }
}


@Service
@ApiStatus.Internal
class BackendRpcCoroutineContext(val coroutineScope: CoroutineScope)