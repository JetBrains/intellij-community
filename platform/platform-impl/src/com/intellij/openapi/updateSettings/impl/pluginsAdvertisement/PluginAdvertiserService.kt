// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginFeatureService
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.util.containers.MultiMap

open class PluginAdvertiserService {

  companion object {
    @JvmStatic
    val instance
      get() = service<PluginAdvertiserService>()
  }

  fun run(
    project: Project,
    customPlugins: List<IdeaPluginDescriptor>,
    unknownFeatures: Set<UnknownFeature>,
  ) {
    val features = MultiMap.createSet<PluginId, UnknownFeature>()
    val disabledPlugins = HashMap<PluginData, IdeaPluginDescriptor>()

    val ids = mutableMapOf<PluginId, PluginData>()
    val marketplaceRequests = MarketplaceRequests.Instance
    unknownFeatures.forEach { feature ->
      ProgressManager.checkCanceled()
      val featureType = feature.featureType
      val implementationName = feature.implementationName
      val installedPluginData = PluginFeatureService.instance
        .getPluginForFeature(featureType, implementationName)
        ?.pluginData

      fun putFeature(data: PluginData) {
        val id = data.pluginId
        ids[id] = data
        features.putValue(id, feature)
      }

      if (installedPluginData != null) {
        putFeature(installedPluginData)
      }
      else {
        marketplaceRequests
          .getFeatures(featureType, implementationName)
          .mapNotNull { it.toPluginData() }
          .forEach { putFeature(it) }
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

    val bundledPlugin = getBundledPluginToInstall(ids.values)
    val plugins = mutableSetOf<PluginDownloader>()

    if (ids.isNotEmpty()) {
      UpdateChecker.mergePluginsFromRepositories(
        marketplaceRequests.loadLastCompatiblePluginDescriptors(ids.keys),
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
            FUSEventSource.NOTIFICATION.logEnablePlugins(
              disabledDescriptors.map { it.pluginId.idString },
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
            FUSEventSource.NOTIFICATION.logConfigurePlugins(project)
            PluginsAdvertiserDialog(project, plugins, customPlugins).show()
          }

        getAddressedMessagePresentation(
          plugins,
          disabledPlugins.values,
          features,
        ) to listOf(
          action,
          NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.ignore.unknown.features")) {
            FUSEventSource.NOTIFICATION.logIgnoreUnknownFeatures(project)

            val collector = UnknownFeaturesCollector.getInstance(project)
            unknownFeatures.forEach { collector.ignoreFeature(it) }
          },
        )
      }
      else if (bundledPlugin.isNotEmpty()
               && !isIgnoreUltimate) {
        IdeBundle.message(
          "plugins.advertiser.ultimate.features.detected",
          bundledPlugin.joinToString()
        ) to listOf(
          NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.try.ultimate")) {
            FUSEventSource.NOTIFICATION.openDownloadPageAndLog(project)
          },
          NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
            FUSEventSource.NOTIFICATION.doIgnoreUltimateAndLog(project)
          },
        )
      }
      else {
        return@invokeLater
      }

      val notification = notificationGroup.createNotification(
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
    disabledPlugins: Collection<IdeaPluginDescriptor>,
    features: MultiMap<PluginId, UnknownFeature>,
  ): @NotificationContent String {
    val ids = plugins.mapTo(LinkedHashSet()) { it.id } +
              disabledPlugins.map { it.pluginId }

    val addressedFeatures = collectFeaturesByName(ids, features)

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

  protected fun collectFeaturesByName(ids: Set<PluginId>,
                                      features: MultiMap<PluginId, UnknownFeature>): MultiMap<String, String> {
    val result = MultiMap.createSet<String, String>()
    ids
      .flatMap { features[it] }
      .forEach { result.putValue(it.featureDisplayName, it.implementationDisplayName) }
    return result
  }
}
