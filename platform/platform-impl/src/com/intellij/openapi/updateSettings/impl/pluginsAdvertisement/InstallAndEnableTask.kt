// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.PluginManagerCore.isCompatible
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.plugins.newui.PluginUiModelAdapter
import com.intellij.ide.plugins.newui.loadAllPluginDetailsSync
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.Condition
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Internal
class InstallAndEnableTask internal constructor(
  project: Project?,
  private val myPluginIds: Set<PluginId>,
  private val myShowDialog: Boolean,
  private val mySelectAllInDialog: Boolean,
  private val myModalityState: ModalityState?,
  private val myOnSuccess: Runnable,
) : Task.Modal(project, IdeBundle.message("plugins.advertiser.task.searching.for.plugins"), true) {
  val plugins: MutableSet<PluginDownloader?> = HashSet()
  var customPlugins: MutableList<PluginNode?>? = null
    private set

  override fun run(indicator: ProgressIndicator) {
    try {
      val marketplacePlugins: List<PluginNode?> = MarketplaceRequests.loadLastCompatiblePluginDescriptors(myPluginIds)
      this.customPlugins = RepositoryHelper.loadPluginsFromCustomRepositories(indicator, PluginNodeModelBuilderFactory)

      val descriptors: MutableList<IdeaPluginDescriptor> = ArrayList<IdeaPluginDescriptor>(
        RepositoryHelper.mergePluginsFromRepositories(
          marketplacePlugins,
          this.customPlugins!!, true
        )
      )
      descriptors.removeIf { descriptor: IdeaPluginDescriptor? -> !myPluginIds.contains(descriptor!!.getPluginId()) }

      if (myShowDialog) {
        val marketplace = MarketplaceRequests.getInstance()
        val pluginIds = ContainerUtil.map2Set<IdeaPluginDescriptor?, PluginId?>(
          descriptors,
          Function { descriptor: IdeaPluginDescriptor? -> descriptor!!.getPluginId() })
        for (update in MarketplaceRequests.getLastCompatiblePluginUpdate(pluginIds)) {
          val index = ContainerUtil.indexOf<IdeaPluginDescriptor?>(
            descriptors,
            Condition { d: IdeaPluginDescriptor? -> d!!.getPluginId().idString == update.pluginId })
          if (index != -1) {
            val descriptor: IdeaPluginDescriptor? = descriptors.get(index)
            if (descriptor is PluginNode) {
              descriptor.setExternalPluginId(update.externalPluginId)
              descriptor.setExternalUpdateId(update.externalUpdateId)
              descriptor.setDescription(null)

              val marketplaceModel = PluginUiModelAdapter(descriptor)
              val pluginNode = marketplace.loadPluginDetails(marketplaceModel)
              if (pluginNode != null) {
                loadAllPluginDetailsSync(marketplaceModel, pluginNode)
                descriptors[index] = pluginNode.getDescriptor()
              }
            }
          }
        }
      }

      for (descriptor in PluginManagerCore.plugins) {
        if (!descriptor.isEnabled() &&
            isCompatible(descriptor) &&
            PluginManagementPolicy.getInstance().canInstallPlugin(descriptor)
        ) {
          descriptors.add(descriptor)
        }
      }

      for (descriptor in descriptors) {
        if (myPluginIds.contains(descriptor.getPluginId())) {
          plugins.add(PluginDownloader.createDownloader(descriptor))
        }
      }
    }
    catch (e: Exception) {
      Logger.getInstance(InstallAndEnableTask::class.java).info(e)
    }
  }

  override fun onSuccess() {
    if (this.customPlugins == null) {
      return
    }

    PluginsAdvertiserDialog(
      myProject,
      this.plugins,
      this.customPlugins!!,
      mySelectAllInDialog,
      Consumer { onSuccess: Boolean? -> this.runOnSuccess(onSuccess!!) })
      .doInstallPlugins(myShowDialog, myModalityState)
  }

  private fun runOnSuccess(onSuccess: Boolean) {
    if (onSuccess) {
      myOnSuccess.run()
    }
  }
}
