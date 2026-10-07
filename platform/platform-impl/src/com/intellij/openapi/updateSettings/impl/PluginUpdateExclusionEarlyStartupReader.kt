// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.configurationStore.deserialize
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.JDOMUtil
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files

/**
 * Allows reading plugin update exclusions during early startup before [PluginUpdateExclusionService] is available.
 *
 * Use this during early startup only. Normal code should use [PluginUpdateExclusionService].
 */
@ApiStatus.Internal
object PluginUpdateExclusionEarlyStartupReader {

  internal fun isFunctionalitySupportedEarlyCheck(): Boolean {
    return AppMode.isMonolith()
  }

  /**
   * Reads exclusions from the current configuration path's `options/pluginUpdateExclusions.xml` file.
   * Returns an empty map if the file or component is missing.
   *
   * @throws IOException if the file cannot be read or parsed
   */
  @JvmStatic
  @Throws(IOException::class)
  fun read(): Map<PluginId, PluginUpdateExclusionService.Exclusion> {
    if (!isFunctionalitySupportedEarlyCheck()) {
      return emptyMap()
    }
    val xmlPath = PathManager.getConfigDir().resolve(PathManager.OPTIONS_DIRECTORY).resolve(PLUGIN_UPDATE_EXCLUSIONS_FILE_NAME)
    if (!Files.exists(xmlPath)) return emptyMap()

    try {
      val text = ComponentStorageUtil.loadTextContent(xmlPath)
      val component = ComponentStorageUtil.loadComponents(JDOMUtil.load(text), null)["PluginUpdateExclusionService"] ?: return emptyMap()
      val state = component.deserialize(PluginUpdateExclusionState::class.java)
      return state.excludedPlugins.entries.associate { entry ->
        PluginId.getId(entry.key) to entry.value
      }
    }
    catch (e: JDOMException) {
      throw IOException("Failed to parse $xmlPath", e)
    }
  }
}