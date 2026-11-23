// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.vfs.DiskQueryRelay
import com.intellij.polySymbols.webTypes.json.WebTypes
import com.intellij.polySymbols.webTypes.readWebTypesRaw
import com.intellij.util.text.SemVer
import com.intellij.util.xmlb.annotations.Attribute
import java.io.IOException

class WebTypesDefinitionsEP : CustomLoadingExtensionPointBean<WebTypes>() {
  companion object {
    val EP_NAME: ExtensionPointName<WebTypesDefinitionsEP> = ExtensionPointName("com.intellij.polySymbols.webTypes")

    private val LOG = thisLogger()

    private data class WebTypesSource(val plugin: PluginDescriptor, val fileUrl: String)

    private val loader: DiskQueryRelay<WebTypesSource, WebTypes> = DiskQueryRelay {
      LOG.debug("Reading <polySymbols.webTypes> from file: $it")

      val inputStream = it.plugin.pluginClassLoader!!.getResourceAsStream(it.fileUrl)
                        ?: throw PluginException("Cannot find web-types definitions located at '${it.fileUrl}'", it.plugin.pluginId)
      inputStream.use { s -> readWebTypesRaw(s) }
    }

    /**
     * Force loading of extensions to prepare them outside of read action.
     * A failed load does not stop the loading of the other extensions.
     * [com.intellij.polySymbols.webTypes.WebTypesEmbeddedDefinitionsLoader] reports the failure.
     */
    fun ensureLoaded() {
      EP_NAME.extensionList.forEach {
        try {
          it.loadWebTypes()
        }
        catch (_: PluginException) {
        }
      }
    }
  }

  @Attribute("source")
  @JvmField
  var source: String? = null

  @Attribute("enableByDefault")
  @JvmField
  var enableByDefault: Boolean? = null

  @Volatile
  private var loadResult: Result<WebTypes>? = null

  override fun getImplementationClassName(): String? = null

  override fun createInstance(componentManager: ComponentManager, pluginDescriptor: PluginDescriptor): WebTypes =
    loadWebTypes()

  /**
   * Returns the Web Types from [source] and loads them on the first call.
   * Concurrent callers share one load and wait for it with `checkCanceled`.
   * A failed load is also kept, and each later call throws the same [PluginException].
   * Use it instead of [getInstance], because [getInstance] holds the monitor of the bean during the load.
   */
  fun loadWebTypes(): WebTypes {
    loadResult?.let { return it.getOrThrow() }
    val result = try {
      Result.success(readWebTypes())
    }
    catch (e: PluginException) {
      Result.failure(e)
    }
    loadResult = result
    return result.getOrThrow()
  }

  private fun readWebTypes(): WebTypes {
    val pluginDescriptor = pluginDescriptor
    try {
      val webTypes = loader.accessDiskWithCheckCanceled(WebTypesSource(pluginDescriptor, source!!))
      if (webTypes.name == null) {
        throw PluginException("Missing package-name in web-types definitions from '$source'", pluginDescriptor.pluginId)
      }

      if (SemVer.parseFromText(webTypes.version) == null) {
        throw PluginException("Cannot parse version '${webTypes.version}' in web-types definitions from '$source'", pluginDescriptor.pluginId)
      }

      return webTypes
    }
    catch (e: IOException) {
      throw PluginException("Cannot load web-types definitions from '$source': ${e.message}", e, pluginDescriptor.pluginId)
    }
  }
}