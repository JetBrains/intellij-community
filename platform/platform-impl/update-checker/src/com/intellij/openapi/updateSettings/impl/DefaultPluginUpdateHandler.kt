// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import javax.swing.JComponent

@ApiStatus.Internal
class DefaultPluginUpdateHandler : PluginUpdateHandler {
  private val myDownloaders = ConcurrentHashMap<PluginId, PluginDownloader>()

  override suspend fun loadAndStorePluginUpdates(
    buildNumber: String?,
    indicator: ProgressIndicator?,
  ): PluginUpdatesModel {
    val buildNumber = BuildNumber.fromString(buildNumber)
    val internalPluginUpdates = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates(indicator, buildNumber)

    val pluginUpdates = internalPluginUpdates.pluginUpdates
    val notIgnoredDownloaders = pluginUpdates.allEnabled.filterNot { UpdateChecker.isIgnored(it.descriptor) }
    val updateModels = notIgnoredDownloaders.map { it.uiModel }
    val disabledUpdateModels = pluginUpdates.allDisabled.map { it.uiModel }
    val incompatiblePluginNames = pluginUpdates.incompatible.map { it.name }
    storeDownloaders(notIgnoredDownloaders)
    val errors = internalPluginUpdates.errors.map { it.key to it.value.message.orEmpty() }.toMap()
    val updateModel = PluginUpdatesModel(pluginUpdates = updateModels.map { PluginDto.fromModel(it) },
                                         disabledPluginUpdates = disabledUpdateModels.map { PluginDto.fromModel(it) },
                                         incompatiblePluginNames = incompatiblePluginNames,
                                         updatesFromCustomRepositories = internalPluginUpdates.pluginNods.map { PluginDto.fromModel(it) },
                                         internalErrors = errors)
    updateModel.downloaders = notIgnoredDownloaders
    return updateModel
  }

  override suspend fun installUpdates(updates: Collection<PluginUiModel>, component: JComponent?, finishCallback: Runnable?, customRestarter: Consumer<Boolean>?) {
    val downloaders = updates.mapNotNull { this.myDownloaders[it.pluginId] }
    PluginUpdateDialog.runUpdateAll(downloaders, component, finishCallback, customRestarter)
  }

  override suspend fun ignorePluginUpdates() {
    UpdateCheckerFacade.getInstance().ignorePlugins(myDownloaders.values.map { it.descriptor })
  }

  private fun storeDownloaders(downloaders: List<PluginDownloader>) {
    myDownloaders.clear()
    downloaders.forEach { myDownloaders[it.descriptor.pluginId] = it }
  }
}
