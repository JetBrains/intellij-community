// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)

package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DEPENDENCY_SUPPORT_FEATURE
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.advertiser.PluginDataSet
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.advertiser.PluginFeatureMap
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.EditorNotifications
import com.intellij.util.SystemProperties
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private val isTestingMode : Boolean by lazy {
  val application = ApplicationManager.getApplication()
  application.isUnitTestMode || application.isHeadlessEnvironment || SystemProperties.getBooleanProperty("idea.is.playback", false)
}

internal class PluginsAdvertiserStartupActivity : ProjectActivity {

  suspend fun checkSuggestedPlugins(project: Project, includeIgnored: Boolean) {
    if (isTestingMode) {
      return
    }

    val customPlugins = computeDetached { RepositoryHelper.loadPluginsFromCustomRepositories(null, PluginNodeModelBuilderFactory) }

    coroutineContext.ensureActive()

    val extensionService = PluginFeatureCacheService.getInstance()
    val oldExtensions = extensionService.extensions.get()

    val pluginAdvertiserService = PluginAdvertiserService.getInstance(project)
    pluginAdvertiserService.collectDependencyUnknownFeatures(includeIgnored)
    val unknownFeatures = UnknownFeaturesCollector.getInstance(project).unknownFeatures

    if (oldExtensions != null && unknownFeatures.isEmpty()) {
      if (includeIgnored) {
        coroutineContext.ensureActive()

        withContext(Dispatchers.EDT) {
          getPluginSuggestionNotificationGroup()
            .createNotification(IdeBundle.message("plugins.advertiser.no.suggested.plugins"), NotificationType.INFORMATION)
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
        extensionService.extensions.set(PluginFeatureMap(
          featureMap = getFeatureMapFromMarketPlace(customPluginIds = customPluginIds, featureType = "com.intellij.fileTypeFactory"),
          lastUpdateTime = if (oldExtensions != null) System.currentTimeMillis() else 0L,
        ))
        coroutineContext.ensureActive()
        EditorNotifications.getInstance(project).updateAllNotifications()
      }

      val oldDependencies = extensionService.dependencies.get()
      if (oldDependencies == null
          || oldDependencies.isOutdated
          || includeIgnored) {
        extensionService.dependencies.set(PluginFeatureMap(
          featureMap = getFeatureMapFromMarketPlace(customPluginIds = customPluginIds, featureType = DEPENDENCY_SUPPORT_FEATURE),
          lastUpdateTime = if (oldDependencies != null) System.currentTimeMillis() else 0L,
        ))
      }

      // update information about file handlers when forced
      if (includeIgnored && PluginAdvertiserExtensionsStateService.getInstance().updateCompatibleFileHandlers()) {
        EditorNotifications.getInstance(project).updateAllNotifications()
      }

      coroutineContext.ensureActive()

      if (unknownFeatures.isNotEmpty()) {
        pluginAdvertiserService.run(
          customPlugins = customPlugins,
          unknownFeatures = unknownFeatures,
          includeIgnored = includeIgnored
        )
      }

      notifyUnbundledPlugins(project)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      if (e !is ControlFlowException) {
        thisLogger().info(e)
      }
    }
  }

  override suspend fun execute(project: Project) {
    if (!Registry.`is`("ide.show.plugin.suggestions.on.open", true)) {
      return
    }

    checkSuggestedPlugins(project = project, includeIgnored = false)
  }
}

internal fun findSuggestedPlugins(project: Project, customRepositories: Map<String, List<PluginUiModel>>): List<PluginUiModel> {
  return runBlockingMaybeCancellable {
    if (isTestingMode) {
      return@runBlockingMaybeCancellable emptyList()
    }

    val customPlugins = ArrayList<PluginUiModel>()
    for (value in customRepositories.values) {
      customPlugins.addAll(value)
    }

    val pluginAdvertiserService = project.serviceAsync<PluginAdvertiserService>()
    pluginAdvertiserService.collectDependencyUnknownFeatures(true)

    val customPluginIds = customPlugins.map { it.pluginId.idString }.toSet()
    val extensionService = serviceAsync<PluginFeatureCacheService>()
    val oldDependencies = extensionService.dependencies.get()
    extensionService.dependencies.set(PluginFeatureMap(
      getFeatureMapFromMarketPlace(customPluginIds, DEPENDENCY_SUPPORT_FEATURE),
      if (oldDependencies != null) System.currentTimeMillis() else 0L,
    ))
    val unknownFeatures = UnknownFeaturesCollector.getInstance(project).unknownFeatures
    if (unknownFeatures.isNotEmpty()) {
      return@runBlockingMaybeCancellable pluginAdvertiserService.fetch(customPlugins, unknownFeatures, true)
    }

    return@runBlockingMaybeCancellable emptyList()
  }
}

private suspend fun getFeatureMapFromMarketPlace(customPluginIds: Set<String>, featureType: String): Map<String, PluginDataSet> {
  if (isTestingMode) {
    return emptyMap()
  }

  val params = mapOf("featureType" to featureType)
  val features = MarketplaceRequests.getInstance().getFeatures(params)
  return features
    .groupBy(
      { it.implementationName!! },
      { feature -> feature.toPluginData { customPluginIds.contains(it) } }
    ).mapValues {
      PluginDataSet(it.value.filterNotNull().toSet())
    }
}

private fun notifyUnbundledPlugins(@Suppress("unused") project: Project) {
  // stub for future plugins
}

private val PluginFeatureMap.isOutdated: Boolean
  get() = System.currentTimeMillis() - lastUpdateTime > TimeUnit.DAYS.toMillis(1L)
