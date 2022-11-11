// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DEPENDENCY_SUPPORT_FEATURE
import com.intellij.ide.plugins.advertiser.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal class PluginsAdvertiserStartupActivity : ProjectPostStartupActivity {

  suspend fun checkSuggestedPlugins(project: Project, includeIgnored: Boolean) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || application.isHeadlessEnvironment) {
      return
    }

    val customPlugins = loadPluginsFromCustomRepositories()

    coroutineContext.ensureActive()

    val extensionsService = PluginFeatureCacheService.getInstance()
    val oldExtensions = extensionsService.extensions

    val pluginAdvertiserService = PluginAdvertiserService.getInstance(project)
    pluginAdvertiserService.collectDependencyUnknownFeatures(includeIgnored)
    val unknownFeatures = UnknownFeaturesCollector.getInstance(project).unknownFeatures

    if (oldExtensions != null && unknownFeatures.isEmpty()) {
      if (includeIgnored) {
        coroutineContext.ensureActive()

        withContext(Dispatchers.EDT) {
          notificationGroup.createNotification(IdeBundle.message("plugins.advertiser.no.suggested.plugins"), NotificationType.INFORMATION)
            .setDisplayId("advertiser.no.plugins")
            .notify(project)
        }
      }
    }

    try {
      val customPluginIds = customPlugins.map { it.pluginId.idString }.toSet()
      if (oldExtensions == null
          || oldExtensions.isOutdated
          || includeIgnored) {
        @Suppress("DEPRECATION")
        extensionsService.extensions = PluginFeatureMap(
          getFeatureMapFromMarketPlace(customPluginIds, FileTypeFactory.FILE_TYPE_FACTORY_EP.name),
          if (oldExtensions != null) System.currentTimeMillis() else 0L,
        )
        coroutineContext.ensureActive()
        EditorNotifications.getInstance(project).updateAllNotifications()
      }

      val oldDependencies = extensionsService.dependencies
      if (oldDependencies == null
          || oldDependencies.isOutdated
          || includeIgnored) {
        extensionsService.dependencies = PluginFeatureMap(
          getFeatureMapFromMarketPlace(customPluginIds, DEPENDENCY_SUPPORT_FEATURE),
          if (oldDependencies != null) System.currentTimeMillis() else 0L,
        )
      }
      coroutineContext.ensureActive()

      if (unknownFeatures.isNotEmpty()) {
        pluginAdvertiserService.run(
          customPlugins = customPlugins,
          unknownFeatures = unknownFeatures,
          includeIgnored = includeIgnored
        )
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      if (e !is ControlFlowException) {
        LOG.info(e)
      }
    }
  }

  override suspend fun execute(project: Project) {
    checkSuggestedPlugins(project = project, includeIgnored = false)
  }
}

private fun getFeatureMapFromMarketPlace(customPluginIds: Set<String>, featureType: String): Map<String, PluginDataSet> {
  val params = mapOf("featureType" to featureType)
  return MarketplaceRequests.getInstance()
    .getFeatures(params)
    .groupBy(
      { it.implementationName!! },
      { feature -> feature.toPluginData { customPluginIds.contains(it) } }
    ).mapValues {
      PluginDataSet(it.value.filterNotNull().toSet())
    }
}

private val PluginFeatureMap.isOutdated: Boolean
  get() = System.currentTimeMillis() - lastUpdateTime > TimeUnit.DAYS.toMillis(1L)
