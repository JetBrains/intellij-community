// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger.sourcemap

import com.google.gson.stream.JsonToken
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.PathUtil
import com.intellij.util.SmartList
import com.intellij.util.UriUtil
import com.intellij.util.text.nullize
import org.jetbrains.io.JsonReaderEx
import java.io.IOException

// https://tc39.es/ecma426/
internal fun readMap(sourceMapData: CharSequence): SourceMapV3? {
  val reader = JsonReaderEx(wrapWithStringViewIfNeeded(sourceMapData))
  reader.isLenient = true
  return doParseMap(reader)
}

private fun doParseMap(reader: JsonReaderEx): SourceMapV3? {
  if (reader.peek() == JsonToken.NULL) {
    reader.close()
    return null
  }

  var version: Int = -1
  var file: String? = null

  var sectionsReader: JsonReaderEx? = null

  var sourceRoot: String? = null
  var sourcesReader: JsonReaderEx? = null
  var sourcesContent: List<String?>? = null

  var names: List<String>? = null
  var encodedMappings: CharSequence? = null
  var ignoreList: List<Int>? = null

  reader.beginObject()

  while (reader.hasNext()) {
    when (reader.nextName()) {
      "version" -> version = reader.nextInt()
      "file" -> file = reader.nextNullableString()
      "sections" -> sectionsReader = reader.createSubReaderAndSkipValue()
      "sourceRoot" -> {
        sourceRoot = readSourcePath(reader).nullize()
        if (sourceRoot != null && sourceRoot != "/") {
          sourceRoot = UriUtil.trimTrailingSlashes(sourceRoot)
        }
      }
      "sources" -> sourcesReader = reader.createSubReaderAndSkipValue()
      "sourcesContent" -> {
        if (reader.peek() == JsonToken.NULL) {
          reader.nextNull()
          continue
        }
        reader.beginArray()
        if (reader.peek() != JsonToken.END_ARRAY) {
          sourcesContent = SmartList<String?>()
          do {
            when (reader.peek()) {
              JsonToken.STRING -> {
                sourcesContent.add(StringUtilRt.convertLineSeparators(reader.nextCharSequence(), "\n").toString())
              }
              JsonToken.NULL -> {
                // null means source file should be resolved by url
                sourcesContent.add(null)
                reader.nextNull()
              }
              else -> {
                logger<SourceMap>().warn("Unknown sourcesContent element: ${reader.peek().name}")
                reader.skipValue()
              }
            }
          }
          while (reader.hasNext())
        }
        reader.endArray()
      }
      "names" -> {
        names = mutableListOf()
        reader.beginArray()
        while (reader.hasNext()) {
          names.add(reader.nextString(true))
        }
        reader.endArray()
      }
      "mappings" -> encodedMappings = reader.nextCharSequence()
      "ignoreList", "x_google_ignoreList" -> {
        ignoreList = mutableListOf()
        reader.beginArray()
        while (reader.hasNext()) {
          ignoreList.add(reader.nextInt())
        }
        reader.endArray()
      }
      else -> {
        // skip file or extensions
        reader.skipValue()
      }
    }
  }
  reader.close()

  if (version != 3) {
    throw IOException("Unsupported sourcemap version: $version")
  }

  return if (sectionsReader != null) {
    val sections = parseSections(sectionsReader)
    SectionedSourceMap(version, file, sections)
  }
  else {
    if (encodedMappings == null) {
      throw IOException("Source map doesn't contain 'mappings' field")
    }

    if (sourcesReader == null) {
      throw IOException("Source map doesn't contain 'sources' field")
    }

    val sources = readSources(sourcesReader, sourceRoot)
    FlatSourceMap(version, file, sources, sourcesContent, sourceRoot, names, encodedMappings, ignoreList)
  }
}

private fun parseSections(reader: JsonReaderEx): List<Section> {
  val result = mutableListOf<Section>()
  reader.beginArray()
  while (reader.hasNext()) {
    val section = reader.createSubReaderAndSkipValue()
                    ?.let { parseSection(it) }
                  ?: throw IOException("Cannot parse source map section")
    result.add(section)
  }
  reader.endArray()
  return result
}

private fun parseSection(reader: JsonReaderEx): Section? {
  reader.beginObject()
  var offset: Offset? = null
  var map: SourceMapV3? = null
  while (reader.hasNext()) {
    when (reader.nextName()) {
      "offset" -> {
        reader.beginObject()
        var line = 0
        var column = 0
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "line" -> line = reader.nextInt()
            "column" -> column = reader.nextInt()
            else -> reader.skipValue()
          }
        }
        offset = Offset(line, column)
        reader.endObject()
      }
      "map" -> map = reader.createSubReaderAndSkipValue()?.let { doParseMap(it) }
      else -> reader.skipValue()
    }
  }
  reader.endObject()

  if (offset == null || map == null) {
    return null
  }

  return Section(offset, map)
}

private fun readSourcePath(reader: JsonReaderEx): String? = PathUtil.toSystemIndependentName(
  reader.nextNullableString()?.trim()
)

private fun readSources(reader: JsonReaderEx, sourceRoot: String?): List<String> {
  reader.beginArray()
  val sources: List<String>
  if (reader.peek() == JsonToken.END_ARRAY) {
    sources = emptyList()
  }
  else {
    sources = SmartList()
    do {
      var sourceUrl = readSourcePath(reader) ?: ""
      if (!sourceRoot.isNullOrEmpty()) {
        if (sourceRoot == "/") {
          sourceUrl = "/$sourceUrl"
        }
        else {
          sourceUrl = "$sourceRoot/$sourceUrl"
        }
      }
      sources.add(sourceUrl)
    }
    while (reader.hasNext())
  }
  reader.endArray()
  return sources
}