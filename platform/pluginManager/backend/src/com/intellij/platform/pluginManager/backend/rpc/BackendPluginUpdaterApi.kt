// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.components.service
import com.intellij.openapi.updateSettings.impl.PluginAutoUpdateService
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandler
import com.intellij.openapi.updateSettings.impl.PluginUpdateProgressSink
import com.intellij.openapi.updateSettings.impl.PluginUpdatesModel
import com.intellij.openapi.updateSettings.impl.withWholePercentDownloadProgress
import com.intellij.platform.pluginManager.shared.base.rpc.PluginUpdaterApi
import com.intellij.platform.pluginManager.shared.base.rpc.PluginUpdateRpcEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendPluginUpdaterApi : PluginUpdaterApi {
  override suspend fun loadAndStorePluginUpdates(apiVersion: String?): PluginUpdatesModel {
    val updates = PluginUpdateHandler.getInstance().loadAndStorePluginUpdates(apiVersion)
    val pluginAutoUpdateService = service<PluginAutoUpdateService>()
    if (pluginAutoUpdateService.isAutoUpdateEnabled()) {
      pluginAutoUpdateService.onPluginUpdatesChecked(updates.downloaders)
    }
    return updates
  }

  override suspend fun installUpdates(updates: List<PluginDto>): Flow<PluginUpdateRpcEvent> {
    return channelFlow {
      val completion = CompletableDeferred<Unit>()
      val progressSink = PluginUpdateProgressSink { pluginId, fraction ->
        trySend(PluginUpdateRpcEvent.DownloadProgressChanged(pluginId, fraction))
      }.withWholePercentDownloadProgress()
      val success = try {
        PluginUpdateHandler.getInstance().installUpdates(
          updates,
          null,
          Runnable {
            completion.complete(Unit)
          },
          progressSink = progressSink,
        )
        completion.await()
        true
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (_: Exception) {
        false
      }
      send(PluginUpdateRpcEvent.Completed(success))
    }
  }

  override suspend fun ignorePluginUpdates() {
    PluginUpdateHandler.getInstance().ignorePluginUpdates()
  }
}
