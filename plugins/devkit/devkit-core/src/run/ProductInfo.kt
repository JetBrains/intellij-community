// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.idea.devkit.run.ProductInfo.Launch.OS.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

private val json = Json { ignoreUnknownKeys = true }

/**
 * @return `null` if no `product-info.json` could be found in given IDE installation or on errors
 */
fun loadProductInfo(ideaJdkHome: String): ProductInfo? {
  val ideHomePath = Path.of(ideaJdkHome)
  val productInfoJsonPath = listOf(
    ApplicationEx.PRODUCT_INFO_FILE_NAME_MAC,
    ApplicationEx.PRODUCT_INFO_FILE_NAME,
  ).firstNotNullOfOrNull { ideHomePath.resolve(it).takeIf(Path::exists) } ?: return null

  return runCatching { json.decodeFromString<ProductInfo>(productInfoJsonPath.readText()) }
    .onFailure { logger<ProductInfo>().error("error parsing '$productInfoJsonPath'", it) }
    .getOrNull()
}

fun resolveIdeHomeVariable(path: String, ideHome: String): String =
  path
    .replace("\$APP_PACKAGE", ideHome)
    .replace("\$IDE_HOME", ideHome)
    .replace("%IDE_HOME%", ideHome)
    .replace("Contents/Contents", "Contents")
    .let { entry ->
      val value = entry.split("=").getOrNull(1) ?: entry
      when {
        runCatching { Path(value).exists() }.getOrElse { false } -> entry
        else -> entry.replace("/Contents", "")
      }
    }

/**
 * Represents information about the IntelliJ Platform product.
 * The information is retrieved from the `product-info.json` file in the IntelliJ Platform directory.
 */
@Serializable
data class ProductInfo(
  val name: String = "",
  val version: String = "",
  val buildNumber: String = "",
  val productCode: String = "",
  val launch: List<Launch> = mutableListOf(),
  val layout: List<LayoutItem> = mutableListOf(),
) {

  /**
   * Finds the [ProductInfo.Launch] object for the given architecture and OS.
   */
  fun getCurrentLaunch(): Launch {
    val availableArchitectures = launch.mapNotNull { it.arch }.toSet()
    val architecture = SystemInfo.OS_ARCH

    val arch = with(availableArchitectures) {
      when {
        isEmpty() -> null // older SDKs or Maven releases don't provide architecture information, null is used in such a case
        contains(architecture) -> architecture
        contains("amd64") && architecture == "x86_64" -> "amd64"
        else -> null
      }
    }

    val os = when {
      SystemInfo.isLinux -> Linux
      SystemInfo.isWindows -> Windows
      SystemInfo.isMac -> macOS
      else -> null
    }

    return launch
      .find { os == it.os && arch == it.arch }
      .let {
        requireNotNull(it) {
          val options = launch.associate { option -> option.os to option.arch }
          """
          Could not find launch information for $name $version ($buildNumber) using {os=$os, arch=$arch}.<br/>
          Available options: $options
          """.trimIndent()
        }
      }
      .run { copy(additionalJvmArguments = additionalJvmArguments.map { it.trim('"') }) }
  }

  fun getProductModuleJarPaths(): List<String> = layout
    .filter { it.kind == ProductInfo.LayoutItem.LayoutItemKind.productModuleV2 }
    .flatMap { it.classPath }

  /**
   * Represents a launch configuration for a product.
   */
  @Serializable
  data class Launch(
    val os: OS? = null,
    val arch: String? = null,
    val bootClassPathJarNames: List<String> = mutableListOf(),
    val additionalJvmArguments: List<String> = mutableListOf(),
  ) {

    @Suppress("EnumEntryName")
    enum class OS { Linux, Windows, macOS; }
  }

  @Serializable
  data class LayoutItem(
    val name: String,
    val kind: LayoutItemKind,
    val classPath: List<String> = mutableListOf(),
  ) {

    @Serializable
    enum class LayoutItemKind {
      plugin, pluginAlias, productModuleV2, moduleV2
    }
  }
}
