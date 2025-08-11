// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.DynamicPlugins
import com.intellij.ide.plugins.InstallFromDiskAction
import com.intellij.ide.plugins.InstallPluginRequest
import com.intellij.ide.plugins.InstalledPluginsTableModel
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.*
import com.intellij.ide.plugins.newui.BgProgressIndicator
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.ide.plugins.newui.PluginManagerSessionService
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.SessionStatePluginEnabler
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.pluginManager.shared.rpc.PluginInstallerApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

internal class BackendPluginInstallerApi : PluginInstallerApi {

  override suspend fun unloadDynamicPlugin(pluginId: PluginId, isUpdate: Boolean): Boolean {
    val pluginDescriptor = PluginManagerCore.findPlugin(pluginId) ?: return false
    return PluginInstaller.unloadDynamicPlugin(null, pluginDescriptor, isUpdate)
  }

  override suspend fun resetSession(sessionId: String, removeSession: Boolean): Map<PluginId, Boolean> {
    return DefaultUiPluginManagerController.resetSession(sessionId, removeSession)
  }

  override suspend fun isModified(sessionId: String): Boolean {
    return DefaultUiPluginManagerController.isModified(sessionId)
  }

  override suspend fun setEnableStateForDependencies(sessionId: String, descriptorIds: Set<PluginId>, enable: Boolean): SetEnabledStateResult {
    return DefaultUiPluginManagerController.setEnableStateForDependencies(sessionId, descriptorIds, enable)
  }

  override suspend fun installPluginFromDisk(projectId: ProjectId?): PluginInstalledFromDiskResult {
    return withContext(Dispatchers.EDT) {
      val project = projectId?.findProjectOrNull()
      val deferred = CompletableDeferred<PluginInstalledFromDiskResult>()

      InstallFromDiskAction.installPluginFromDisk(null, project, InstalledPluginsTableModel(project), PluginEnabler.HEADLESS, null) {
        deferred.complete(PluginInstalledFromDiskResult(PluginDescriptorConverter.toPluginDto(it.pluginDescriptor), it.restartNeeded))
      }
      deferred.await()
    }
  }

  override suspend fun installOrUpdatePlugin(sessionId: String, projectId: ProjectId, descriptor: PluginDto, updateDescriptor: PluginDto?, installSource: FUSEventSource?): InstallPluginResult {
    return installPlugin(sessionId, projectId) { enabler, project ->
      DefaultUiPluginManagerController.installOrUpdatePlugin(sessionId,
                                                             project,
                                                             null,
                                                             descriptor,
                                                             updateDescriptor,
                                                             installSource,
                                                             null,
                                                             enabler)
    }
  }

  override suspend fun continueInstallation(sessionId: String, pluginId: PluginId, projectId: ProjectId, enableRequiredPlugins: Boolean, allowInstallWithoutRestart: Boolean): InstallPluginResult {
    return installPlugin(sessionId, projectId) { enabler, project ->
      DefaultUiPluginManagerController.continueInstallation(sessionId,
                                                            pluginId,
                                                            project,
                                                            enableRequiredPlugins,
                                                            allowInstallWithoutRestart,
                                                            enabler,
                                                            null,
                                                            null)
    }
  }

  private suspend fun installPlugin(sessionId: String, projectId: ProjectId, installOperation: suspend (PluginEnabler, Project) -> InstallPluginResult): InstallPluginResult {
    val session = PluginManagerSessionService.getInstance().getSession(sessionId) ?: return InstallPluginResult.FAILED
    val project = projectId.findProjectOrNull() ?: return InstallPluginResult.FAILED
    val enabler = SessionStatePluginEnabler(session)
    val result = installOperation(enabler, project)
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

  override suspend fun applyPluginSession(sessionId: String, projectId: ProjectId?): ApplyPluginsStateResult {
    return withContext(Dispatchers.EDT) {
      DefaultUiPluginManagerController.applySession(sessionId, null, projectId?.findProjectOrNull())
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