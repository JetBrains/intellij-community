// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AdditionalTextAttributesProvider : PluginAware {
  fun getScheme(): String
  fun getFile(): String
  fun getPluginDescriptor(): PluginDescriptor
}

@ApiStatus.Internal
abstract class AdditionalTextAttributesProviderBase(private val scheme: String) : AdditionalTextAttributesProvider {
  private lateinit var pluginDescriptor: PluginDescriptor

  final override fun getPluginDescriptor(): PluginDescriptor {
    return pluginDescriptor
  }

  final override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  final override fun getScheme(): String {
    return scheme
  }
}
