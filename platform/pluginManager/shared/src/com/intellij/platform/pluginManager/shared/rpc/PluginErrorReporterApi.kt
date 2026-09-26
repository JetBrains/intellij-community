// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.shared.rpc

import com.intellij.ide.plugins.PluginInitializationErrorHandler
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface PluginErrorReporterApi : RemoteApi<Unit>, PluginInitializationErrorHandler {
  companion object {
    @JvmStatic
    suspend fun getInstance(): PluginErrorReporterApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<PluginErrorReporterApi>())
    }
  }
}