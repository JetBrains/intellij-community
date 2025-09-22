// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandler
import com.intellij.openapi.updateSettings.impl.PluginUpdateModel
import com.intellij.platform.pluginManager.shared.rpc.PluginUpdaterApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class BackendPluginUpdaterApi : PluginUpdaterApi {
  override suspend fun loadAndStorePluginUpdates(apiVersion: String?, sessionId: String): PluginUpdateModel {
    return PluginUpdateHandler.getInstance().loadAndStorePluginUpdates(apiVersion)
  }

  override suspend fun installUpdates(sessionId: String, updates: List<PluginDto>): Deferred<Boolean> {
    return serviceAsync<PluginManagerCoroutineScopeHolder>().cs.async {
      try {
        PluginUpdateHandler.getInstance().installUpdates(sessionId, updates, null, null)
      }
      catch (_: Exception) {
        return@async false
      }
      return@async true
    }
  }

  override suspend fun ignorePluginUpdates(sessionId: String) {
    PluginUpdateHandler.getInstance().ignorePluginUpdates(sessionId)
  }
}

@Service
class PluginManagerCoroutineScopeHolder(val cs: CoroutineScope)