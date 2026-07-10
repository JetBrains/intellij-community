// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer
import javax.swing.JComponent

// Class used for loading and installing plugin updates on the backend and frontend.
// Allows us to override default implementation with the combined one when application started in Remote Development mode.
// Eventually should be responsible for all plugin updates.
@ApiStatus.Internal
interface PluginUpdateHandler {
  suspend fun loadAndStorePluginUpdates(buildNumber: String?, indicator: ProgressIndicator? = null): PluginUpdatesModel
  suspend fun installUpdates(updates: Collection<PluginUiModel>, component: JComponent?, finishCallback: Runnable?, customRestarter: Consumer<Boolean>? = null)

  suspend fun ignorePluginUpdates()

  companion object {
    @JvmStatic
    fun getInstance(): PluginUpdateHandler = PluginUpdateHandlerProvider.getInstance().getPluginUpdateHandler()

    @JvmStatic
    fun installUpdatesInBackground(
      updates: Collection<PluginUiModel>,
      component: JComponent?,
      finishCallback: Runnable?,
      customRestarter: Consumer<Boolean>? = null,
    ) {
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.IO) {
        getInstance().installUpdates(updates, component, finishCallback, customRestarter)
      }
    }

    @JvmStatic
    fun loadAndStorePluginUpdates(buildNumber: String?, indicator: ProgressIndicator? = null): PluginUpdatesModel {
      return runBlockingMaybeCancellable {
        getInstance().loadAndStorePluginUpdates(buildNumber, indicator)
      }
    }
  }
}

@ApiStatus.Internal
@Serializable
data class PluginUpdatesModel(
  val pluginUpdates: List<PluginDto>,
  val disabledPluginUpdates: List<PluginDto> = emptyList(),
  val updatesFromCustomRepositories: List<PluginDto>,
  val incompatiblePluginNames: List<String>,
  val internalErrors: Map<String?, String>,
) {

  @Transient
  var downloaders: List<PluginDownloader> = emptyList() //compatibility parameter that shouldn't exist in the future
}
