// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class WebTypesEmbeddedIconLoader(private val pluginDescriptor: PluginDescriptor) {

  fun loadIcon(path: String): Icon =
    try {
      IconLoader.findIcon(path, pluginDescriptor.classLoader)
    }
    catch (e: Throwable) {
      logger<WebTypesEmbeddedIconLoader>()
        .warn("Cannot load icon $path from plugin ${pluginDescriptor.pluginId}", e)
      null
    } ?: EmptyIcon.ICON_16

}