// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.shared.base.rpc

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdatesModel
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface PluginUpdaterApi : RemoteApi<Unit> {
  suspend fun loadAndStorePluginUpdates(apiVersion: String?): PluginUpdatesModel

  suspend fun installUpdates(updates: List<PluginDto>): Flow<PluginUpdateRpcEvent>

  suspend fun ignorePluginUpdates()

  companion object {
    fun tryGetInstance(): PluginUpdaterApi? {
      return LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<PluginUpdaterApi>())
    }
  }
}

@Serializable
@ApiStatus.Internal
sealed interface PluginUpdateRpcEvent {
  @Serializable
  data class DownloadProgressChanged(val pluginId: PluginId, val fraction: Double?) : PluginUpdateRpcEvent

  @Serializable
  data class Completed(val success: Boolean) : PluginUpdateRpcEvent
}
