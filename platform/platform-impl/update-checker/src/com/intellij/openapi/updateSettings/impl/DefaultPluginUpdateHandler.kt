// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent

@OptIn(IntellijInternalApi::class)
@ApiStatus.Internal
class DefaultPluginUpdateHandler : PluginUpdateHandler {
  private val downloaders = ConcurrentHashMap<String, PluginDownloaders>()

  override suspend fun loadAndStorePluginUpdates(
    buildNumber: String?,
    sessionId: String,
    indicator: ProgressIndicator?,
  ): PluginUpdatesModel {
    val buildNumber = BuildNumber.fromString(buildNumber)
    val internalPluginUpdates = UpdateCheckerFacade.getInstance().checkInstalledPluginUpdates(indicator, buildNumber)

    val pluginUpdates = internalPluginUpdates.pluginUpdates
    val notIgnoredDownloaders = pluginUpdates.allEnabled.filterNot { UpdateChecker.isIgnored(it.descriptor) }
    val updateModels = notIgnoredDownloaders.mapNotNull { it.uiModel }
    val incompatiblePluginNames = pluginUpdates.incompatible.map { it.name }
    registerDownloaders(sessionId, notIgnoredDownloaders)
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
    val downloaders = updates.mapNotNull { getDownloader(sessionId, it.pluginId.idString) }
    val callbackWrapper = {
      finishCallback?.run()
      deleteSession(sessionId)
    }
    PluginUpdateDialog.runUpdateAll(downloaders, component, callbackWrapper, null)
  }

  override suspend fun ignorePluginUpdates(sessionId: String) {
    UpdateCheckerFacade.getInstance().ignorePlugins(getDownloaders(sessionId).map { it.descriptor })
  }

  private fun registerDownloader(sessionId: String, pluginId: String, downloader: PluginDownloader) {
    downloaders.getOrPut(sessionId) { ConcurrentHashMap<String, PluginDownloader>() }[pluginId] = downloader
  }

  private fun registerDownloaders(sessionId: String, downloaders: List<PluginDownloader>) {
    downloaders.forEach { registerDownloader(sessionId, it.descriptor.pluginId.idString, it) }
  }

  private fun getDownloader(sessionId: String, pluginId: String): PluginDownloader? {
    return downloaders[sessionId]?.get(pluginId)
  }

  private fun getDownloaders(sessionId: String): List<PluginDownloader> = downloaders[sessionId]?.values?.toList() ?: emptyList()

  private fun deleteSession(sessionId: String) {
    downloaders.remove(sessionId)
  }
}

typealias PluginDownloaders = ConcurrentHashMap<String, PluginDownloader>
