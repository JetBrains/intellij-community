// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Supplies the [PluginUpdateHandler] of this process.
 *
 * The first registered extension wins: the embedded update checker registers the default provider, and the split frontend
 * registers its combined provider with `order="first"`. This is an extension point and not a service, so the module that
 * registers the frontend provider stays dynamically loadable (see IJPL-246709), and every process without a split frontend,
 * including the light product mode, gets the default provider.
 */
@ApiStatus.Internal
interface PluginUpdateHandlerProvider {
  fun getPluginUpdateHandler(): PluginUpdateHandler

  companion object {
    val EP_NAME: ExtensionPointName<PluginUpdateHandlerProvider> = ExtensionPointName("com.intellij.pluginUpdateHandlerProvider")

    fun getInstance(): PluginUpdateHandlerProvider =
      EP_NAME.extensionList.firstOrNull() ?: error("No PluginUpdateHandlerProvider extension is registered")
  }
}
