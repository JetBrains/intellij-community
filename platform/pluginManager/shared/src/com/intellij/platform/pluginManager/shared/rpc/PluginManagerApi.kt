// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.shared.rpc

import com.intellij.platform.pluginManager.shared.dto.PluginDto
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface PluginManagerApi: RemoteApi<Unit> {
  suspend fun getPlugins(): List<PluginDto>

  companion object {
    suspend fun getInstance(): PluginManagerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<PluginManagerApi>())
    }
  }
}