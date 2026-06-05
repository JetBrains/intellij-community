// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.idea.AppMode
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PluginUpdateSourceService {

  companion object {
    @JvmStatic
    fun getInstance(): PluginUpdateSourceService = service<PluginUpdateSourceService>()

    @JvmStatic
    fun isFunctionalitySupported(): Boolean {
      return AppMode.isMonolith() && Registry.`is`("platform.enable.plugin.update.source.feature", false)
    }
  }

  fun getPluginUpdateSourceId(pluginId: PluginId): PluginUpdateSourceId?

  fun setPluginUpdateSourceId(pluginId: PluginId, updateSourceId: PluginUpdateSourceId)

  /**
   * @param host null for Marketplace and download URL for a custom repository
   */
  fun setPluginUpdateSourceId(pluginId: PluginId, host: String?)

  fun erasePluginUpdateSourceId(pluginId: PluginId)
}