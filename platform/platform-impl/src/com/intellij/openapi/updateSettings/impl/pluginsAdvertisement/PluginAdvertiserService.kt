// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.UpdateChecker.mergePluginsFromRepositories
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorNotifications
import com.intellij.util.containers.ContainerUtil
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
    ids.forEach { (pluginId, plugin) ->
      if (PluginManagerCore.isDisabled(pluginId)) {
        val pluginDescriptor = PluginManagerCore.getPlugin(pluginId) ?: return@forEach
        disabledPlugins[plugin] = pluginDescriptor
      }
    }

    val bundledPlugin = PluginsAdvertiser.hasBundledPluginToInstall(ids.values)
    val plugins = mutableSetOf<PluginDownloader>()

    if (ids.isNotEmpty()) {
      val marketplacePlugins =
        MarketplaceRequests.getInstance().loadLastCompatiblePluginDescriptors(ContainerUtil.map(ids.keys) { pluginId -> pluginId.idString })

      val compatibleUpdates = mergePluginsFromRepositories(marketplacePlugins, customPlugins, true)
      compatibleUpdates.forEach { loadedPlugin ->
        val existingPlugin = PluginManagerCore.getPlugin(loadedPlugin.pluginId)
        if (existingPlugin != null &&
            PluginDownloader.compareVersionsSkipBrokenAndIncompatible(loadedPlugin.version, existingPlugin) <= 0
        ) {
          return@forEach
        }

        val pluginId = loadedPlugin.pluginId
        if (ids.containsKey(pluginId) &&
            !PluginManagerCore.isDisabled(pluginId) &&
            !PluginManagerCore.isBrokenPlugin(loadedPlugin)
        ) {
          plugins.add(PluginDownloader.createDownloader(loadedPlugin))
        }
      }
    }

    ApplicationManager.getApplication().invokeLater({
                                                      if (project.isDisposed)
                                                        return@invokeLater

                                                      val notificationActions = mutableListOf<NotificationAction>()
                                                      var message: String? = null

                                                      if (plugins.isNotEmpty() || disabledPlugins.isNotEmpty()) {
                                                        message = getAddressedMessagePresentation(plugins, disabledPlugins, features)
                                                        if (disabledPlugins.isNotEmpty()) {
                                                          val disabledDescriptors = disabledPlugins.values
                                                          val title = if (disabledPlugins.size == 1)
                                                            IdeBundle.message(
                                                              "plugins.advertiser.action.enable.plugin",
                                                              disabledDescriptors.single().name
                                                            )
                                                          else
                                                            IdeBundle.message("plugins.advertiser.action.enable.plugins")

                                                          val action = NotificationAction.createSimpleExpiring(title) {
                                                            val data = FeatureUsageData()
                                                              .addData("source", "notification")
                                                              .addData("plugins", disabledDescriptors.map { it.pluginId.idString })
                                                            FUCounterUsageLogger.getInstance().logEvent(
                                                              PluginsAdvertiser.FUS_GROUP_ID,
                                                              "enable.plugins",
                                                              data,
                                                            )
                                                            PluginManagerConfigurable.showPluginConfigurableAndEnable(
                                                              project,
                                                              disabledDescriptors.toSet(),
                                                            )
                                                          }
                                                          notificationActions.add(action)
                                                        }
                                                        else {
                                                          notificationActions.add(
                                                            NotificationAction.createSimpleExpiring(
                                                              IdeBundle.message("plugins.advertiser.action.configure.plugins")
                                                            ) {
                                                              val data = FeatureUsageData().addData("source", "notification")
                                                              FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID,
                                                                                                          "configure.plugins", data)
                                                              PluginsAdvertiserDialog(project, plugins.toTypedArray(), customPlugins).show()
                                                            }
                                                          )
                                                        }
                                                        notificationActions.add(
                                                          NotificationAction.createSimpleExpiring(
                                                            IdeBundle.message("plugins.advertiser.action.ignore.unknown.features")
                                                          ) {
                                                            val data = FeatureUsageData().addData("source", "notification")
                                                            FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID,
                                                                                                        "ignore.unknown.features", data)
                                                            val featuresCollector = UnknownFeaturesCollector.getInstance(project)
                                                            for (feature in unknownFeatures) {
                                                              featuresCollector.ignoreFeature(feature)
                                                            }
                                                          }
                                                        )
                                                      }
                                                      else if (bundledPlugin.isNotEmpty()
                                                               && !PropertiesComponent.getInstance().isTrueValue(
                                                          PluginsAdvertiser.IGNORE_ULTIMATE_EDITION)) {
                                                        message = IdeBundle.message(
                                                          "plugins.advertiser.ultimate.features.detected",
                                                          bundledPlugin.joinToString()
                                                        )

                                                        notificationActions.add(
                                                          NotificationAction.createSimpleExpiring(
                                                            IdeBundle.message("plugins.advertiser.action.try.ultimate")
                                                          ) {
                                                            val data = FeatureUsageData().addData("source", "notification")
                                                            FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID,
                                                                                                        "open.download.page", data)
                                                            PluginsAdvertiser.openDownloadPage()
                                                          }
                                                        )
                                                        notificationActions.add(
                                                          NotificationAction.createSimpleExpiring(
                                                            IdeBundle.message("plugins.advertiser.action.ignore.ultimate")
                                                          ) {
                                                            val data = FeatureUsageData().addData("source", "notification")
                                                            FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID,
                                                                                                        "ignore.ultimate", data)
                                                            PropertiesComponent.getInstance().setValue(
                                                              PluginsAdvertiser.IGNORE_ULTIMATE_EDITION, "true")
                                                          }
                                                        )
                                                      }

                                                      val notificationMessage = message ?: return@invokeLater
                                                      val notification = PluginsAdvertiser.getNotificationGroup()
                                                        .createNotification("", notificationMessage, NotificationType.INFORMATION, null)

                                                      notificationActions.forEach { action -> notification.addAction(action) }
                                                      notification.notify(project)

                                                    }, ModalityState.NON_MODAL)
  }

  open fun getAddressedMessagePresentation(
    plugins: Set<PluginDownloader>,
    disabledPlugins: Map<PluginsAdvertiser.Plugin, IdeaPluginDescriptor?>,
    features: MultiMap<PluginId?, UnknownFeature>
  ): @NotificationContent String {

    val ids: MutableSet<PluginId> = LinkedHashSet()
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

    val addressedFeaturesNumber = addressedFeatures.size()
    val pluginsNumber = ids.size
    val repoPluginsNumber = plugins.size

    return if (addressedFeaturesNumber == 1) {
      val feature = addressedFeatures.entrySet().iterator().next()
      val name = feature.key
      val text = StringUtil.join(feature.value, ", ")
      IdeBundle.message("plugins.advertiser.missing.feature", pluginsNumber, name, text, repoPluginsNumber)
    }
    else {
      val text = StringUtil.join(
        addressedFeatures.entrySet(),
        { entry -> entry.key + ": " + StringUtil.join(entry.value, ", ") }, "; "
      )
      IdeBundle.message("plugins.advertiser.missing.features", pluginsNumber, text, repoPluginsNumber)
    }
  }
}
