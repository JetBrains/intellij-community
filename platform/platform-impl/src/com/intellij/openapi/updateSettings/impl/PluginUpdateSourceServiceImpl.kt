// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.utils.MarketplaceCustomizationService
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService.Companion.isFunctionalitySupported
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap
import org.apache.http.client.utils.URIBuilder
import org.jetbrains.annotations.NonNls

@State(name = "PluginUpdateSources", storages = [Storage("pluginUpdateSources.xml", roamingType = RoamingType.DISABLED)])
internal class PluginUpdateSourceServiceImpl : PluginUpdateSourceService,
                                               SerializablePersistentStateComponent<PluginUpdateSourceServiceImpl.State>(State()) {

  override fun getPluginUpdateSourceId(pluginId: PluginId): PluginUpdateSourceId? {
    if (!isFunctionalitySupported()) {
      return null
    }
    val source = state.sources[pluginId.idString]
    thisLogger().debug { "Requested pluginSourceId for $pluginId: $source" }
    return source
  }

  override fun setPluginUpdateSourceId(pluginId: PluginId, updateSourceId: PluginUpdateSourceId) {
    if (!isFunctionalitySupported()) {
      return
    }
    thisLogger().info("Set PluginUpdateSourceId of $pluginId to $updateSourceId")
    updateState({ copy(sources = state.sources + (pluginId.idString to updateSourceId.toRepository())) }) {
      "Plugin source for $pluginId is set to $updateSourceId"
    }
  }

  override fun setPluginUpdateSourceId(plugin: PluginUiModel) {
    setPluginUpdateSourceId(plugin.pluginId, plugin.repositoryName)
  }

  private fun setPluginUpdateSourceId(pluginId: PluginId, host: String?) {
    if (!isFunctionalitySupported()) {
      return
    }
    setPluginUpdateSourceId(pluginId, createRepository(host))
  }

  override fun erasePluginUpdateSourceId(pluginId: PluginId) {
    if (!isFunctionalitySupported()) {
      return
    }
    updateState({ copy(sources = state.sources - pluginId.idString) }) { "Plugin uninstalled: $pluginId" }
  }

  private fun updateState(
    update: State.() -> State,
    lazyMessage: () -> @NonNls String,
  ) {
    updateState(update)
    thisLogger().apply {
      if (isTraceEnabled) {
        trace(RuntimeException(lazyMessage()))
      }
      else if (isDebugEnabled) {
        debug(null as Throwable?, lazyMessage)
      }
    }
  }

  override fun loadState(state: State) {
    if (!isFunctionalitySupported()) {
      // do not lose settings that may have come from monolith runs; just ignore them in split mode
      super.loadState(state)
      return
    }
    thisLogger().debug { "Loading state $state" }
    val installedPlugins = state.sources.toMutableMap()
    //don't lose source information for disabled/not loaded plugins, use PluginManagerCore.plugins
    val installedPluginIds = PluginManagerCore.plugins.map { it.pluginId.idString }.toSet()
    installedPlugins.keys.retainAll(installedPluginIds)
    updateState({ State(installedPlugins) }) {
      "Removed uninstalled plugins on load(): $state"
    }
  }

  internal fun resetPluginUpdateSources() {
    updateState({ State() }) {
      "Reset plugin update sources"
    }
  }

  internal data class State(
    @JvmField @XMap(propertyElementName = "sources", entryTagName = "entry", keyAttributeName = "pluginId")
    val sources: Map<String, Repository> = emptyMap(),
  )
}

@Tag("updateSource")
internal data class Repository(
  @JvmField @Attribute("host") val hostToSerialize: @NlsSafe String,
  @JvmField @Attribute("isMarketplace") val isMarketplaceToSerialize: Boolean,
) : PluginUpdateSourceId {
  constructor() : this("", true)//for serialization

  override val host: @NlsSafe String get() = hostToSerialize
  override val isMarketplace: Boolean get() = isMarketplaceToSerialize
}

internal fun createRepository(initialHost: String?): PluginUpdateSourceId {
  val isMarketplace = initialHost == null
  var host = initialHost ?: MarketplaceCustomizationService.getInstance().getPluginDownloadUrl()
  host = URIBuilder(host).removeQuery().build().toString()
  host = host.trimEnd('/')
  return Repository(host, isMarketplace)
}

private fun PluginUpdateSourceId.toRepository(): Repository {
  return Repository(host, isMarketplace)
}
