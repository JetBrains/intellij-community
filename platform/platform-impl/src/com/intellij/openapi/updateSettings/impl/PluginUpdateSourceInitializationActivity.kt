// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtils
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ResourceUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
@ApiStatus.Internal
class PluginUpdateSourceInitializationActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    PluginUpdateSourceInitializer.initialize()
  }
}

@ApiStatus.Internal
object PluginUpdateSourceInitializer {
  private const val INITIALIZATION_HAPPENED_PROPERTY: String = "initialize.plugin.update.sources"

  fun hasInitializationHappened(): Boolean {
    return PropertiesComponent.getInstance().getBoolean(INITIALIZATION_HAPPENED_PROPERTY, false)
  }

  fun allowInitializationHappenAgain() {
    return PropertiesComponent.getInstance().unsetValue(INITIALIZATION_HAPPENED_PROPERTY)
  }

  fun initialize() {
    if (!PluginUpdateSourceService.isFunctionalitySupported()) return
    if (!Registry.`is`("update.source.initialization.enabled", false)) return
    if (!PropertiesComponent.getInstance().updateValue(INITIALIZATION_HAPPENED_PROPERTY, true)) return
    enforceInitialization()
  }

  fun enforceInitialization(): Boolean {
    val success = doInitializePlugins()
    if (!success) {
      PropertiesComponent.getInstance().setValue(INITIALIZATION_HAPPENED_PROPERTY, false)
    }
    return success
  }

  /**
   * Returns if initialization was successful
   */
  private fun doInitializePlugins(): Boolean {
    val dataMap = mutableMapOf<PluginId, MutableList<PluginUpdateSourceId>>()

    val updateSourceIds = mutableSetOf<PluginUpdateSourceId>()
    for (host in RepositoryHelper.getCustomPluginRepositoryHosts()) {
      val updateSourceId = PluginUpdateSourceService.getInstance().createCustomRepositoryPluginUpdateSourceId(host)
      if (!updateSourceIds.add(updateSourceId)) continue

      val pluginResult = runCatching { RepositoryHelper.loadPluginModels(host, null, null) }
      val pluginModels = pluginResult.getOrHandleException {
        thisLogger().warn("Fail to get plugin list from repository $host; plugin update sources would be initialized next time", it)
      } ?: return false

      for (model in pluginModels) {
        val pluginUpdateSourceIds = dataMap.getOrPut(model.pluginId) { mutableListOf() }
        pluginUpdateSourceIds.add(updateSourceId)
      }
    }

    val safePluginList = getSafePluginIdList()
    if (safePluginList == null) {
      thisLogger().warn("Fail to get list of trusted plugin ids; plugin update sources would be initialized next time")
      return false
    }

    val marketplaceUpdateSourceId = PluginUpdateSourceService.getInstance().createMarketplacePluginUpdateSourceId()
    PluginManagerCore.plugins.forEach { plugin ->
      val customPluginUpdateSources = dataMap[plugin.pluginId] ?: emptyList()
      initializePluginUpdateSourceIfNeeded(plugin, customPluginUpdateSources, safePluginList, marketplaceUpdateSourceId)
    }

    thisLogger().info("Initialization of plugin update sources finished successfully")
    return true
  }

  private fun getSafePluginIdList(): List<String>? {
    val descriptionStream =
      ResourceUtil.getResourceAsStream(this.javaClass.classLoader, "plugins", "pluginsToInitializeMarketplaceUpdateSource.txt")
    if (descriptionStream == null) {
      thisLogger().error("Unable to load plugins from pluginsToInitializeMarketplaceUpdateSource.txt")
      return null
    }
    val safePluginListText = runCatching { ResourceUtil.loadText(descriptionStream) }.getOrHandleException { exception ->
      thisLogger().error("Unable to read pluginsToInitializeMarketplaceUpdateSource.txt", exception)
      return null
    }
    return safePluginListText?.trimEnd()?.lines()
  }

  private fun initializePluginUpdateSourceIfNeeded(
    plugin: IdeaPluginDescriptor,
    customPluginUpdateSources: List<PluginUpdateSourceId>,
    safePluginIdList: List<String>,
    marketplaceUpdateSourceId: PluginUpdateSourceId,
  ) {
    val pluginId = plugin.pluginId
    val service = PluginUpdateSourceService.getInstance()
    // Plugins with predefined Marketplace update source may still be overwritten in custom repositories and
    // should go through initialization
    if (!PluginUtils.isUpdateable(plugin)) {
      thisLogger().info("Plugin $pluginId doesn't need update source")
      return
    }
    val pluginUpdateSourceId = service.getPluginUpdateSourceId(pluginId)
    if (pluginUpdateSourceId != null && (service as PluginUpdateSourceServiceImpl).hasExplicitlySetPluginUpdateSource(pluginId)) {
      thisLogger().info("Plugin $pluginId already has update source")
      return
    }

    val singleCustomRepoUpdateSource = customPluginUpdateSources.singleOrNull()
    val newUpdateSourceId = when {
      singleCustomRepoUpdateSource != null -> {
        singleCustomRepoUpdateSource
      }
      customPluginUpdateSources.isNotEmpty() -> {
        thisLogger().info("Plugin $pluginId is found in multiple custom repositories: $customPluginUpdateSources")
        null
      }
      safePluginIdList.contains(pluginId.idString) -> {
        marketplaceUpdateSourceId
      }
      else -> {
        thisLogger().info("Plugin $pluginId is not found in custom repositories and is not in the safe list")
        null
      }
    }
    if (newUpdateSourceId != null) {
      thisLogger().info("Set ${newUpdateSourceId.getPresentableName()} update source to $pluginId")
      service.setPluginUpdateSourceId(pluginId, newUpdateSourceId)
    }
  }
}
