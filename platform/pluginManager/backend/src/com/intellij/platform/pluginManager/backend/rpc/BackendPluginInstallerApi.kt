// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.*
import com.intellij.ide.plugins.newui.BgProgressIndicator
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.ide.plugins.newui.PluginManagerSessionService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
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

  override suspend fun getApplySessionError(sessionId: String): String? {
    return DefaultUiPluginManagerController.getApplyError(sessionId)
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

  override suspend fun performInstallOperation(installPluginRequest: InstallPluginRequest): InstallPluginResult {
    val session = PluginManagerSessionService.getInstance().getSession(installPluginRequest.sessionId) ?: return InstallPluginResult()

    val enabler = SessionStatePluginEnabler(session)
    val result: CompletableDeferred<InstallPluginResult> = CompletableDeferred()
    DefaultUiPluginManagerController
      .performInstallOperation(
        installPluginRequest,
        null,
        null,
        BgProgressIndicator(),
        enabler,
      ) {
        result.complete(it)
      }

    return result.await().apply { pluginsToDisable = enabler.pluginsToDisable }
  }

  override suspend fun uninstallDynamicPlugin(sessionId: String, pluginId: PluginId, isUpdate: Boolean): Boolean {
    return DefaultUiPluginManagerController.uninstallDynamicPlugin(null, sessionId, pluginId, isUpdate)
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