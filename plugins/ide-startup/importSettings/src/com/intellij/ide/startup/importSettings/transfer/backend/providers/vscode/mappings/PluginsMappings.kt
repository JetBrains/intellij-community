// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vscode.mappings

import com.intellij.ide.startup.importSettings.models.BuiltInFeature
import com.intellij.ide.startup.importSettings.models.FeatureInfo
import com.intellij.ide.startup.importSettings.models.PluginFeature
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.PlatformUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Allows registering plugins of third-party products for importing from VSCode.
 */
interface VSCodePluginMapping {

  companion object {
    val EP_NAME: ExtensionPointName<VSCodePluginMapping> = ExtensionPointName("com.intellij.transferSettings.vscode.pluginMapping")
  }

  fun mapPlugin(pluginId: String): FeatureInfo?
}

@Serializable
private data class FeatureData(
  val vsCodeId: String,
  val ideaId: String? = null,
  val ideaName: String,
  val builtIn: Boolean = false,
  val disabled: Boolean = false
)

private val logger = logger<CommonPluginMapping>()

internal class CommonPluginMapping : VSCodePluginMapping {

  // Note that the later files will override the data from the former.
  @Suppress("DEPRECATION")
  private fun getResourceMappings(): List<String> = when {
    PlatformUtils.isAqua() -> listOf("general.json", "aq.json")
    PlatformUtils.isCLion() -> listOf("general.json", "cl.json")
    PlatformUtils.isDataGrip() -> listOf("general.json", "dg.json")
    PlatformUtils.isDataSpell() -> listOf("general.json", "ds.json")
    PlatformUtils.isGoIde() -> listOf("general.json", "ws.json", "dg.json", "go.json")
    PlatformUtils.isPhpStorm() -> listOf("general.json", "ps.json")
    PlatformUtils.isPyCharm() -> listOf("general.json", "pc.json", "pp.json")
    PlatformUtils.isPyCharmPro() -> listOf("general.json", "pc.json")
    PlatformUtils.isRider() -> listOf("general.json", "rd.json")
    PlatformUtils.isRubyMine() -> listOf("general.json", "rm.json")
    PlatformUtils.isRustRover() -> listOf("general.json", "rr.json")
    PlatformUtils.isWebStorm() -> listOf("general.json", "ws.json")

    // NOTE: order is important in this section
    PlatformUtils.isIdeaUltimate() -> listOf("general.json", "ic.json", "iu.json")
    PlatformUtils.isIntelliJ() -> listOf("general.json", "ic.json")

    else -> listOf("general.json")
  }

  val allPlugins by lazy {
    val resourceNames = getResourceMappings()
    val result = mutableMapOf<String, FeatureInfo>()
    for (resourceName in resourceNames) {
      logger.runAndLogException {
        @OptIn(ExperimentalSerializationApi::class)
        val features = this.javaClass.classLoader.getResourceAsStream("pluginData/$resourceName").use { file ->
          if (file == null) {
            logger.error("Cannot find resource $resourceName")
            return@runAndLogException
          }
          Json.decodeFromStream<List<FeatureData>>(file)
        }
        for (data in features) {
          val key = data.vsCodeId.lowercase()
          if (data.disabled) {
            result.remove(key)
            continue
          }

          val feature =
            if (data.builtIn) BuiltInFeature(null, data.ideaName)
            else {
              if (data.ideaId == null) {
                logger.error("Cannot determine IntelliJ plugin id for feature $data.")
                continue
              }
              PluginFeature(null, data.ideaId, data.ideaName)
            }
          result[key] = feature
        }
      }
    }

    result
  }

  override fun mapPlugin(pluginId: String) = allPlugins[pluginId.lowercase()]
}

object PluginsMappings {

  fun pluginIdMap(pluginId: String): FeatureInfo? {

    for (mapping in VSCodePluginMapping.EP_NAME.extensionList) {
      val feature = mapping.mapPlugin(pluginId)
      if (feature != null) return feature
    }

    return null
  }
}
