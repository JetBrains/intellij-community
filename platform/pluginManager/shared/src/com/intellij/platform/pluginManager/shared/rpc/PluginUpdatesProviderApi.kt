// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.shared.rpc

import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@Rpc
@Suppress("NonSerializableTypeInRpcInterface")
@ApiStatus.Internal
interface PluginUpdatesProviderApi : RemoteApi<Unit> {
  suspend fun pluginUpdateEvents(): Flow<PluginUpdatesEvent?>
  suspend fun update()

  companion object {
    suspend fun getInstance(): PluginUpdatesProviderApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<PluginUpdatesProviderApi>())
    }
  }
}
