// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import javax.swing.JComponent

// Class used for loading and installing plugin updates on the backend and frontend.
// Allows us to override default implementation with the combined one when application started in Remote Development mode.
// Eventually should be responsible for all plugin updates.
@ApiStatus.Internal
interface PluginUpdateHandler {
  suspend fun loadAndStorePluginUpdates(buildNumber: String?, sessionId: String = UUID.randomUUID().toString(), indicator: ProgressIndicator? = null): PluginUpdatesModel
  suspend fun installUpdates(sessionId: String, updates: List<PluginUiModel>, component: JComponent?, finishCallback: Runnable?)

  suspend fun ignorePluginUpdates(sessionId: String)

  companion object {
    fun getInstance(): PluginUpdateHandler = PluginUpdateHandlerProvider.getInstance().getPluginUpdateHandler()
  }
}

@ApiStatus.Internal
@Serializable
data class PluginUpdatesModel(
  val sessionId: String,
  val pluginUpdates: List<PluginDto>,
  val updatesFromCustomRepositories: List<PluginDto>,
  val incompatiblePluginNames: List<String>,
  val internalErrors: Map<String?, String>,
) {

  @Transient
  var downloaders: List<PluginDownloader> = emptyList() //compatibility parameter that shouldn't exist in the future
}
