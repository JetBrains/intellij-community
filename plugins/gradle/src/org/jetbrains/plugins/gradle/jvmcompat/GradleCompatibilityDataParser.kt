// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal object GradleCompatibilityDataParser : IdeVersionedDataParser<GradleCompatibilityState>() {
  private fun JsonArray.parseVersions(): List<String> {
    return filter { it.isJsonPrimitive }.map { it.asString }
  }

  override fun parseJson(data: JsonObject): GradleCompatibilityState? {
    val supportedJavaVersionsArr = data["supportedJavaVersions"]?.asSafeJsonArray ?: return null
    val supportedGradleVersionsArr = data["supportedGradleVersions"]?.asSafeJsonArray ?: return null
    val compatibilityArr = data["compatibility"]?.asSafeJsonArray ?: return null

    val supportedJavaVersions = supportedJavaVersionsArr.parseVersions()
    val supportedGradleVersions = supportedGradleVersionsArr.parseVersions()
    val versionMappings = compatibilityArr
      .mapNotNull { element ->
        val obj = element.asSafeJsonObject ?: return@mapNotNull null
        val versionMapping = VersionMapping()
        versionMapping.javaVersionInfo = obj["java"]?.asSafeString ?: return@mapNotNull null
        versionMapping.gradleVersionInfo = obj["gradle"]?.asSafeString ?: return@mapNotNull null
        versionMapping
      }

    return GradleCompatibilityState(versionMappings, supportedJavaVersions, supportedGradleVersions)
  }
}