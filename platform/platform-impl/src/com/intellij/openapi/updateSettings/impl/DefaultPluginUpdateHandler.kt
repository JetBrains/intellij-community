// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import java.util.UUID
import javax.swing.JComponent

class DefaultPluginUpdateHandler : PluginUpdateHandler {
  override fun isEnabled(): Boolean {
    return true
  }

  override suspend fun loadAndStorePluginUpdates(apiVersion: String?, sessionId: String, indicator: ProgressIndicator?): PluginUpdateModel {
    val buildNumber = BuildNumber.fromString(apiVersion)
    val internalPluginUpdates = UpdateChecker.getInternalPluginUpdates(buildNumber, indicator)
    val pluginUpdates = internalPluginUpdates.pluginUpdates
    val notIgnoredDownloaders = pluginUpdates.allEnabled.filterNot { UpdateChecker.isIgnored(it.descriptor) }
    val updateModels = notIgnoredDownloaders.mapNotNull { it.uiModel }
    val incompatiblePluginNames = pluginUpdates.incompatible.map { it.name }
    PluginDownloadersHolder.getInstance().registerDownloaders(sessionId, notIgnoredDownloaders)
    val errors = internalPluginUpdates.errors.map { it.key to it.value.message.orEmpty() }.toMap()
    val updateModel = PluginUpdateModel(nonIgnoredUpdates = updateModels,
                                        incompatiblePluginNames = incompatiblePluginNames,
                                        customRepoPluginUpdates = internalPluginUpdates.pluginNods.toList(),
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