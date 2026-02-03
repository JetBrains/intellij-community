// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagementPolicy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.marketplace.MarketplaceRequests.Companion.getLastCompatiblePluginUpdate
import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUiModelAdapter
import com.intellij.ide.plugins.newui.loadAllPluginDetailsSync
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

@ApiStatus.Internal
class InstallAndEnableTask(
  private val project: Project?,
  private val pluginIds: Set<PluginId>,
  private val showDialog: Boolean,
  private val selectAllInDialog: Boolean,
  private val modalityState: ModalityState?,
  private val onSuccess: Runnable,
) {
  private val plugins: MutableSet<PluginDownloader> = mutableSetOf()
  fun getPlugins(): Set<PluginDownloader> = plugins

  private val customPlugins: MutableList<PluginUiModel> = mutableListOf()
  fun getCustomPlugins(): List<PluginUiModel> = customPlugins

  suspend fun execute() {
    loadPlugins()
    showDialogAndRunSuccess()
  }

  fun runBlocking(): Unit = runWithModalProgressBlocking(
    owner = if (project != null) ModalTaskOwner.project(project) else ModalTaskOwner.guess(),
    title = IdeBundle.message("plugins.advertiser.task.searching.for.plugins"),
    cancellation = TaskCancellation.cancellable(),
    action = { execute() }
  )


  private suspend fun loadPlugins() = runInterruptible { //for proper cancellation
    try {
      val marketplacePlugins: List<PluginUiModel> = runBlockingCancellable { MarketplaceRequests.loadLastCompatiblePluginModels(pluginIds) }
      val customPluginNodes = runBlockingCancellable {
        coroutineToIndicator { indicator ->
          RepositoryHelper.loadPluginsFromCustomRepositories(indicator, PluginNodeModelBuilderFactory)
        }
      }
      customPlugins.addAll(customPluginNodes.map { PluginUiModelAdapter(it) })
      val descriptors: MutableList<PluginUiModel> = RepositoryHelper
        .mergePluginModelsFromRepositories(marketplacePlugins, customPlugins, true)
        .filter { pluginIds.contains(it.pluginId) }
        .toMutableList()

      if (showDialog) {
        val marketplace = MarketplaceRequests.getInstance()
        val descriptorIds: Set<PluginId> = descriptors.mapTo(mutableSetOf()) { it.pluginId }
        val compatiblePluginUpdates = runBlockingCancellable { getLastCompatiblePluginUpdate(descriptorIds) }
        for (update in compatiblePluginUpdates) {
          val index = ContainerUtil.indexOf(descriptors) { d -> d.pluginId.idString == update.pluginId }
          if (index != -1) {
            val descriptor = descriptors[index]

            descriptor.externalPluginId = update.externalPluginId
            descriptor.externalUpdateId = update.externalUpdateId
            descriptor.description = null

            runBlockingCancellable { marketplace.loadPluginDetails(descriptor) }?.let { pluginModel ->
              loadAllPluginDetailsSync(descriptor, pluginModel)
              descriptors[index] = pluginModel
            }
          }
        }
      }

      for (descriptor in PluginManagerCore.plugins) {
        if (PluginManagerCore.isDisabled(descriptor.pluginId) &&
            PluginManagerCore.isCompatible(descriptor) &&
            PluginManagementPolicy.getInstance().canInstallPlugin(descriptor)) {
          descriptors.add(PluginUiModelAdapter(descriptor))
        }
      }

      for (descriptor in descriptors) {
        if (pluginIds.contains(descriptor.pluginId)) {
          plugins.add(PluginDownloader.createDownloader(descriptor, null, null))
        }
      }
    }
    catch (e: CancellationException) {
      thisLogger().info("Search for Plugins in Repository task cancelled")
      throw e
    }
    catch (e: Exception) {
      thisLogger().info(e)
    }
  }

  private suspend fun showDialogAndRunSuccess() {
    withContext(Dispatchers.EDT) {
      PluginsAdvertiserDialog(
        project,
        plugins,
        customPlugins.map { it.getDescriptor() as PluginNode },
        selectAllInDialog,
        ::runOnSuccess,
      ).doInstallPlugins(showDialog, modalityState)
    }
  }

  private fun runOnSuccess(success: Boolean) {
    if (success) {
      onSuccess.run()
    }
  }
}
