// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.org.PluginManagerFilters
import com.intellij.ide.ui.PluginBooleanOptionDescriptor
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.util.containers.MultiMap

open class PluginAdvertiserService {

  companion object {
    @JvmStatic
    val instance
      get() = service<PluginAdvertiserService>()
  }

  open fun run(
    project: Project,
    customPlugins: List<PluginNode>,
    unknownFeatures: Collection<UnknownFeature>,
  ) {
    val features = MultiMap.createSet<PluginId, UnknownFeature>()
    val disabledPlugins = HashMap<PluginData, IdeaPluginDescriptor>()

    val ids = mutableMapOf<PluginId, PluginData>()
    val dependencies = PluginFeatureCacheService.instance.dependencies
    unknownFeatures.forEach { feature ->
      ProgressManager.checkCanceled()
      val featureType = feature.featureType
      val implementationName = feature.implementationName
      val featurePluginData = PluginFeatureService.instance.getPluginForFeature(featureType, implementationName)

      val installedPluginData = featurePluginData?.pluginData

      fun putFeature(data: PluginData) {
        val id = data.pluginId
        ids[id] = data
        features.putValue(id, featurePluginData?.displayName?.let { feature.withImplementationDisplayName(it) } ?: feature)
      }

      if (installedPluginData != null) {
        putFeature(installedPluginData)
      }
      else if (featureType == DEPENDENCY_SUPPORT_FEATURE && dependencies != null) {
        dependencies[implementationName].forEach { putFeature(it) }
      }
      else {
        MarketplaceRequests.Instance
          .getFeatures(featureType, implementationName)
          .mapNotNull { it.toPluginData() }
          .forEach { putFeature(it) }
      }
    }

    val org = PluginManagerFilters.getInstance()

    //include disabled plugins
    ids.filter { (pluginId, _) ->
      PluginManagerCore.isDisabled(pluginId)
    }.mapNotNull { (pluginId, plugin) ->
      PluginManagerCore.getPlugin(pluginId)?.let {
        plugin to it
      }
    }.filter {
      org.allowInstallingPlugin(it.second)
    }.forEach { (plugin, pluginDescriptor) ->
      disabledPlugins[plugin] = pluginDescriptor
    }

    val bundledPlugin = getBundledPluginToInstall(ids.values)
    val plugins = if (ids.isEmpty())
      emptyList()
    else
      RepositoryHelper.mergePluginsFromRepositories(
        MarketplaceRequests.loadLastCompatiblePluginDescriptors(ids.keys),
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
      }.filter {
        org.allowInstallingPlugin(it)
      }.map { PluginDownloader.createDownloader(it) }

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

            PluginBooleanOptionDescriptor.togglePluginState(true, disabledDescriptors.toSet())
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
        ) to listOf(action, createIgnoreUnknownFeaturesNotification(project, plugins, disabledPlugins.values, unknownFeatures))
      }
      else if (bundledPlugin.isNotEmpty()
               && !isIgnoreIdeSuggestion) {
        IdeBundle.message(
          "plugins.advertiser.ultimate.features.detected",
          bundledPlugin.joinToString()
        ) to listOf(
          NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.try.ultimate", PluginAdvertiserEditorNotificationProvider.ideaUltimate.name)) {
            FUSEventSource.NOTIFICATION.openDownloadPageAndLog(project, PluginAdvertiserEditorNotificationProvider.ideaUltimate.downloadUrl)
          },
          NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
            FUSEventSource.NOTIFICATION.doIgnoreUltimateAndLog(project)
          },
        )
      }
      else {
        return@invokeLater
      }

      notificationGroup.createNotification(notificationMessage, NotificationType.INFORMATION)
        .addActions(notificationActions as Collection<AnAction>)
        .notify(project)
    }
  }

  private fun createIgnoreUnknownFeaturesNotification(project: Project,
                                                      plugins: Collection<PluginDownloader>,
                                                      disabledPlugins: Collection<IdeaPluginDescriptor>,
                                                      unknownFeatures: Collection<UnknownFeature>): NotificationAction {
    val ids = plugins.mapTo(LinkedHashSet()) { it.id } +
              disabledPlugins.map { it.pluginId }

    val message = IdeBundle.message("plugins.advertiser.action.ignore.unknown.features", ids.size)

    return NotificationAction.createSimpleExpiring(message) {
      FUSEventSource.NOTIFICATION.logIgnoreUnknownFeatures(project)

      val collector = UnknownFeaturesCollector.getInstance(project)
      unknownFeatures.forEach { collector.ignoreFeature(it) }
    }
  }

  open fun getAddressedMessagePresentation(
    plugins: Collection<PluginDownloader>,
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

  fun rescanDependencies(project: Project) {
    val dependencyUnknownFeatures = collectDependencyUnknownFeatures(project)
    if (dependencyUnknownFeatures.isNotEmpty()) {
      instance.run(
        project,
        loadPluginsFromCustomRepositories(),
        dependencyUnknownFeatures,
      )
    }
  }
}
