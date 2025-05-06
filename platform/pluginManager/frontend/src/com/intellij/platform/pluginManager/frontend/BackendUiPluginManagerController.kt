// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.frontend

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManagerController
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.pluginManager.shared.rpc.PluginManagerApi
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendUiPluginManagerController: UiPluginManagerController {
  override fun getPlugins(): List<PluginUiModel> {
    return runBlockingCancellable {
      PluginManagerApi.Companion.getInstance().getPlugins()
    }
  }

  override fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    TODO("Not yet implemented")
  }

  override fun getInstalledPlugins(): List<PluginUiModel> {
    return runBlockingCancellable {
      PluginManagerApi.Companion.getInstance().getPlugins()
    }
  }

  override fun isPluginDisabled(pluginId: PluginId): Boolean {
    TODO("Not yet implemented")
  }
}