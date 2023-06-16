// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal object GradleCompatibilityDataParser : IdeVersionedDataParser<GradleCompatibilityState>() {
  private fun JsonArray.parseVersions(): List<String> {
    return filter { it.isJsonPrimitive }.map { it.asString }
  }

  override fun parseJson(data: JsonObject): GradleCompatibilityState? {
    val supportedJavaVersionsArr = data["supportedJavaVersions"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    val supportedGradleVersionsArr = data["supportedGradleVersions"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    val compatibilityArr = data["compatibility"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return null

    val supportedJavaVersions = supportedJavaVersionsArr.parseVersions()
    val supportedGradleVersions = supportedGradleVersionsArr.parseVersions()
    val versionMappings = compatibilityArr
      .filter { it.isJsonObject }
      .mapNotNull { element ->
        val obj = element.asJsonObject
        val versionMapping = VersionMapping()
        versionMapping.javaVersionInfo = obj["java"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        versionMapping.gradleVersionInfo = obj["gradle"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        versionMapping.comment = obj["comment"]?.takeIf { it.isJsonPrimitive }?.asString
        versionMapping
      }

    return GradleCompatibilityState(versionMappings, supportedJavaVersions, supportedGradleVersions)
  }
}