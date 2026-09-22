// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.PluginUtils
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.UnifiedPluginsPageFeature
import com.intellij.ide.plugins.newui.SearchWords
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ResourceUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
@ApiStatus.Internal
class PluginUpdateSourceInitializationActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    PluginUpdateSourceInitializer.initialize()
    PluginsMissingUpdateSourceNotifier.notify(project)
  }
}

@ApiStatus.Internal
object PluginsMissingUpdateSourceNotifier {

  fun notify(project: Project?) {
    if (!PluginUpdateSourceService.isMissingUpdateSourceWarningEnabled()) return
    val problemPlugins = getUpdateablePluginsWithoutUpdateSource(PluginManagerCore.loadedPlugins).sortedBy { it.name }

    val title: @NlsContexts.NotificationTitle String
    val message: @NlsContexts.NotificationContent String
    when (problemPlugins.size) {
      0 -> return
      1 -> {
        title = IdeBundle.message("notification.missing.plugin.update.source.title")
        message = IdeBundle.message("notification.missing.plugin.update.source.message", problemPlugins[0].name)
      }
      2 -> {
        title = IdeBundle.message("notification.missing.plugin.update.sources.title")
        message =
          IdeBundle.message("notification.missing.two.plugin.update.sources.message", problemPlugins[0].name, problemPlugins[1].name)
      }
      else -> {
        title = IdeBundle.message("notification.missing.plugin.update.sources.title")
        message = IdeBundle.message("notification.missing.plugin.update.sources.message", problemPlugins[0].name, problemPlugins.size - 1)
      }
    }

    @Suppress("DialogTitleCapitalization") // should be @NotificationContent, see com.intellij.notification.NotificationAction
    val listener = object : AnAction(IdeBundle.message("notification.missing.plugin.update.action")) {
      override fun actionPerformed(e: AnActionEvent) {
        val configurable = PluginManagerConfigurable()
        ShowSettingsUtil.getInstance().editConfigurable(
          project,
          configurable,
          Runnable {
            if (UnifiedPluginsPageFeature.isEnabled()) {
              configurable.navigateToInstalled(SearchWords.PLUGIN_UPDATE_SOURCE.value + null.getPresentableName())
            }
            else {
              configurable.navigateToInstalled("")
              @Suppress("DEPRECATION")  //case will be removed after full switching to UnifiedPluginsPageFeature
              val search = configurable.enableSearch(SearchWords.PLUGIN_UPDATE_SOURCE.value + null.getPresentableName())
              if (search != null) {
                ApplicationManager.getApplication().invokeLater(search)
              }
            }
          }
        )
      }
    }

    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("Missing Plugin Update Source")
      .createNotification(title, message, NotificationType.WARNING)
    notification.addAction(listener)
    notification.setImportant(true)
    notification.notify(project)
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
    val dataMap = mutableMapOf<PluginId, MutableList<PluginUpdateSource>>()

    val updateSourceIds = mutableSetOf<PluginUpdateSource>()
    for (host in RepositoryHelper.getCustomPluginRepositoryHosts()) {
      val updateSourceId = PluginUpdateSourceService.getInstance().createCustomRepositoryPluginUpdateSourceId(host)
      if (!updateSourceIds.add(updateSourceId)) continue

      if (host.isEmpty()) continue
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
    PluginUpdateSourcePluginsProvider.getInstance().getAllPlugins().forEach { plugin ->
      val customPluginUpdateSources = dataMap[plugin.pluginId] ?: emptyList()
      initializePluginUpdateSourceIfNeeded(plugin, customPluginUpdateSources, safePluginList, marketplaceUpdateSourceId)
    }

    thisLogger().info("Initialization of plugin update sources finished successfully")
    logUpdateablePluginsWithoutUpdateSource()
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
    plugin: PluginDescriptor,
    customPluginUpdateSources: List<PluginUpdateSource>,
    safePluginIdList: List<String>,
    marketplaceUpdateSourceId: PluginUpdateSource,
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
    if (pluginUpdateSourceId != null && PluginUpdateSourceServiceImpl.getImplInstance().hasExplicitlySetPluginUpdateSource(pluginId)) {
      thisLogger().info("Plugin $pluginId already has update source")
      return
    }

    val singleCustomRepoUpdateSource = customPluginUpdateSources.singleCompatibleSourceOrNull()
    val newUpdateSourceId = when {
      singleCustomRepoUpdateSource != null -> {
        PluginUpdateSourceServiceImpl.getImplInstance().allowUpdateFromMarketplaceWhenApplicable(singleCustomRepoUpdateSource, plugin, safePluginIdList)
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

  private fun logUpdateablePluginsWithoutUpdateSource() {
    val plugins = getUpdateablePluginsWithoutUpdateSource(PluginManagerCore.loadedPlugins)
    if (plugins.isEmpty()) {
      thisLogger().info("No loaded updateable plugins without an update source after initialization")
      return
    }

    val pluginNames = plugins.joinToString { "${it.pluginId.idString} (${it.name})" }
    thisLogger().info("Loaded updateable plugins without update source after initialization: $pluginNames")
  }

  private fun List<PluginUpdateSource>.singleCompatibleSourceOrNull(): PluginUpdateSource? {
    val firstSource = firstOrNull() ?: return null
    return firstSource.takeIf { all { source -> firstSource.canInstallUpdatesFrom(source) } }
  }
}

private fun getUpdateablePluginsWithoutUpdateSource(plugins: Collection<PluginDescriptor>): List<PluginDescriptor> {
  val service = PluginUpdateSourceService.getInstance()
  return plugins.filter { plugin ->
    PluginUtils.isUpdateable(plugin) && service.getPluginUpdateSourceId(plugin.pluginId) == null
  }
}
