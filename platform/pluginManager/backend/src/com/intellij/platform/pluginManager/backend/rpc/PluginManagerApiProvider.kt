// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.pluginManager.shared.rpc.PluginInstallerApi
import com.intellij.platform.pluginManager.shared.rpc.PluginManagerApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
internal class PluginManagerApiProvider: RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<PluginManagerApi>()) {
      BackendPluginManagerApi()
    }

    remoteApi(remoteApiDescriptor<PluginInstallerApi>()) {
      BackendPluginInstallerApi()
    }
  }
}