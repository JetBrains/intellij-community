// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.shared.rpc

import com.intellij.ide.plugins.InstallPluginRequest
import com.intellij.ide.plugins.marketplace.*
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
@IntellijInternalApi
interface PluginInstallerApi : RemoteApi<Unit> {
  suspend fun unloadDynamicPlugin(pluginId: PluginId, isUpdate: Boolean): Boolean
  suspend fun uninstallDynamicPlugin(sessionId: String, pluginId: PluginId, isUpdate: Boolean): Boolean
  suspend fun deletePluginFiles(pluginId: PluginId)
  suspend fun performUninstall(sessionId: String, id: PluginId): Boolean
  suspend fun performInstallOperation(installPluginRequest: InstallPluginRequest): InstallPluginResult

  suspend fun allowLoadUnloadWithoutRestart(pluginId: String): Boolean
  suspend fun allowLoadUnloadSynchronously(pluginId: PluginId): Boolean
  suspend fun applyPluginSession(sessionId: String, projectId: ProjectId?): ApplyPluginsStateResult
  suspend fun getApplySessionError(sessionId: String): String?
  suspend fun updatePluginDependencies(sessionId: String): Set<PluginId>
  suspend fun isModified(sessionId: String): Boolean
  suspend fun resetSession(sessionId: String, removeSession: Boolean): Map<PluginId, Boolean>
  suspend fun prepareToUninstall(pluginsToUninstall: List<PluginId>): PrepareToUninstallResult
  suspend fun getErrors(sessionId: String, pluginId: PluginId): CheckErrorsResult
  suspend fun setEnableStateForDependencies(sessionId: String, descriptorIds: Set<PluginId>, enable: Boolean, ): SetEnabledStateResult
  suspend fun installPluginFromDisk(projectId: ProjectId?): PluginInstalledFromDiskResult

  companion object {
    suspend fun getInstance(): PluginInstallerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<PluginInstallerApi>())
    }
  }
}