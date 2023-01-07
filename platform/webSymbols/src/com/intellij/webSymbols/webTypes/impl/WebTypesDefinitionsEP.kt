// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.text.SemVer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.webTypes.json.WebTypes
import com.intellij.webSymbols.webTypes.readWebTypes
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Internal
open class WebTypesDefinitionsEP : CustomLoadingExtensionPointBean<WebTypes>() {

  companion object {
    val EP_NAME = ExtensionPointName<WebTypesDefinitionsEP>("com.intellij.webSymbols.webTypes")
    val EP_NAME_DEPRECATED = ExtensionPointName<WebTypesDefinitionsEP>("com.intellij.javascript.webTypes")
  }

  @Attribute("source")
  @JvmField
  var source: String? = null

  @Attribute("enableByDefault")
  @JvmField
  var enableByDefault: Boolean? = null

  override fun getImplementationClassName(): String? = null

  override fun createInstance(componentManager: ComponentManager, pluginDescriptor: PluginDescriptor): WebTypes {
    val pluginId = pluginDescriptor.pluginId
    try {
      val inputStream = pluginDescriptor.classLoader.getResourceAsStream(source)
                        ?: throw PluginException("Cannot find web-types definitions located at '$source'", pluginId)
      val webTypes = inputStream.readWebTypes()
      if (webTypes.name == null) {
        throw PluginException("Missing package-name in web-types definitions from '$source'", pluginId)
      }

      if (SemVer.parseFromText(webTypes.version) == null) {
        throw PluginException("Cannot parse version '${webTypes.version}' in web-types definitions from '$source'", pluginId)
      }

      return webTypes
    }
    catch (e: IOException) {
      throw PluginException("Cannot load web-types definitions from '$source': ${e.message}", e, pluginId)
    }
  }
}