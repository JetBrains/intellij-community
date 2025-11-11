// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.DynamicBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SemanticBundleProvider {
  fun getSemanticBundle(): DynamicBundle? {
    val pluginId = PluginId.getId("com.intellij.ml.llm")
    if (!PluginManagerCore.isPluginInstalled(pluginId)) {
      return null
    }

    val descriptor = PluginManagerCore.getPlugin(pluginId) ?: return null
    val classLoader = descriptor.pluginClassLoader

    return try {
      val clazz = classLoader?.loadClass(
        "com.intellij.ml.llm.searchEverywhere.embeddings.SemanticSearchBundle"
      )
      val instanceField = clazz?.getDeclaredField("INSTANCE")
      instanceField?.get(null) as DynamicBundle
    } catch (_: Throwable) {
      null
    }
  }
}
