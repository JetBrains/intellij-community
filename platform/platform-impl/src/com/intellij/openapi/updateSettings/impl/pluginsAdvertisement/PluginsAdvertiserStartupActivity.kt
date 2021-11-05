// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DEPENDENCY_SUPPORT_FEATURE
import com.intellij.ide.plugins.DependencyCollectorBean
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.advertiser.PluginFeatureMap
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal class PluginsAdvertiserStartupActivity : StartupActivity.Background {

  @RequiresBackgroundThread
  fun checkSuggestedPlugins(project: Project, includeIgnored: Boolean) {
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
    unknownFeatures.addAll(collectDependencyUnknownFeatures(project, includeIgnored))

    if (extensions != null && unknownFeatures.isEmpty()) {
      if (includeIgnored) {
        ProgressManager.checkCanceled()
        ApplicationManager.getApplication().invokeLater(Runnable {
          notificationGroup.createNotification(IdeBundle.message("plugins.advertiser.no.suggested.plugins"), NotificationType.INFORMATION)
            .notify(project)
        }, ModalityState.NON_MODAL, project.disposed)
      }
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
      ProgressManager.checkCanceled()
      PluginAdvertiserService.instance.run(
        project,
        customPlugins,
        unknownFeatures,
        includeIgnored
      )
    }
    catch (e: Exception) {
      if (e !is ControlFlowException) {
        LOG.info(e)
      }
    }
  }

  @RequiresBackgroundThread
  override fun runActivity(project: Project) {
    checkSuggestedPlugins(project, false)
  }

  companion object {
    @JvmStatic
    private fun getFeatureMapFromMarketPlace(customPluginIds: Set<String>, featureType: String): Map<String, Set<PluginData>> {
      val params = mapOf("featureType" to featureType)
      return MarketplaceRequests.getInstance()
        .getFeatures(params)
        .groupBy(
          { it.implementationName!! },
          { feature -> feature.toPluginData { customPluginIds.contains(it) } }
        ).mapValues { it.value.filterNotNull().toSet() }
    }
  }
}

internal fun collectDependencyUnknownFeatures(project: Project, includeIgnored: Boolean = false): Sequence<UnknownFeature> {
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
