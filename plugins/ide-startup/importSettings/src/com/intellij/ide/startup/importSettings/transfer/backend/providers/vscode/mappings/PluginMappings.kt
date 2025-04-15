// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vscode.mappings

import com.intellij.ide.startup.importSettings.models.BuiltInFeature
import com.intellij.ide.startup.importSettings.models.FeatureInfo
import com.intellij.ide.startup.importSettings.models.PluginFeature
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.PlatformUtils
import com.intellij.util.text.nullize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream

/**
 * Allows customizing the list of plugins for importing from VSCode.
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

private const val ALTERNATE_JSON_FILE_PROPERTY = "intellij.vscode.plugin.json"

internal class CommonPluginMapping : VSCodePluginMapping {

  // Note that the later files will override the data from the former.
  @Suppress("DEPRECATION")
  private fun getResourceMappings(): List<String> = when {
    PlatformUtils.isAqua() -> listOf("aq.json")
    PlatformUtils.isCLion() -> listOf("cl.json")
    PlatformUtils.isDataGrip() -> listOf("dg.json")
    PlatformUtils.isDataSpell() -> listOf("ds.json")
    PlatformUtils.isGoIde() -> listOf("go.json")
    PlatformUtils.isPhpStorm() -> listOf("ps.json")
    PlatformUtils.isRider() -> listOf("rd.json")
    PlatformUtils.isRubyMine() -> listOf("rm.json")
    PlatformUtils.isRustRover() -> listOf("rr.json")
    PlatformUtils.isWebStorm() -> listOf("ws.json")

    // NOTE: order is important in this subsection
    PlatformUtils.isIdeaUltimate() -> listOf("ic.json", "iu.json")
    PlatformUtils.isIntelliJ() -> listOf("ic.json")
    PlatformUtils.isPyCharmPro() -> listOf("pc.json", "pp.json")
    PlatformUtils.isPyCharm() -> listOf("pc.json")

    else -> listOf()
  }

  private val allPlugins by lazy {
    System.getProperty(ALTERNATE_JSON_FILE_PROPERTY)?.nullize(nullizeSpaces = true)?.let {
      loadFromFile(Path(it))
    } ?: loadFromResources()
  }

  private fun loadFromFile(path: Path): Map<String, FeatureInfo> {
    val result = mutableMapOf<String, FeatureInfo>()
    path.inputStream().use { loadFromStream(it, result) }
    return result
  }

  private fun loadFromResources(): Map<String, FeatureInfo> {
    val resourceNames = getResourceMappings()
    val result = mutableMapOf<String, FeatureInfo>()
    for (resourceName in resourceNames) {
      logger.runAndLogException {
        this.javaClass.classLoader.getResourceAsStream("pluginData/$resourceName").use { file ->
          if (file == null) {
            logger.error("Cannot find resource $resourceName")
            return@runAndLogException
          }
          loadFromStream(file, result)
        }
      }
    }

    return result
  }

  private fun loadFromStream(input: InputStream, result: MutableMap<String, FeatureInfo>) {
    @OptIn(ExperimentalSerializationApi::class)
    val features = Json.decodeFromStream<List<FeatureData>>(input)
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

  override fun mapPlugin(pluginId: String) = allPlugins[pluginId.lowercase()]
}

object PluginMappings {

  fun pluginIdMap(pluginId: String): FeatureInfo? {

    for (mapping in VSCodePluginMapping.EP_NAME.extensionList) {
      val feature = mapping.mapPlugin(pluginId)
      if (feature != null) return feature
    }

    return null
  }

  fun vsCodeAiMapping(settings: Settings) {
    settings.plugins["com.intellij.ml.llm"] = PluginFeature(null, "com.intellij.ml.llm", "JetBrains AI Assistant")
  }
}
