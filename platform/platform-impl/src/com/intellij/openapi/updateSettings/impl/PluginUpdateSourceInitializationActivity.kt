// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtils
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.UnifiedPluginsPageFeature
import com.intellij.ide.plugins.newui.SearchWords
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
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
  private val startupMutex = Mutex()
  private var startupAttempted = false

  @ApiStatus.Internal
  sealed interface Result {
    data class Success(
      val loadedPluginsWithoutUpdateSource: Int,
    ) : Result

    data class Failure(
      val errorMessage: @NlsContexts.NotificationContent String? = null,
    ) : Result
  }

  @TestOnly
  fun resetStartupAttemptForTests() {
    startupAttempted = false
  }

  suspend fun initialize() {
    if (!PluginUpdateSourceService.isFunctionalitySupported()) return
    if (!Registry.`is`("update.source.initialization.enabled", false)) return
    withContext(Dispatchers.IO) {
      startupMutex.withLock {
        if (!startupAttempted) {
          enforceInitialization()
          startupAttempted = true
        }
      }
    }
  }

  private fun needsInitialization(service: PluginUpdateSourceServiceImpl, plugin: PluginDescriptor): Boolean = when {
    !PluginUtils.isUpdateable(plugin) -> false
    // Plugins with predefined Marketplace update source may still be overwritten in custom repositories and
    // should go through initialization
    service.hasExplicitlySetPluginUpdateSource(plugin.pluginId) -> false
    else -> true
  }

  fun enforceInitialization(): Result {
    val plugins = PluginUpdateSourcePluginsProvider.getInstance().getAllPlugins()
    val service = PluginUpdateSourceServiceImpl.getImplInstance()
    if (plugins.none { needsInitialization(service, it) }) {
      thisLogger().info("No initialization of plugin update sources needed")
      return Result.Success(0)
    }

    val dataMap = mutableMapOf<PluginId, MutableList<PluginUpdateSource>>()

    val customPluginRepositoryHosts = RepositoryHelper.getCustomPluginRepositoryHosts()
    thisLogger().info("Repositories: $customPluginRepositoryHosts")
    val handledRepos = mutableSetOf<String>()
    for (host in customPluginRepositoryHosts) {
      if (!handledRepos.add(host)) continue
      val updateSourceId = service.createCustomRepositoryPluginUpdateSourceId(host)

      if (host.isEmpty()) {
        thisLogger().info("No plugins in $host")
        continue
      }
      val pluginResult = runCatching { RepositoryHelper.loadPluginModels(host, null, null) }
      val pluginModels = pluginResult.getOrHandleException {
        thisLogger().warn("Failed to get the plugin list from repository $host. Plugin update sources will be initialized next time.", it)
      } ?: return Result.Failure(
        IdeBundle.message("notification.content.plugin.update.sources.initialization.repository.error", host)
      )

      thisLogger().info("Plugins in $host:\n${pluginModels.joinToString(",") { it.pluginId.idString }}")

      for (model in pluginModels) {
        val pluginUpdateSourceIds = dataMap.getOrPut(model.pluginId) { mutableListOf() }
        pluginUpdateSourceIds.add(updateSourceId)
      }
    }

    val safePluginList = getSafePluginIdList()
    if (safePluginList == null) {
      thisLogger().warn("Failed to get the list of trusted plugin IDs. Plugin update sources will be initialized next time.")
      return Result.Failure()
    }

    val marketplaceUpdateSourceId = service.createMarketplacePluginUpdateSourceId()
    plugins.forEach { plugin ->
      if (needsInitialization(service, plugin)) {
        val customPluginUpdateSources = dataMap[plugin.pluginId] ?: emptyList()
        val newUpdateSourceId = getUpdateSourceToInitialize(plugin,
                                                            customPluginUpdateSources,
                                                            safePluginList, marketplaceUpdateSourceId)
        if (newUpdateSourceId != null) {
          thisLogger().info("Set ${newUpdateSourceId.getPresentableName()} update source to ${plugin.pluginId}")
          service.setPluginUpdateSourceId(plugin.pluginId, newUpdateSourceId)
        }
      }
      else {
        thisLogger().info("Plugin ${plugin.pluginId} doesn't need update source init")
      }
    }

    thisLogger().info("Initialization of plugin update sources finished successfully")
    val pluginsWithoutUpdateSource = getUpdateablePluginsWithoutUpdateSource(PluginManagerCore.loadedPlugins)
    logUpdateablePluginsWithoutUpdateSource(pluginsWithoutUpdateSource)
    return Result.Success(pluginsWithoutUpdateSource.size)
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

  private fun getUpdateSourceToInitialize(
    plugin: PluginDescriptor,
    customPluginUpdateSources: List<PluginUpdateSource>,
    safePluginIdList: List<String>,
    marketplaceUpdateSourceId: PluginUpdateSource,
  ): PluginUpdateSource? {
    val pluginId = plugin.pluginId

    val singleCustomRepoUpdateSource = customPluginUpdateSources.singleCompatibleSourceOrNull()
    return when {
      singleCustomRepoUpdateSource != null -> {
        PluginUpdateSourceServiceImpl.getImplInstance()
          .allowUpdateFromMarketplaceWhenApplicable(singleCustomRepoUpdateSource, plugin, safePluginIdList)
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
  }

  private fun logUpdateablePluginsWithoutUpdateSource(plugins: List<PluginDescriptor>) {
    val message: String
    if (plugins.isEmpty()) {
      message = "No loaded updateable plugins without an update source after initialization"
    }
    else {
      val pluginNames = plugins.joinToString { "${it.pluginId.idString} (${it.name})" }
      message = "Loaded updateable plugins without update source after initialization: $pluginNames"
    }
    thisLogger().info(message)
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
