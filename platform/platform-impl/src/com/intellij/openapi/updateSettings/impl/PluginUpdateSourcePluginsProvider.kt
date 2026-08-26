// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Provides the available (also unloaded) plugin descriptors used by [PluginUpdateSourceService].
 *
 * This service keeps the plugin list lookup replaceable in tests without exposing mutable test hooks
 * on [PluginUpdateSourceService] or depending on its implementation class.
 */
@ApiStatus.Internal
interface PluginUpdateSourcePluginsProvider {
  companion object {
    fun getInstance(): PluginUpdateSourcePluginsProvider =
      ApplicationManager.getApplication().getService(PluginUpdateSourcePluginsProvider::class.java)!!
  }

  fun getAllPlugins(): Collection<PluginDescriptor>
}

internal class PluginUpdateSourcePluginsProviderImpl : PluginUpdateSourcePluginsProvider {
  override fun getAllPlugins(): Collection<PluginDescriptor> {
    return PluginManagerCore.getPluginSet().allPlugins
  }
}
