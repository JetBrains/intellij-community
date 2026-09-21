// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.marketplace.utils.MarketplaceCustomizationService
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService.Companion.isFunctionalitySupported
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.UriUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.Random

@State(name = "PluginUpdateSources", storages = [Storage("pluginUpdateSources.xml", roamingType = RoamingType.DISABLED)])
internal class PluginUpdateSourceServiceImpl : PluginUpdateSourceService,
                                               SerializablePersistentStateComponent<PluginUpdateSourceServiceImpl.State>(State()) {

  companion object {
    fun getImplInstance(): PluginUpdateSourceServiceImpl = PluginUpdateSourceService.getInstance() as PluginUpdateSourceServiceImpl
  }

  override fun getPluginUpdateSourceId(pluginId: PluginId): PluginUpdateSourceId? {
    if (!isFunctionalitySupported()) {
      return null
    }
    val source = getPersistedPluginUpdateSourceId(pluginId)
    thisLogger().debug { "Requested pluginSourceId for $pluginId: $source" }
    if (source != null) return source
    val plugin = PluginUpdateSourcePluginsProvider.getInstance().getAllPlugins().firstOrNull { it.pluginId == pluginId }
    if (plugin.hasImplicitMarketplaceUpdateSource()) {
      return createMarketplacePluginUpdateSourceId()
    }
    return null
  }

  private fun PluginDescriptor?.hasImplicitMarketplaceUpdateSource(): Boolean =
    this != null && isBundled && allowBundledUpdate() && PluginManagerCore.isDevelopedByJetBrains(this)

  override fun setPluginUpdateSourceId(pluginId: PluginId, updateSourceId: PluginUpdateSourceId) {
    if (!isFunctionalitySupported()) {
      return
    }
    thisLogger().info("Set PluginUpdateSourceId of $pluginId to $updateSourceId")
    updateState({ copy(sources = state.sources + (pluginId.idString to updateSourceId.toXmlSerializableRepository())) }) {
      "Plugin source for $pluginId is set to $updateSourceId"
    }
  }

  override fun setPluginUpdateSourceId(plugin: PluginUiModel) {
    setPluginUpdateSourceId(plugin.pluginId, createRepository(plugin))
  }

  override fun erasePluginUpdateSourceId(pluginId: PluginId) {
    if (!isFunctionalitySupported()) {
      return
    }
    updateState({ copy(sources = state.sources - pluginId.idString) }) { "Plugin uninstalled: $pluginId" }
  }

  override fun createMarketplacePluginUpdateSourceId(): PluginUpdateSourceId {
    return createRepository(null)
  }

  override fun createCustomRepositoryPluginUpdateSourceId(host: String): PluginUpdateSourceId {
    return createRepository(host)
  }

  override fun getPersistedPluginUpdateSourceId(pluginId: PluginId): PluginUpdateSourceId? {
    val updateSourceId = state.sources[pluginId.idString]?.toPluginSourceId()
    thisLogger().debug { "Requested persisted pluginSourceId for $pluginId: $updateSourceId" }
    return updateSourceId
  }

  fun hasExplicitlySetPluginUpdateSource(pluginId: PluginId): Boolean {
    return getPersistedPluginUpdateSourceId(pluginId) != null
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

  override fun getAllSources(): List<PluginUpdateSourceId> {
    val sources = RepositoryHelper.getCustomPluginRepositoryHosts()
      .map { createRepository(it) }.distinctBy { it.host }.toMutableList()
    sources.add(createRepository(null))
    return sources
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

  fun allowUpdateFromMarketplaceWhenApplicable(
    updateSource: PluginUpdateSourceId,
    plugin: PluginDescriptor,
    safePluginIdList: List<String>,
  ): PluginUpdateSourceId {
    if (!ApplicationManager.getApplication().isInternal) return updateSource
    if (!(updateSource as Repository).isNightlyRepository) return updateSource
    if (!plugin.hasImplicitMarketplaceUpdateSource() && !safePluginIdList.contains(plugin.pluginId.idString)) return updateSource
    return Repository(isMarketplace = true, isNightlyRepository = true,
                      host = MarketplaceCustomizationService.getInstance().getPluginDownloadUrl())
  }

  internal data class State(
    @JvmField @XMap(propertyElementName = "sources", entryTagName = "entry", keyAttributeName = "pluginId")
    val sources: Map<String, XmlSerializableRepository> = emptyMap(),
  )
}

@Serializable
private data class Repository(
  override val host: @NlsSafe String,
  override val isMarketplace: Boolean,
  val isNightlyRepository: Boolean,
) : PluginUpdateSourceId {

  override fun canInstallUpdatesFrom(other: PluginUpdateSourceId): Boolean {
    if (other !is Repository) return false
    return when {
      isMarketplace && other.isMarketplace -> true
      isNightlyRepository && other.isNightlyRepository -> true
      host == other.host -> true
      else -> false
    }
  }
}

@Tag("updateSource")
internal data class XmlSerializableRepository(
  @JvmField @Attribute("host") val hostToSerialize: @NlsSafe String,
  @JvmField @Attribute("isMarketplace") val isMarketplaceToSerialize: Boolean,
  @JvmField @Attribute("isNightlyRepository") val isNightlyRepositoryToSerialize: Boolean,
) {
  @Suppress("unused")
  constructor() : this("", true, false) //for serialization

  fun toPluginSourceId(): PluginUpdateSourceId {
    return Repository(hostToSerialize,
                      isMarketplaceToSerialize,
                      isNightlyRepositoryToSerialize && ApplicationManager.getApplication().isInternal)
  }
}

private const val CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY = "intellij.plugins.custom.built.in.repository.url"

private fun createRepository(initialHost: String?): PluginUpdateSourceId {
  val isMarketplace = initialHost == null
  val host = normalizeHost(initialHost)
  return Repository(host,
                    isMarketplace,
                    isNightlyRepository(host, ApplicationManager.getApplication().isInternal))
}

private fun normalizeHost(initialHost: String?): String {
  var host = initialHost ?: MarketplaceCustomizationService.getInstance().getPluginDownloadUrl()
  host = UriUtil.trimParameters(host).trimEnd('/')
  return host
}

internal fun createRepository(model: PluginUiModel): PluginUpdateSourceId {
  return createRepository(model.repositoryName)
}

internal fun isNightlyRepository(host: String, isInternalMode: Boolean): Boolean {
  if (!isInternalMode) return false
  return System.getProperty(CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY)
    ?.split(',')
    ?.map { normalizeHost(it) }
    ?.contains(host) == true
}

private fun PluginUpdateSourceId.toXmlSerializableRepository(): XmlSerializableRepository {
  return XmlSerializableRepository(host, isMarketplace, (this as Repository).isNightlyRepository)
}

@ApiStatus.Internal
@TestOnly
fun createNightlyPluginUpdateSourceId(): PluginUpdateSourceId {
  return Repository(host = "someHost${Random().nextInt(Int.MAX_VALUE)}", isMarketplace = false, isNightlyRepository = true)
}

@ApiStatus.Internal
@TestOnly
fun createNightlyAndMarketplacePluginUpdateSourceId(): PluginUpdateSourceId {
  return Repository(host = "someHost${Random().nextInt(Int.MAX_VALUE)}", isMarketplace = true, isNightlyRepository = true)
}
