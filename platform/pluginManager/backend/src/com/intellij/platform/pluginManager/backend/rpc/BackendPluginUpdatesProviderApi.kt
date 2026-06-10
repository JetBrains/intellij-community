// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.ide.plugins.newui.PluginUpdatesProvider
import com.intellij.platform.pluginManager.shared.rpc.PluginUpdatesProviderApi
import kotlinx.coroutines.flow.Flow

internal class BackendPluginUpdatesProviderApi() : PluginUpdatesProviderApi {
  private val delegate = PluginUpdatesProvider.getInstances().first()

  override suspend fun pluginUpdateEvents(): Flow<PluginUpdatesEvent?> {
    return delegate.pluginUpdateEvents()
  }

  override suspend fun update() {
    delegate.update()
  }
}
