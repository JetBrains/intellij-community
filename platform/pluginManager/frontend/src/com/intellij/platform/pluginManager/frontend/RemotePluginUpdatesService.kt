// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.frontend

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesService
import com.intellij.openapi.components.service
import com.intellij.platform.pluginManager.shared.rpc.PluginManagerApi
import com.intellij.platform.util.coroutines.childScope
import fleet.rpc.client.durable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Internal
open class RemotePluginUpdatesService(private val sessionId: String) : PluginUpdatesService() {

  internal val coroutineScope = service<BackendRpcCoroutineContext>().coroutineScope.childScope("RemotePluginUpdatesServiceScope")

  override fun calculateUpdates(callback: Consumer<in Collection<PluginUiModel>>) {
    coroutineScope.launch {
      durable {
        PluginManagerApi.getInstance().subscribeToPluginUpdates(sessionId).collect {
          callback.accept(it)
        }
      }
    }
  }

  override fun recalculateUpdates() {
    coroutineScope.launch {
      PluginManagerApi.getInstance().recalculatePluginUpdates(sessionId)
    }
  }

  override fun dispose() {
    coroutineScope.launch {
      PluginManagerApi.getInstance().disposeUpdaterService(sessionId)
      coroutineScope.cancel()
    }
  }

  override fun finishUpdate() {
    coroutineScope.launch {
      PluginManagerApi.getInstance().notifyUpdateFinished(sessionId)
    }
  }
}