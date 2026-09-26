// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.DynamicPlugins
import com.intellij.ide.plugins.InstallFromDiskAction
import com.intellij.ide.plugins.InstalledPluginsTableModel
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.getMainDescriptor
import com.intellij.ide.plugins.marketplace.ApplyPluginsStateResult
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.ide.plugins.marketplace.PluginInstalledFromDiskResult
import com.intellij.ide.plugins.marketplace.PrepareToUninstallResult
import com.intellij.ide.plugins.marketplace.ResetPluginsStateResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.ide.plugins.newui.PluginInstallationProgressSink
import com.intellij.ide.plugins.newui.PluginManagerSessionService
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.SessionStatePluginEnabler
import com.intellij.ide.plugins.newui.withWholePercentDownloadProgress
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.pluginManager.shared.rpc.PluginInstallerApi
import com.intellij.platform.pluginManager.shared.rpc.PluginInstallRpcEvent
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.IOException

internal class BackendPluginInstallerApi : PluginInstallerApi {

  override suspend fun unloadDynamicPlugin(pluginId: PluginId, isUpdate: Boolean): Boolean {
    val pluginDescriptor = PluginManagerCore.findPlugin(pluginId)?.getMainDescriptor() ?: return false
    return PluginInstaller.unloadDynamicPlugin(pluginDescriptor)
  }

  override suspend fun resetSession(sessionId: String, removeSession: Boolean): ResetPluginsStateResult {
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
        InstallFromDiskAction.installPluginFromDisk(null, project, InstalledPluginsTableModel(project), PluginEnabler.HEADLESS, null, {
          trySend(PluginInstalledFromDiskResult(PluginDescriptorConverter.toPluginDto(it.pluginDescriptor), it.restartNeeded))
        }, { _, _ -> })
      }
    }
  }

  override suspend fun installOrUpdatePlugin(
    sessionId: String,
    descriptor: PluginDto,
    updateDescriptor: PluginDto?,
    installSource: FUSEventSource?,
    customRepoPlugins: List<PluginDto>?,
  ): Flow<PluginInstallRpcEvent> {
    return installPlugin(sessionId) { enabler, progressSink ->
      DefaultUiPluginManagerController.installOrUpdatePlugin(sessionId,
                                                             { null },
                                                             descriptor,
                                                             updateDescriptor,
                                                             installSource,
                                                             null,
                                                             enabler,
                                                             customRepoPlugins,
                                                             progressSink)
    }
  }

  override suspend fun continueInstallation(
    sessionId: String,
    pluginId: PluginId,
    enableRequiredPlugins: Boolean,
    allowInstallWithoutRestart: Boolean,
    customRepoPlugins: List<PluginDto>?,
  ): Flow<PluginInstallRpcEvent> {
    return installPlugin(sessionId) { enabler, progressSink ->
      DefaultUiPluginManagerController.continueInstallation(sessionId,
                                                            pluginId,
                                                            enableRequiredPlugins,
                                                            allowInstallWithoutRestart,
                                                            enabler,
                                                            null,
                                                            { null },
                                                            customRepoPlugins,
                                                            progressSink)
    }
  }

  override suspend fun isRestartRequired(sessionId: String): Boolean {
    return DefaultUiPluginManagerController.isRestartRequired(sessionId)
  }

  private fun installPlugin(
    sessionId: String,
    installOperation: suspend (PluginEnabler, PluginInstallationProgressSink) -> InstallPluginResult,
  ): Flow<PluginInstallRpcEvent> = channelFlow {
    val session = PluginManagerSessionService.getInstance().getSession(sessionId)
    if (session == null) {
      send(PluginInstallRpcEvent.Completed(InstallPluginResult.FAILED))
      return@channelFlow
    }
    val enabler = SessionStatePluginEnabler(session)
    val progressSink = object : PluginInstallationProgressSink {
      override fun dependenciesScheduled(dependencies: List<PluginUiModel>) {
        trySend(PluginInstallRpcEvent.DependenciesScheduled(dependencies.map(PluginDto::fromModel)))
      }

      override fun downloadProgressChanged(fraction: Double?) {
        trySend(PluginInstallRpcEvent.DownloadProgressChanged(fraction))
      }
    }.withWholePercentDownloadProgress()
    val result = installOperation(enabler, progressSink)
    send(PluginInstallRpcEvent.Completed(result.apply { pluginsToDisable = enabler.pluginsToDisable }))
  }.buffer(Channel.UNLIMITED)

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
}

private val LOG = Logger.getInstance(BackendPluginInstallerApi::class.java)
