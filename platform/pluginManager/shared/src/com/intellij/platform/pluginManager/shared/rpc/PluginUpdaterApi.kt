// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.shared.rpc

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.updateSettings.impl.PluginUpdatesModel
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
@IntellijInternalApi
interface PluginUpdaterApi : RemoteApi<Unit> {
  suspend fun loadAndStorePluginUpdates(apiVersion: String?, sessionId: String): PluginUpdatesModel

  suspend fun installUpdates(sessionId: String, updates: List<PluginDto>): Deferred<Boolean>

  suspend fun ignorePluginUpdates(sessionId: String)

  companion object {
    suspend fun getInstance(): PluginUpdaterApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<PluginUpdaterApi>())
    }
  }
}