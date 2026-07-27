// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.idea.AppMode
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

private const val REGISTRY_KEY_FILTER_UPDATES_SETTING = "platform.limit.plugin.update.source.by.configured.one"

@ApiStatus.Internal
interface PluginUpdateSourceService {

  companion object {
    @JvmStatic
    fun getInstance(): PluginUpdateSourceService = service<PluginUpdateSourceService>()

    @JvmStatic
    fun isFunctionalitySupported(): Boolean {
      return AppMode.isMonolith() && Registry.`is`("platform.enable.plugin.update.source.feature", false)
    }

    @JvmStatic
    fun isPluginUpdateFilteredAgainstPluginUpdateSource(): Boolean {
      return isFunctionalitySupported() && Registry.`is`(REGISTRY_KEY_FILTER_UPDATES_SETTING, false)
    }

    @JvmStatic
    fun addPluginUpdateSourceFilteringRegistryListener(coroutineScope: CoroutineScope, listener: (Boolean) -> Unit) {
      RegistryManager.getInstance().get(REGISTRY_KEY_FILTER_UPDATES_SETTING).addListener(
        object : RegistryValueListener {
          override fun afterValueChanged(value: RegistryValue) {
            if (value.key == REGISTRY_KEY_FILTER_UPDATES_SETTING) {
              listener.invoke(value.asBoolean())
            }
          }
        },
        coroutineScope
      )
    }
  }

  fun getPluginUpdateSourceId(pluginId: PluginId): PluginUpdateSourceId?

  fun setPluginUpdateSourceId(pluginId: PluginId, updateSourceId: PluginUpdateSourceId)

  fun setPluginUpdateSourceId(plugin: PluginUiModel)

  fun erasePluginUpdateSourceId(pluginId: PluginId)

  fun createMarketplacePluginUpdateSourceId(): PluginUpdateSourceId

  fun createCustomRepositoryPluginUpdateSourceId(host: String): PluginUpdateSourceId
}