// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

/** IDE-side completion metadata imported from [IntelliJPlatformGradleModel]. */
internal data class IntelliJPlatformGradleData(
  val dependencyHelperProductCodes: Map<String, String> = emptyMap(),
  val productReleases: Map<String, List<IntelliJPlatformProductRelease>> = emptyMap(),
  val bundledPlugins: List<IntelliJPlatformBundledArtifact> = emptyList(),
  val bundledModules: List<IntelliJPlatformBundledArtifact> = emptyList(),
  val currentPluginVersion: String = "0.0.0",
  val latestPluginVersion: String = "0.0.0",
) {
  companion object {
    @JvmField
    val KEY = Key.create(IntelliJPlatformGradleData::class.java, ProjectKeys.MODULE.processingWeight + 1)
  }
}

internal data class IntelliJPlatformProductRelease(
  val version: String = "",
  val channel: String = "",
)

internal data class IntelliJPlatformBundledArtifact(
  val id: String = "",
  val name: String = "",
)

internal fun IntelliJPlatformGradleModel.toIntelliJPlatformGradleData() = IntelliJPlatformGradleData(
  dependencyHelperProductCodes = dependencyHelperProductCodes,
  productReleases = productReleasesFile.readProductReleases(),
  bundledPlugins = bundledPluginsFile.readBundledPlugins(),
  bundledModules = bundledModulesFile.readBundledModules(),
  currentPluginVersion = currentPluginVersion,
  latestPluginVersion = latestPluginVersion,
)

internal fun IntelliJPlatformGradleData.hasUsableData(): Boolean =
  productReleases.isNotEmpty() || bundledPlugins.isNotEmpty() || bundledModules.isNotEmpty() || dependencyHelperProductCodes.isNotEmpty() || currentPluginVersion != "0.0.0"

/** Reads `product-code<TAB>version<TAB>channel` records written by the Gradle task. */
internal fun String?.readProductReleases(): Map<String, List<IntelliJPlatformProductRelease>> =
  readTsv {
    parseTsvTriple(it) { productCode, version, channel ->
      productCode to IntelliJPlatformProductRelease(version = version, channel = channel)
    }
  }.groupBy(
    keySelector = { it.first },
    valueTransform = { it.second },
  )

/** Reads `plugin-id<TAB>plugin-name` records written by the Gradle task. */
internal fun String?.readBundledPlugins(): List<IntelliJPlatformBundledArtifact> =
  readTsv { parseTsvPair(it, ::IntelliJPlatformBundledArtifact) }

/** Reads `module-id<TAB>module-name` records written by the Gradle task. */
internal fun String?.readBundledModules(): List<IntelliJPlatformBundledArtifact> =
  readTsv { parseTsvPair(it, ::IntelliJPlatformBundledArtifact) }

private inline fun <T> String?.readTsv(transform: (List<String>) -> T?): List<T> {
  val file = this?.let { Path.of(it) }?.takeIf { it.isRegularFile() } ?: return emptyList()

  return runCatching {
    file.readLines().mapNotNull { line -> transform(line.split('\t')) }
  }.getOrDefault(emptyList())
}

private inline fun <T> parseTsvPair(fields: List<String>, create: (first: String, second: String) -> T): T? {
  val first = fields.firstOrNull()?.trim()?.takeIf(String::isNotEmpty) ?: return null
  return create(first, fields.getOrElse(1) { "" }.trim())
}

private inline fun <T> parseTsvTriple(fields: List<String>, create: (first: String, second: String, third: String) -> T): T? {
  if (fields.size != 3 || fields.any(String::isBlank)) return null
  return create(fields[0], fields[1], fields[2])
}
