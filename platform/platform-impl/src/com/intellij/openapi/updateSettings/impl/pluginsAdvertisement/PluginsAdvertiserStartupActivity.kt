// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.advertiser.KnownExtensions
import com.intellij.ide.plugins.advertiser.KnownExtensionsService
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.ui.EditorNotifications

internal class PluginsAdvertiserStartupActivity : StartupActivity.Background {

  override fun runActivity(project: Project) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode ||
        application.isHeadlessEnvironment ||
        !UpdateSettings.getInstance().isPluginsCheckNeeded) {
      return
    }

    val customPlugins = loadPluginsFromCustomRepositories()
    if (project.isDisposed) {
      return
    }

    val extensionsService = KnownExtensionsService.instance
    val extensions = extensionsService.extensions
    val unknownFeatures = UnknownFeaturesCollector.getInstance(project).unknownFeatures
    if (extensions != null && unknownFeatures.isEmpty()) {
      return
    }

    try {
      if (extensions == null) {
        val extensionsMap = getExtensionsFromMarketPlace(customPlugins)
        extensionsService.extensions = KnownExtensions(extensionsMap)
        if (project.isDisposed) {
          return
        }
        EditorNotifications.getInstance(project).updateAllNotifications()
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

    private fun getExtensionsFromMarketPlace(customPlugins: List<IdeaPluginDescriptor>): Map<String, Set<PluginData>> {
      val customPluginIds = customPlugins.map { it.pluginId.idString }.toSet()

      @Suppress("DEPRECATION") val params = mapOf("featureType" to FileTypeFactory.FILE_TYPE_FACTORY_EP.name)
      return MarketplaceRequests.Instance
        .getFeatures(params)
        .groupBy(
          { it.implementationName!! },
          { feature -> feature.toPluginData { customPluginIds.contains(it) } }
        ).mapValues { it.value.filterNotNull().toSet() }
    }
  }
}