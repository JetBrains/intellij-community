// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.advertiser.PluginFeatureMap
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.org.PluginManagerFilters
import com.intellij.ide.ui.PluginBooleanOptionDescriptor
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

open class PluginAdvertiserService {

  private val notificationManager: SingletonNotificationManager =
    SingletonNotificationManager(notificationGroup.displayId, NotificationType.INFORMATION)

  companion object {
    @JvmStatic
    fun getInstance(): PluginAdvertiserService = service()
  }

  open fun run(
    project: Project,
    customPlugins: List<PluginNode>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean = false
  ) {
    val featuresMap = MultiMap.createSet<PluginId, UnknownFeature>()
    val disabledPlugins = HashMap<PluginData, IdeaPluginDescriptor>()

    val ids = mutableMapOf<PluginId, PluginData>()
    val dependencies = PluginFeatureCacheService.getInstance().dependencies

    val ignoredPluginSuggestionState = GlobalIgnoredPluginSuggestionState.getInstance()
    for (feature in unknownFeatures) {
      ProgressManager.checkCanceled()
      val featureType = feature.featureType
      val implementationName = feature.implementationName
      val featurePluginData = PluginFeatureService.instance.getPluginForFeature(featureType, implementationName)

      val installedPluginData = featurePluginData?.pluginData

      fun putFeature(data: PluginData) {
        if (ignoredPluginSuggestionState.isIgnored(data.pluginId) && !includeIgnored) { // globally ignored
          LOG.info("Plugin is ignored by user, suggestion will not be shown: " + data.pluginId.idString)
          return
        }

        val id = data.pluginId
        ids[id] = data
        featuresMap.putValue(id, featurePluginData?.displayName?.let { feature.withImplementationDisplayName(it) } ?: feature)
      }

      if (installedPluginData != null) {
        putFeature(installedPluginData)
      }
      else if (featureType == DEPENDENCY_SUPPORT_FEATURE && dependencies != null) {
        dependencies.get(implementationName).forEach(::putFeature)
      }
      else {
        MarketplaceRequests.getInstance()
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

    val bundledPlugins = getBundledPluginToInstall(ids.values)
    val suggestToInstall = if (ids.isEmpty())
      emptyList()
    else
      fetchPluginSuggestions(ids, customPlugins, org)

    invokeLater(ModalityState.NON_MODAL) {
      if (project.isDisposed)
        return@invokeLater

      notifyUser(project, bundledPlugins, suggestToInstall, disabledPlugins, customPlugins,
                 featuresMap, unknownFeatures, dependencies, includeIgnored)
    }
  }

  private fun fetchPluginSuggestions(ids: MutableMap<PluginId, PluginData>,
                                     customPlugins: List<PluginNode>,
                                     org: PluginManagerFilters): List<PluginDownloader> {
    return RepositoryHelper.mergePluginsFromRepositories(
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
  }

  private fun notifyUser(project: Project,
                         bundledPlugins: List<String>,
                         suggestionPlugins: List<PluginDownloader>,
                         disabledPlugins: Map<PluginData, IdeaPluginDescriptor>,
                         customPlugins: List<PluginNode>,
                         featuresMap: MultiMap<PluginId, UnknownFeature>,
                         allUnknownFeatures: Collection<UnknownFeature>,
                         dependencies: PluginFeatureMap?,
                         includeIgnored: Boolean) {
    val (notificationMessage, notificationActions) = if (suggestionPlugins.isNotEmpty() ||
                                                         disabledPlugins.isNotEmpty()) {
      val action = if (disabledPlugins.isNotEmpty()) {
        val disabledDescriptors = disabledPlugins.values
        val title = if (disabledPlugins.size == 1)
          IdeBundle.message("plugins.advertiser.action.enable.plugin", disabledDescriptors.single().name)
        else
          IdeBundle.message("plugins.advertiser.action.enable.plugins")

        NotificationAction.createSimpleExpiring(title) {
          FUSEventSource.NOTIFICATION.logEnablePlugins(
            disabledDescriptors.map { it.pluginId.idString },
            project,
          )

          ApplicationManager.getApplication().invokeLater {
            PluginBooleanOptionDescriptor.togglePluginState(disabledDescriptors, true)
          }
        }
      }
      else {
        NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.configure.plugins")) {
          FUSEventSource.NOTIFICATION.logConfigurePlugins(project)
          PluginsAdvertiserDialog(project, suggestionPlugins, customPlugins).show()
        }
      }

      val notificationActions = listOf(
        action,
        createIgnoreUnknownFeaturesAction(project, suggestionPlugins, disabledPlugins.values, allUnknownFeatures, dependencies)
      )
      val messagePresentation = getAddressedMessagePresentation(suggestionPlugins, disabledPlugins.values, featuresMap)

      Pair(messagePresentation, notificationActions)
    }
    else if (bundledPlugins.isNotEmpty()
             && !isIgnoreIdeSuggestion) {
      IdeBundle.message(
        "plugins.advertiser.ultimate.features.detected",
        bundledPlugins.joinToString()
      ) to listOf(
        NotificationAction.createSimpleExpiring(
          IdeBundle.message("plugins.advertiser.action.try.ultimate", PluginAdvertiserEditorNotificationProvider.ideaUltimate.name)) {
          FUSEventSource.NOTIFICATION.openDownloadPageAndLog(project, PluginAdvertiserEditorNotificationProvider.ideaUltimate.downloadUrl)
        },
        NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
          FUSEventSource.NOTIFICATION.doIgnoreUltimateAndLog(project)
        },
      )
    }
    else {
      if (includeIgnored) {
        notificationGroup.createNotification(IdeBundle.message("plugins.advertiser.no.suggested.plugins"), NotificationType.INFORMATION)
          .setDisplayId("advertiser.no.plugins")
          .notify(project)
      }
      return
    }

    ProgressManager.checkCanceled()

    notificationManager.notify("", notificationMessage, project, Consumer {
      it.setSuggestionType(true)
        .addActions(notificationActions as Collection<AnAction>)
    })
  }

  private fun createIgnoreUnknownFeaturesAction(project: Project,
                                                plugins: Collection<PluginDownloader>,
                                                disabledPlugins: Collection<IdeaPluginDescriptor>,
                                                unknownFeatures: Collection<UnknownFeature>,
                                                dependencyPlugins: PluginFeatureMap?): NotificationAction {
    val ids = plugins.mapTo(LinkedHashSet()) { it.id } +
              disabledPlugins.map { it.pluginId }

    val message = IdeBundle.message("plugins.advertiser.action.ignore.unknown.features", ids.size)

    return NotificationAction.createSimpleExpiring(message) {
      FUSEventSource.NOTIFICATION.logIgnoreUnknownFeatures(project)

      val collector = UnknownFeaturesCollector.getInstance(project)
      for (unknownFeature in unknownFeatures) {
        if (unknownFeature.featureType != DEPENDENCY_SUPPORT_FEATURE
            || dependencyPlugins?.get(unknownFeature.implementationName)?.isNotEmpty() == true) {
          collector.ignoreFeature(unknownFeature)
        }
      }

      val globalIgnoredState = GlobalIgnoredPluginSuggestionState.getInstance()
      for (pluginIdToIgnore in ids) {
        globalIgnoredState.ignoreFeature(pluginIdToIgnore)
      }
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

      if (feature.key != "dependency") {
        IdeBundle.message(
          "plugins.advertiser.missing.feature",
          pluginsNumber,
          feature.key,
          feature.value.joinToString(),
          repoPluginsNumber
        )
      }
      else {
        if (feature.value.size <= 1) {
          IdeBundle.message(
            "plugins.advertiser.missing.feature.dependency",
            pluginsNumber,
            feature.value.joinToString()
          )
        }
        else {
          IdeBundle.message(
            "plugins.advertiser.missing.features.dependency",
            pluginsNumber,
            feature.value.joinToString()
          )
        }
      }
    }
    else {
      if (entries.all { it.key == "dependency" }) {
        IdeBundle.message(
          "plugins.advertiser.missing.features.dependency",
          pluginsNumber,
          entries.joinToString(separator = "; ") { it.value.joinToString(prefix = it.key + ": ") }
        )
      }
      else {
        IdeBundle.message(
          "plugins.advertiser.missing.features",
          pluginsNumber,
          entries.joinToString(separator = "; ") { it.value.joinToString(prefix = it.key + ": ") },
          repoPluginsNumber
        )
      }
    }
  }

  @ApiStatus.Internal
  open fun collectDependencyUnknownFeatures(project: Project, includeIgnored: Boolean = false): Sequence<UnknownFeature> {
    return DependencyCollectorBean.EP_NAME.extensions.asSequence()
      .flatMap { dependencyCollectorBean ->
        dependencyCollectorBean.instance.collectDependencies(project).map { coordinate ->
          UnknownFeature(DEPENDENCY_SUPPORT_FEATURE,
                         IdeBundle.message("plugins.advertiser.feature.dependency"),
                         dependencyCollectorBean.kind + ":" + coordinate, null)
        }
      }
      .filter { includeIgnored || !UnknownFeaturesCollector.getInstance(project).isIgnored(it) }
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
    val dependencyUnknownFeatures = collectDependencyUnknownFeatures(project).toList()
    if (dependencyUnknownFeatures.isNotEmpty()) {
      getInstance().run(
        project,
        loadPluginsFromCustomRepositories(),
        dependencyUnknownFeatures,
      )
    }
  }
}
