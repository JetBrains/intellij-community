// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests.Companion.loadLastCompatiblePluginDescriptors
import com.intellij.ide.plugins.org.PluginManagerFilters
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.updateSettings.impl.PluginDownloader

internal class InstallPluginTask(private val pluginIds: Set<PluginId>, val modalityState: ModalityState) :
  Task.Modal(null, IdeBundle.message("plugins.advertiser.task.searching.for.plugins"), true) {
  private val plugins = mutableSetOf<PluginDownloader>()
  private lateinit var customPlugins: List<PluginNode>

  override fun run(indicator: ProgressIndicator) {
    val marketplacePlugins = loadLastCompatiblePluginDescriptors(pluginIds)
    customPlugins = loadPluginsFromCustomRepositories(indicator)
    val descriptors: MutableList<IdeaPluginDescriptor> = ArrayList(RepositoryHelper.mergePluginsFromRepositories(marketplacePlugins,
                                                                                                                 customPlugins, true))
    PluginManagerCore.getPlugins().filterTo(descriptors) {
      !it.isEnabled && PluginManagerCore.isCompatible(it) && PluginManagerFilters.getInstance().allowInstallingPlugin(it)
    }
    descriptors
      .filter { pluginIds.contains(it.pluginId) }
      .mapTo(plugins) { PluginDownloader.createDownloader(it) }
  }

  override fun onSuccess() {
    val pluginsToEnable = mutableListOf<IdeaPluginDescriptor>()
    val nodes = mutableListOf<PluginNode>()
    for (downloader in plugins) {
      val plugin = downloader.descriptor
      pluginsToEnable.add(plugin)
      if (plugin.isEnabled) {
        nodes.add(downloader.toPluginNode())
      }
    }
    PluginManagerMain.suggestToEnableInstalledDependantPlugins(PluginEnabler.HEADLESS, nodes)
    PluginEnabler.HEADLESS.enable(pluginsToEnable)
    if (nodes.isNotEmpty()) {
      downloadPlugins(nodes, customPlugins, modalityState)
    }
  }

  companion object {
    private fun downloadPlugins(plugins: List<PluginNode>, customPlugins: Collection<PluginNode>, modalityState: ModalityState) {
      ProgressManager.getInstance().run(object : Modal(null, IdeBundle.message("progress.download.plugins"), true) {
        override fun run(indicator: ProgressIndicator) {
          val operation = PluginInstallOperation(plugins, customPlugins, PluginEnabler.HEADLESS, indicator)
          operation.setAllowInstallWithoutRestart(true)
          operation.run()
          if (operation.isSuccess) {
            invokeLater(modalityState) {
              for ((file, pluginDescriptor) in operation.pendingDynamicPluginInstalls) {
                PluginInstaller.installAndLoadDynamicPlugin(file, pluginDescriptor)
              }
            }
          }
        }
      })
    }
  }
}