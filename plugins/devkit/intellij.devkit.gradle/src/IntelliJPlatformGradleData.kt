// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import java.io.Serializable
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

/** IDE-side completion metadata imported from [IntelliJPlatformGradleModel]. */
internal data class IntelliJPlatformGradleData(
  val dependencyHelperProductCodes: Map<String, String> = emptyMap(),
  val productReleases: Map<String, List<IntelliJPlatformProductRelease>> = emptyMap(),
) : Serializable {
  companion object {
    @JvmField
    val KEY: Key<IntelliJPlatformGradleData> =
      Key.create(IntelliJPlatformGradleData::class.java, ProjectKeys.MODULE.processingWeight + 1)
  }
}

internal data class IntelliJPlatformProductRelease(
  val version: String = "",
  val channel: String = "",
) : Serializable

internal fun IntelliJPlatformGradleModel.toIntelliJPlatformGradleData() = IntelliJPlatformGradleData(
  dependencyHelperProductCodes = dependencyHelperProductCodes,
  productReleases = productReleasesFile.readProductReleases(),
)

/** Reads `product-code<TAB>version<TAB>channel` records written by the Gradle task. */
internal fun String?.readProductReleases(): Map<String, List<IntelliJPlatformProductRelease>> {
  val file = this?.let { Path.of(it) }?.takeIf { it.isRegularFile() } ?: return emptyMap()

  return runCatching {
    file.readLines()
      .mapNotNull(::parseProductRelease)
      .groupBy(
        keySelector = { it.first },
        valueTransform = { it.second },
      )
  }.getOrDefault(emptyMap())
}

private fun parseProductRelease(value: String): Pair<String, IntelliJPlatformProductRelease>? {
  val fields = value.split('\t')
  if (fields.size != 3 || fields.any(String::isBlank)) return null

  return fields[0] to IntelliJPlatformProductRelease(
    version = fields[1],
    channel = fields[2],
  )
}
