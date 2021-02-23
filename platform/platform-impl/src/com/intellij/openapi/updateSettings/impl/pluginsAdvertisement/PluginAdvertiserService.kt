// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.UpdateChecker.mergePluginsFromRepositories
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.ui.EditorNotifications
import com.intellij.util.containers.MultiMap
import java.io.IOException
import kotlin.collections.set

open class PluginAdvertiserService {
  @Throws(IOException::class)
  fun run(project: Project) {
    val unknownFeatures = UnknownFeaturesCollector.getInstance(project).unknownFeatures
    val extensions = PluginsAdvertiser.loadExtensions()
    if (extensions != null && unknownFeatures.isEmpty())
      return

    val features = MultiMap<PluginId?, UnknownFeature>()
    val disabledPlugins = HashMap<PluginsAdvertiser.Plugin, IdeaPluginDescriptor>()
    val customPlugins = RepositoryHelper.loadPluginsFromCustomRepositories(null)

    if (project.isDisposed) {
      return
    }

    if (extensions == null) {
      PluginsAdvertiser.loadAllExtensions(customPlugins.map { it.pluginId.idString }.toSet())
      if (project.isDisposed) {
        return
      }

      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    val ids = mutableMapOf<PluginId, PluginsAdvertiser.Plugin>()
    unknownFeatures.forEach { feature ->
      ProgressManager.checkCanceled()
      val installedPlugin = PluginFeatureService.getInstance().getPluginForFeature(
        feature.featureType,
        feature.implementationName
      )
      if (installedPlugin != null) {
        val id = PluginId.getId(installedPlugin.pluginId)
        ids[id] = PluginsAdvertiser.Plugin(installedPlugin.pluginId, installedPlugin.pluginName, installedPlugin.bundled)
        features.putValue(id, feature)
      }
      else {
        for (plugin in PluginsAdvertiser.retrieve(feature)) {
          val id = plugin.pluginId
          ids[id] = plugin
          features.putValue(id, feature)
        }
      }
    }

    //include disabled plugins
    ids.filter { (pluginId, _) ->
      PluginManagerCore.isDisabled(pluginId)
    }.mapNotNull { (pluginId, plugin) ->
      PluginManagerCore.getPlugin(pluginId)?.let {
        plugin to it
      }
    }.forEach { (plugin, pluginDescriptor) ->
      disabledPlugins[plugin] = pluginDescriptor
    }

    val bundledPlugin = PluginsAdvertiser.hasBundledPluginToInstall(ids.values)
    val plugins = mutableSetOf<PluginDownloader>()

    if (ids.isNotEmpty()) {
      mergePluginsFromRepositories(
        MarketplaceRequests.getInstance().loadLastCompatiblePluginDescriptors(ids.keys.map { it.idString }),
        customPlugins,
        true,
      ).filterNot { loadedPlugin ->
        val pluginId = loadedPlugin.pluginId
        val compareVersions = PluginManagerCore.getPlugin(pluginId)?.let {
          PluginDownloader.compareVersionsSkipBrokenAndIncompatible(loadedPlugin.version, it) <= 0
        } ?: false

        compareVersions
        || !ids.containsKey(pluginId)
        || PluginManagerCore.isDisabled(pluginId)
        || PluginManagerCore.isBrokenPlugin(loadedPlugin)
      }.map {
        PluginDownloader.createDownloader(it)
      }.forEach {
        plugins += it
      }
    }

    invokeLater(ModalityState.NON_MODAL) {
      if (project.isDisposed)
        return@invokeLater

      val (notificationMessage, notificationActions) = if (plugins.isNotEmpty() ||
                                                           disabledPlugins.isNotEmpty()) {
        val action = if (disabledPlugins.isNotEmpty()) {
          val disabledDescriptors = disabledPlugins.values
          val title = if (disabledPlugins.size == 1)
            IdeBundle.message(
              "plugins.advertiser.action.enable.plugin",
              disabledDescriptors.single().name
            )
          else
            IdeBundle.message("plugins.advertiser.action.enable.plugins")

          NotificationAction.createSimpleExpiring(title) {
            PluginsAdvertiser.logEnablePlugins(
              disabledDescriptors.map { it.pluginId.idString },
              PluginsAdvertiser.Source.NOTIFICATION,
              project,
            )

            PluginManagerConfigurable.showPluginConfigurableAndEnable(
              project,
              disabledDescriptors.toSet(),
            )
          }
        }
        else
          NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.configure.plugins")) {
            PluginsAdvertiser.logConfigurePlugins(PluginsAdvertiser.Source.NOTIFICATION, project)
            PluginsAdvertiserDialog(project, plugins.toTypedArray(), customPlugins).show()
          }

        getAddressedMessagePresentation(
          plugins,
          disabledPlugins,
          features,
        ) to listOf(
          action,
          NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.ignore.unknown.features")) {
            PluginsAdvertiser.logIgnoreUnknownFeatures(
              PluginsAdvertiser.Source.NOTIFICATION,
              project,
            )

            val collector = UnknownFeaturesCollector.getInstance(project)
            unknownFeatures.forEach { collector.ignoreFeature(it) }
          },
        )
      }
      else if (bundledPlugin.isNotEmpty()
               && !PluginsAdvertiser.isIgnoreUltimate()) {
        IdeBundle.message(
          "plugins.advertiser.ultimate.features.detected",
          bundledPlugin.joinToString()
        ) to listOf(
          NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.try.ultimate")) {
            PluginsAdvertiser.openDownloadPageAndLog(PluginsAdvertiser.Source.NOTIFICATION, project)
          },
          NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
            PluginsAdvertiser.doIgnoreUltimateAndLog(PluginsAdvertiser.Source.NOTIFICATION, project)
          },
        )
      }
      else {
        return@invokeLater
      }

      val notification = PluginsAdvertiser.getNotificationGroup().createNotification(
        "",
        notificationMessage,
        NotificationType.INFORMATION,
        null,
      )

      notification.addActions(notificationActions)
      notification.notify(project)
    }
  }

  open fun getAddressedMessagePresentation(
    plugins: Set<PluginDownloader>,
    disabledPlugins: Map<PluginsAdvertiser.Plugin, IdeaPluginDescriptor?>,
    features: MultiMap<PluginId?, UnknownFeature>
  ): @NotificationContent String {

    val ids = LinkedHashSet<PluginId>()
    plugins.forEach { plugin -> ids.add(plugin.id) }
    disabledPlugins.keys
      .map { it.pluginId }
      .forEach { ids += it }

    val addressedFeatures = MultiMap.createSet<String, String>()
    ids.forEach { id ->
      features[id].forEach { feature ->
        addressedFeatures.putValue(feature.featureDisplayName, feature.implementationDisplayName)
      }
    }

    val pluginsNumber = ids.size
    val repoPluginsNumber = plugins.size

    val entries = addressedFeatures.entrySet()
    return if (entries.size == 1) {
      val feature = entries.single()
      IdeBundle.message(
        "plugins.advertiser.missing.feature",
        pluginsNumber,
        feature.key,
        feature.value.joinToString(),
        repoPluginsNumber,
      )
    }
    else {
      IdeBundle.message(
        "plugins.advertiser.missing.features",
        pluginsNumber,
        entries.joinToString(separator = "; ") { it.value.joinToString(prefix = it.key + ": ") },
        repoPluginsNumber,
      )
    }
  }
}
