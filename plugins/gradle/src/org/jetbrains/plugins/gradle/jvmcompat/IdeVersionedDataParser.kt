// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.plugins.gradle.util.Ranges
import org.jetbrains.plugins.gradle.util.range
import java.io.StringReader

abstract class IdeVersionedDataParser<T : IdeVersionedDataState> {
  companion object {
    internal class IdeVersion(val v: String) : Comparable<IdeVersion> {
      override fun compareTo(other: IdeVersion): Int {
        return VersionComparatorUtil.compare(v, other.v, VersionComparatorUtil.TokenPrioritizer { 0 })
      }
    }

    fun <T> parseVersion(p: String?, transform: (String) -> T): T? {
      if (p == null || p == "INF") return null
      return transform(p)
    }

    fun <T : Comparable<T>> parseRange(data: List<String>, transform: (String) -> T): Ranges<T> {
      val list = data.map { it.split('-') }

      list.firstOrNull { t -> t.size != 2 }?.let {
        throw IllegalArgumentException(
          "$data cannot be parsed as version range: Range is two versions separated by dash, version ranges separated by comma")
      }
      return range(*list.map { parseVersion(it[0], transform) to parseVersion(it[1], transform) }.toTypedArray())
    }
  }

  fun parseVersionedJson(json: String, ideVersion: String): T? {
    val element = JsonParser.parseReader(StringReader(json))

    if (!element.isJsonArray) return null
    val versionEntries = element.asJsonArray

    val currentVersion = IdeVersion(ideVersion)

    for (entry in versionEntries) {
      val obj = entry.takeIf { it.isJsonObject }?.asJsonObject ?: continue
      val versionInfo = obj["ideVersion"]?.takeIf { it.isJsonPrimitive }?.asString ?: continue
      val ideaRange = parseRange(listOf(versionInfo), ::IdeVersion)
      if (!ideaRange.contains(currentVersion)) continue

      parseJson(obj)?.let {
        it.isDefault = false
        it.ideVersion = ApplicationInfo.getInstance().fullVersion
        it.lastUpdateTime = System.currentTimeMillis()
        return it
      }
    }

    return null
  }


   abstract fun parseJson(data: JsonObject): T?
}