// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy

import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Inspired by [com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.InstallPluginTask]
 */
@ApiStatus.Internal
object InstallJBAcademyTask {
  val JB_ACADEMY_PLUGIN_ID: PluginId = PluginId.getId("com.jetbrains.edu")
  
  suspend fun install(): Unit = reportSequentialProgress { reporter ->
    val descriptors = reporter.nextStep(endFraction = 20) {
      val marketplacePlugins = MarketplaceRequests.loadLastCompatiblePluginDescriptors(setOf(JB_ACADEMY_PLUGIN_ID))
      val customPlugins = coroutineToIndicator {
        val indicator = ProgressManager.getGlobalProgressIndicator()!!
        RepositoryHelper.loadPluginsFromCustomRepositories(indicator)
      }
      val descriptors: MutableList<IdeaPluginDescriptor> =
        RepositoryHelper.mergePluginsFromRepositories(marketplacePlugins, customPlugins, true).toMutableList()
      PluginManagerCore.plugins.filterTo(descriptors) {
        !it.isEnabled && PluginManagerCore.isCompatible(it) && PluginManagementPolicy.getInstance().canInstallPlugin(it)
      }
      checkCanceled()
      descriptors
    }

    val plugins: List<PluginNode> = reporter.nextStep(endFraction = 40) {
      val downloader = PluginDownloader.createDownloader(descriptors.first())
      val nodes = mutableListOf<PluginNode>()
      val plugin = downloader.descriptor
      if (plugin.isEnabled) {
        nodes.add(downloader.toPluginNode())
      }
      PluginEnabler.HEADLESS.enable(listOf(plugin))
      checkCanceled()
      nodes
    }

    if (plugins.isEmpty()) return

    val operation = reporter.nextStep(endFraction = 80) {
      coroutineToIndicator {
        val indicator = ProgressManager.getGlobalProgressIndicator()!!
        val operation = PluginInstallOperation(plugins, emptyList(), PluginEnabler.HEADLESS, indicator)
        indicator.checkCanceled()
        operation.setAllowInstallWithoutRestart(true)
        operation.run()
        operation
      }
    }

    if (!operation.isSuccess) return

    reporter.nextStep(endFraction = 100) {
      withContext(Dispatchers.EDT) {
        for ((file, pluginDescriptor) in operation.pendingDynamicPluginInstalls) {
          checkCanceled()
          PluginInstaller.installAndLoadDynamicPlugin(file, pluginDescriptor)
        }
      }
    }
  }
}