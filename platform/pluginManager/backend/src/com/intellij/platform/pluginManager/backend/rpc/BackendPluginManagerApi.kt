// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.platform.pluginManager.shared.rpc.PluginManagerApi
import com.intellij.platform.pluginManager.shared.dto.PluginDto
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendPluginManagerApi : PluginManagerApi {
  override suspend fun getPlugins(): List<PluginDto> {
    return PluginManagerCore.plugins.map {
      PluginDto(it.name, it.pluginId)
    }
  }
}