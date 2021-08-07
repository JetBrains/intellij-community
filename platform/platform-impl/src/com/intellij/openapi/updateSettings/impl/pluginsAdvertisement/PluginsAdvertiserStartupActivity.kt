// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DEPENDENCY_SUPPORT_FEATURE
import com.intellij.ide.plugins.DependencyCollectorBean
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.advertiser.PluginFeatureMap
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.ui.EditorNotifications

internal class PluginsAdvertiserStartupActivity : StartupActivity.Background {

  override fun runActivity(project: Project) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode ||
        application.isHeadlessEnvironment) {
      return
    }

    val customPlugins = loadPluginsFromCustomRepositories()
    if (project.isDisposed) {
      return
    }

    val extensionsService = PluginFeatureCacheService.instance
    val extensions = extensionsService.extensions

    val unknownFeatures = UnknownFeaturesCollector.getInstance(project).unknownFeatures.toMutableList()
    unknownFeatures.addAll(collectDependencyUnknownFeatures(project))

    if (extensions != null && unknownFeatures.isEmpty()) {
      return
    }

    try {
      if (extensions == null || extensions.outdated) {
        @Suppress("DEPRECATION")
        val extensionsMap = getFeatureMapFromMarketPlace(customPlugins.map { it.pluginId.idString }.toSet(),
                                                         FileTypeFactory.FILE_TYPE_FACTORY_EP.name)
        extensionsService.extensions?.update(extensionsMap) ?: run {
          extensionsService.extensions = PluginFeatureMap(extensionsMap)
        }
        if (project.isDisposed) {
          return
        }
        EditorNotifications.getInstance(project).updateAllNotifications()
      }

      if (extensionsService.dependencies == null || extensionsService.dependencies!!.outdated) {
        val dependencyMap = getFeatureMapFromMarketPlace(customPlugins.map { it.pluginId.idString }.toSet(), DEPENDENCY_SUPPORT_FEATURE)
        extensionsService.dependencies?.update(dependencyMap) ?: run {
          extensionsService.dependencies = PluginFeatureMap(dependencyMap)
        }
      }
      PluginAdvertiserService.instance.run(
        project,
        customPlugins,
        unknownFeatures,
      )
    }
    catch (e: Exception) {
      LOG.info(e)
    }
  }

  companion object {

    @JvmStatic
    private fun getFeatureMapFromMarketPlace(customPluginIds: Set<String>, featureType: String): Map<String, Set<PluginData>> {
      val params = mapOf("featureType" to featureType)
      return MarketplaceRequests.Instance
        .getFeatures(params)
        .groupBy(
          { it.implementationName!! },
          { feature -> feature.toPluginData { customPluginIds.contains(it) } }
        ).mapValues { it.value.filterNotNull().toSet() }
    }
  }
}

fun collectDependencyUnknownFeatures(project: Project): List<UnknownFeature> {
  return DependencyCollectorBean.EP_NAME.extensions.flatMap { dependencyCollectorBean ->
    dependencyCollectorBean.instance.collectDependencies(project).map { coordinate ->
      UnknownFeature(DEPENDENCY_SUPPORT_FEATURE,
                     IdeBundle.message("plugins.advertiser.feature.dependency"),
                     dependencyCollectorBean.kind + ":" + coordinate, null)
    }
  }.filterNot { UnknownFeaturesCollector.getInstance(project).isIgnored(it) }
}
