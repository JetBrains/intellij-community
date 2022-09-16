package com.intellij.javascript.web.webTypes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

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