// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests.Companion.loadLastCompatiblePluginDescriptors
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import java.awt.Component
import java.io.IOException

class InstallAndEnablePluginTask(val pluginId: PluginId) :
  Task.WithResult<PluginDownloader?, IOException>(null, IdeBundle.message("new.project.wizard.download.plugin"), true) {
  override fun compute(indicator: ProgressIndicator): PluginDownloader? {
    val pluginNode = loadLastCompatiblePluginDescriptors(setOf(pluginId))
      .ifEmpty {
        LOG.error("Cannot find compatible plugin: " + pluginId.idString)
        invokeLater {
          Messages.showWarningDialog(IdeBundle.message("new.project.wizard.cannot.find.plugin", pluginId.idString),
                                     IdeBundle.message("new.project.wizard.cannot.find.plugin.title"))
        }
        return null
      }.first()

    return PluginDownloader.createDownloader(pluginNode)
  }

  companion object {
    private val LOG = Logger.getInstance(InstallAndEnablePluginTask::class.java)

    fun doInstallPlugins(component: Component, downloader: PluginDownloader) {
      val plugin = downloader.descriptor
      val nodes = mutableListOf<PluginNode>()
      if (plugin.isEnabled) {
        nodes.add(downloader.toPluginNode())
      }
      PluginManagerMain.suggestToEnableInstalledDependantPlugins(PluginEnabler.HEADLESS, nodes)
      PluginEnabler.HEADLESS.enable(listOf(plugin))
      downloadPlugins(nodes, ModalityState.stateForComponent(component))
    }

    fun downloadPlugins(plugins: List<PluginNode>, modalityState: ModalityState) {
      ProgressManager.getInstance().run(object : Modal(null, IdeBundle.message("progress.download.plugins"), true) {
        override fun run(indicator: ProgressIndicator) {
          val operation = PluginInstallOperation(plugins, emptyList(), PluginEnabler.HEADLESS, indicator)
            .apply {
              setAllowInstallWithoutRestart(true)
              run()
            }
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