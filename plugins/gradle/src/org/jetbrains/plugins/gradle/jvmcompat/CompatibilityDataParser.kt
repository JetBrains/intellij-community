// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.intellij.util.lang.JavaVersion
import com.intellij.util.text.VersionComparatorUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.Ranges
import org.jetbrains.plugins.gradle.util.range
import java.io.StringReader

class CompatibilityDataParser(val ideVersion: String) {

  internal fun getCompatibilityRanges(data: CompatibilityData): List<Pair<Ranges<JavaVersion>, Ranges<GradleVersion>>> {
    return data.versionMappings.map { entry ->
      val gradleRange = parseRange(entry.gradleVersionInfo.split(','), GradleVersion::version)
      val javaRange = parseRange(entry.javaVersionInfo.split(','), JavaVersion::parse)
      javaRange to gradleRange
    }
  }

  internal fun parseJson(data: String): CompatibilityData? {
    val reader = Gson().newJsonReader(StringReader(data)).apply { isLenient = true }
    val result = CompatibilityData()
    reader.beginArray();
    while (reader.peek() == JsonToken.BEGIN_OBJECT) {
      reader.beginObject()
      val name = reader.nextName()
      if (name == "ideVersion") {
        val versionInfo = reader.nextString();
        val ideaRange = parseRange(listOf(versionInfo), ::IdeVersion)
        if (ideaRange.contains(IdeVersion(ideVersion))) {
          while (reader.peek() == JsonToken.NAME) {
            val nextName = reader.nextName()
            if (nextName == "supportedJavaVersions") {
              result.supportedJavaVersions = readArray(reader)
            }
            if (nextName == "supportedGradleVersions") {
              result.supportedGradleVersions = readArray(reader)
            }
            else if (nextName == "compatibility") {
              parseCompatibility(reader, result)
            }
          }
          return checkValid(result);
        }
        else {
          while (reader.peek() != JsonToken.END_OBJECT) {
            reader.skipValue();
          }
          reader.endObject()
        }
      }
      else {
        throw IllegalArgumentException("Cannot parse format: expected ideVersion, got: $name")
      }
    }
    reader.endArray()
    return null
  }

  private fun readArray(reader: JsonReader): List<String> {
    reader.beginArray();
    val result = ArrayList<String>()
    while (reader.peek() == JsonToken.STRING) {
      result.add(reader.nextString())
    }
    reader.endArray()
    return result
  }

  private fun checkValid(result: CompatibilityData): CompatibilityData {
    if (result.supportedJavaVersions == null) throw IllegalStateException("Supported java versions are null")
    if (result.supportedGradleVersions == null) throw IllegalStateException("Supported gradle versions are null")
    if (result.versionMappings == null) throw IllegalStateException("Version mappings are null")
    return result
  }

  private fun parseCompatibility(reader: JsonReader, result: CompatibilityData) {
    reader.beginArray()
    val versionMappings = ArrayList<VersionMapping>()
    while (reader.peek() == JsonToken.BEGIN_OBJECT) {
      reader.beginObject();
      val mapping = VersionMapping()
      while (reader.peek() == JsonToken.NAME) {
        val name = reader.nextName();
        val value = reader.nextString();
        if (name == "gradle") {
          mapping.gradleVersionInfo = value;
        }
        else if (name == "java") {
          mapping.javaVersionInfo = value;
        }
        else if (name == "comment") {
          mapping.comment = value
        }
      }
      versionMappings.add(mapping)
      reader.endObject()
    }
    reader.endArray();
    result.versionMappings = versionMappings
  }

  internal class IdeVersion(val v: String) : Comparable<IdeVersion> {
    override fun compareTo(other: IdeVersion): Int {
      return VersionComparatorUtil.compare(v, other.v) { 0 }
    }
  }

  private fun <T> parseVersion(p: String?, transform: (String) -> T): T? {
    if (p == null || p == "INF") return null;
    return transform(p)
  }

  private fun <T : Comparable<T>> parseRange(data: List<String>, transform: (String) -> T): Ranges<T> {
    val list = data.map { it.split('-') }

    list.firstOrNull { t -> t.size != 2 }?.let {
      throw IllegalArgumentException(
        "$data cannot be parsed as version range: Range be two versions separated by dash, version ranges separated by comma")
    }
    return range(*list.map { parseVersion(it[0], transform) to parseVersion(it[1], transform) }.toTypedArray())
  }

}
