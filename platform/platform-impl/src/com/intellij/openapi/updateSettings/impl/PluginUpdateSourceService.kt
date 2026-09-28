// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.PluginUtils
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.idea.AppMode
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

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
      return isFunctionalitySupported() &&
             !isPluginUpdateSourceUIAndFilteringDisabledForInternalUser() &&
             Registry.`is`(REGISTRY_KEY_FILTER_UPDATES_SETTING, false)
    }

    fun isPluginUpdateSourceShownInUI(): Boolean {
      return isFunctionalitySupported() &&
             !isPluginUpdateSourceUIAndFilteringDisabledForInternalUser() &&
             Registry.`is`("platform.make.plugin.update.source.visible.in.ui", false)
    }

    fun isMissingUpdateSourceWarningEnabled(): Boolean {
      return isFunctionalitySupported() &&
             isPluginUpdateSourceShownInUI() &&
             isPluginUpdateFilteredAgainstPluginUpdateSource()
    }

    @JvmStatic
    fun addPluginUpdateSourceFilteringRegistryListener(coroutineScope: CoroutineScope, listener: (Boolean) -> Unit) {
      val registryListener = object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          if (value.key == REGISTRY_KEY_FILTER_UPDATES_SETTING ||
              value.key == REGISTRY_KEY_DISABLE_UPDATE_SOURCES_FOR_INTERNAL_USERS) {
            listener.invoke(isPluginUpdateFilteredAgainstPluginUpdateSource())
          }
        }
      }
      RegistryManager.getInstance().get(REGISTRY_KEY_FILTER_UPDATES_SETTING).addListener(registryListener, coroutineScope)
      RegistryManager.getInstance().get(REGISTRY_KEY_DISABLE_UPDATE_SOURCES_FOR_INTERNAL_USERS).addListener(registryListener, coroutineScope)
    }
  }

  fun getPluginUpdateSourceId(pluginId: PluginId): PluginUpdateSource?

  fun setPluginUpdateSourceId(pluginId: PluginId, updateSourceId: PluginUpdateSource)

  fun setPluginUpdateSourceId(plugin: PluginUiModel)

  fun erasePluginUpdateSourceId(pluginId: PluginId)

  fun createMarketplacePluginUpdateSourceId(): PluginUpdateSource

  fun createCustomRepositoryPluginUpdateSourceId(host: String): PluginUpdateSource

  fun isMissingPluginUpdateSource(plugin: PluginDescriptor): Boolean {
    return PluginUtils.isUpdateable(plugin) && getPluginUpdateSourceId(plugin.pluginId) == null
  }

  fun getAllSources(): List<PluginUpdateSource>

  @TestOnly
  fun getPersistedPluginUpdateSourceId(pluginId: PluginId): PluginUpdateSource?
}