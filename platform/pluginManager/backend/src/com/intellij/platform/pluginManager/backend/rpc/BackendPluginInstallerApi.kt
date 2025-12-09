// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.marketplace.*
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.ide.plugins.newui.PluginManagerSessionService
import com.intellij.ide.plugins.newui.SessionStatePluginEnabler
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.pluginManager.shared.rpc.PluginInstallerApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.IOException

internal class BackendPluginInstallerApi : PluginInstallerApi {

  override suspend fun unloadDynamicPlugin(pluginId: PluginId, isUpdate: Boolean): Boolean {
    val pluginDescriptor = PluginManagerCore.findPlugin(pluginId)?.getMainDescriptor() ?: return false
    return PluginInstaller.unloadDynamicPlugin(null, pluginDescriptor, isUpdate)
  }

  override suspend fun resetSession(sessionId: String, removeSession: Boolean): Map<PluginId, Boolean> {
    return DefaultUiPluginManagerController.resetSession(sessionId, removeSession)
  }

  override suspend fun isModified(): Boolean {
    return DefaultUiPluginManagerController.isModified()
  }

  override suspend fun setEnableStateForDependencies(sessionId: String, descriptorIds: Set<PluginId>, enable: Boolean): SetEnabledStateResult {
    return DefaultUiPluginManagerController.setEnableStateForDependencies(sessionId, descriptorIds, enable)
  }

  override suspend fun installPluginFromDisk(projectId: ProjectId?): Flow<PluginInstalledFromDiskResult> {
    return channelFlow {
      withContext(Dispatchers.EDT) {
        val project = projectId?.findProjectOrNull()
        InstallFromDiskAction.installPluginFromDisk(null, project, InstalledPluginsTableModel(project), PluginEnabler.HEADLESS, null) {
          trySend(PluginInstalledFromDiskResult(PluginDescriptorConverter.toPluginDto(it.pluginDescriptor), it.restartNeeded))
        }
      }
    }
  }

  override suspend fun installOrUpdatePlugin(sessionId: String, descriptor: PluginDto, updateDescriptor: PluginDto?, installSource: FUSEventSource?, customRepoPlugins: List<PluginDto>?): InstallPluginResult {
    return installPlugin(sessionId) { enabler ->
      DefaultUiPluginManagerController.installOrUpdatePlugin(sessionId,
                                                             null,
                                                             descriptor,
                                                             updateDescriptor,
                                                             installSource,
                                                             null,
                                                             enabler,
                                                             customRepoPlugins)
    }
  }

  override suspend fun continueInstallation(sessionId: String, pluginId: PluginId, enableRequiredPlugins: Boolean, allowInstallWithoutRestart: Boolean, customRepoPlugins: List<PluginDto>?): InstallPluginResult {
    return installPlugin(sessionId) { enabler ->
      DefaultUiPluginManagerController.continueInstallation(sessionId,
                                                            pluginId,
                                                            enableRequiredPlugins,
                                                            allowInstallWithoutRestart,
                                                            enabler,
                                                            null,
                                                            null,
                                                            customRepoPlugins)
    }
  }

  override suspend fun isRestartRequired(sessionId: String): Boolean {
    return DefaultUiPluginManagerController.isRestartRequired(sessionId)
  }

  private suspend fun installPlugin(sessionId: String, installOperation: suspend (PluginEnabler) -> InstallPluginResult): InstallPluginResult {
    val session = PluginManagerSessionService.getInstance().getSession(sessionId) ?: return InstallPluginResult.FAILED
    val enabler = SessionStatePluginEnabler(session)
    val result = installOperation(enabler)
    return result.apply { pluginsToDisable = enabler.pluginsToDisable }
  }

  override suspend fun prepareToUninstall(pluginsToUninstall: List<PluginId>): PrepareToUninstallResult {
    return DefaultUiPluginManagerController.prepareToUninstall(pluginsToUninstall)
  }

  override suspend fun getErrors(sessionId: String, pluginId: PluginId): CheckErrorsResult {
    return DefaultUiPluginManagerController.getErrors(sessionId, pluginId)
  }

  override suspend fun performUninstall(sessionId: String, id: PluginId): Boolean {
    return DefaultUiPluginManagerController.performUninstall(sessionId, id)
  }

  override suspend fun updatePluginDependencies(sessionId: String): Set<PluginId> {
    return DefaultUiPluginManagerController.updatePluginDependencies(sessionId)
  }

  override suspend fun apply(projectId: ProjectId?): ApplyPluginsStateResult {
    return withContext(Dispatchers.EDT) {
      DefaultUiPluginManagerController.apply(project = projectId?.findProjectOrNull())
    }
  }

  override suspend fun deletePluginFiles(pluginId: PluginId) {
    val pluginDescriptor = PluginManagerCore.findPlugin(pluginId) ?: return
    try {
      FileUtil.delete(pluginDescriptor.getPluginPath())
    }
    catch (e: IOException) {
      LOG.warn(e);
    }
  }

  override suspend fun allowLoadUnloadSynchronously(pluginId: PluginId): Boolean {
    val pluginDescriptor = PluginManagerCore.findPlugin(pluginId) ?: return false
    return DynamicPlugins.allowLoadUnloadSynchronously(pluginDescriptor)
  }


  override suspend fun allowLoadUnloadWithoutRestart(pluginId: String): Boolean {
    val pluginDescriptor = PluginManagerCore.findPlugin(PluginId.getId(pluginId)) ?: return false
    return DynamicPlugins.allowLoadUnloadWithoutRestart(pluginDescriptor)
  }
}

private val LOG = Logger.getInstance(BackendPluginInstallerApi::class.java)