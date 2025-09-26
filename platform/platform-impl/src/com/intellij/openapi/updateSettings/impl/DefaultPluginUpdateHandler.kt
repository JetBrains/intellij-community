// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class DefaultPluginUpdateHandler : PluginUpdateHandler {
  override suspend fun loadAndStorePluginUpdates(buildNumber: String?, sessionId: String, indicator: ProgressIndicator?): PluginUpdatesModel {
    val buildNumber = BuildNumber.fromString(buildNumber)
    val internalPluginUpdates = UpdateChecker.getInternalPluginUpdates(buildNumber, indicator)
    val pluginUpdates = internalPluginUpdates.pluginUpdates
    val notIgnoredDownloaders = pluginUpdates.allEnabled.filterNot { UpdateChecker.isIgnored(it.descriptor) }
    val updateModels = notIgnoredDownloaders.mapNotNull { it.uiModel }
    val incompatiblePluginNames = pluginUpdates.incompatible.map { it.name }
    PluginDownloadersHolder.getInstance().registerDownloaders(sessionId, notIgnoredDownloaders)
    val errors = internalPluginUpdates.errors.map { it.key to it.value.message.orEmpty() }.toMap()
    val updateModel = PluginUpdatesModel(pluginUpdates = updateModels.map { PluginDto.fromModel(it) },
                                         incompatiblePluginNames = incompatiblePluginNames,
                                         updatesFromCustomRepositories = internalPluginUpdates.pluginNods.map { PluginDto.fromModel(it) },
                                         internalErrors = errors,
                                         sessionId = sessionId)
    updateModel.downloaders = notIgnoredDownloaders
    return updateModel
  }

  override suspend fun installUpdates(sessionId: String, updates: List<PluginUiModel>, component: JComponent?, finishCallback: Runnable?) {
    val downloaders = updates.mapNotNull { PluginDownloadersHolder.getInstance().getDownloader(sessionId, it.pluginId.idString) }
    val callbackWrapper = {
      finishCallback?.run()
      PluginDownloadersHolder.getInstance().deleteSession(sessionId)
    }
    PluginUpdateDialog.runUpdateAll(downloaders, component, callbackWrapper, null)
  }

  override suspend fun ignorePluginUpdates(sessionId: String) {
    UpdateChecker.ignorePlugins(PluginDownloadersHolder.getInstance().getDownloaders(sessionId).map { it.descriptor })
  }
}