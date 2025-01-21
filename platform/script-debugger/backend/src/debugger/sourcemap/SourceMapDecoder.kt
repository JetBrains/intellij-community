/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger.sourcemap

import com.google.gson.JsonParseException
import com.google.gson.stream.JsonToken
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.PathUtil
import com.intellij.util.SmartList
import com.intellij.util.UriUtil
import com.intellij.util.Url
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.debugger.sourcemap.Base64VLQ.CharIterator
import org.jetbrains.io.JsonReaderEx
import java.io.IOException
import java.nio.file.Path


internal const val UNMAPPED = -1

@ApiStatus.Internal
fun decodeSourceMapFromFile(file: Path,
                            trimFileScheme: Boolean,
                            baseUrl: Url?,
                            baseUrlIsFile: Boolean): SourceMap? {
  return FileBackedSourceMap.newFileBackedSourceMap(file, trimFileScheme, baseUrl, baseUrlIsFile)
}

fun decodeSourceMapSafely(sourceMapData: CharSequence,
                          trimFileScheme: Boolean,
                          baseUrl: Url?,
                          baseUrlIsFile: Boolean): SourceMap? {
  return decodeSourceMap(sourceMapData) { sourceUrls -> SourceResolver(sourceUrls, trimFileScheme, baseUrl, baseUrlIsFile) }
}

internal fun parseMapSafely(sourceMapData: CharSequence, mapDebugName: String?): SourceMapDataImpl? {
  try {
    if (sourceMapData.isEmpty()) {
      throw IOException("source map contents cannot be empty")
    }
    val reader = JsonReaderEx(sourceMapData)
    reader.isLenient = true
    return parseMap(reader)
  }
  catch (e: JsonParseException) {
    logger<SourceMap>().warn("Cannot decode sourcemap $mapDebugName", e)
  }
  catch (t: Throwable) {
    // WEB-9565
    logger<SourceMap>().error("Cannot decode sourcemap $mapDebugName", t, Attachment("sourceMap.txt", sourceMapData.toString()))
  }

  return null
}

// https://docs.google.com/document/d/1U1RGAehQwRypUTovF1KRlpiOFze0b-_2gc6fAH0KY0k/edit?hl=en_US
@ApiStatus.Internal
fun decodeSourceMap(sourceMapData: CharSequence, sourceResolverFactory: (sourceUrls: List<String>) -> SourceResolver): SourceMap? {
  val data = SourceMapDataCache.getOrCreate(sourceMapData.toString()) ?: return null
  return OneLevelSourceMap(data, sourceResolverFactory(data.sourceMapData.sources))
}

internal fun calculateReverseMappings(data: SourceMapData): Array<MappingList?> {
  val reverseMappingsBySourceUrl = arrayOfNulls<MutableList<MappingEntry>?>(data.sources.size)
  for (entry in data.mappings) {
    val sourceIndex = entry.source
    if (sourceIndex >= 0) {
      val reverseMappings = getMapping(reverseMappingsBySourceUrl, sourceIndex)
      reverseMappings.add(entry)
    }
  }
  return Array(reverseMappingsBySourceUrl.size) {
    val entries = reverseMappingsBySourceUrl[it]
    if (entries == null) {
      null
    }
    else {
      SourceMappingList(entries)
    }
  }
}

private fun parseMap(reader: JsonReaderEx): SourceMapDataImpl? {
  reader.beginObject()
  var sourceRoot: String? = null
  var sourcesReader: JsonReaderEx? = null
  var names: List<String>? = null
  var encodedMappings: String? = null
  var file: String? = null
  var version = -1
  var sourcesContent: MutableList<String?>? = null
  val mappings = ArrayList<MappingEntry>()
  while (reader.hasNext()) {
    when (reader.nextName()) {
      "sections" -> throw IOException("sections is not supported yet")
      "version" -> {
        version = reader.nextInt()
      }
      "sourceRoot" -> {
        sourceRoot = StringUtil.nullize(readSourcePath(reader))
        if (sourceRoot != null && sourceRoot != "/") {
          sourceRoot = UriUtil.trimTrailingSlashes(sourceRoot)
        }
      }
      "sources" -> {
        sourcesReader = reader.subReader()
        reader.skipValue()
      }
      "names" -> {
        reader.beginArray()
        if (reader.hasNext()) {
          names = ArrayList()
          do {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
              // polymer map
              reader.skipValue()
              names.add("POLYMER UNKNOWN NAME")
            }
            else {
              names.add(reader.nextString(true))
            }
          }
          while (reader.hasNext())
        }
        else {
          names = emptyList()
        }
        reader.endArray()
      }
      "mappings" -> {
        encodedMappings = reader.nextString()
      }
      "file" -> {
        file = reader.nextNullableString()
      }
      "sourcesContent" -> {
        if (reader.peek() == JsonToken.NULL) {
          reader.nextNull()
          continue
        }
        reader.beginArray()
        if (reader.peek() != JsonToken.END_ARRAY) {
          sourcesContent = SmartList<String>()
          do {
            if (reader.peek() == JsonToken.STRING) {
              sourcesContent.add(StringUtilRt.convertLineSeparators(reader.nextString()))
            }
            else if (reader.peek() == JsonToken.NULL) {
              // null means source file should be resolved by url
              sourcesContent.add(null)
              reader.nextNull()
            }
            else {
              logger<SourceMap>().warn("Unknown sourcesContent element: ${reader.peek().name}")
              reader.skipValue()
            }
          }
          while (reader.hasNext())
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

  // check it before other checks, probably it is not a sourcemap file
  if (encodedMappings.isNullOrEmpty()) {
    // empty map
    return null
  }

  // https://stackoverflow.com/questions/36228177/jspm-system-js-debugging-with-typescript
  if (Registry.`is`("js.debugger.fix.jspm.source.maps", false) && encodedMappings.startsWith(";") && file != null && file.endsWith(".ts!transpiled")) {
    encodedMappings = encodedMappings.substring(1)
  }

  if (version != 3) {
    throw IOException("Unsupported sourcemap version: $version")
  }

  if (sourcesReader == null) {
    throw IOException("sources is not specified")
  }

  val sources = readSources(sourcesReader, sourceRoot)
  if (sources.isEmpty()) {
    // empty map, meteor can report such ugly maps
    return null
  }

  readMappings(encodedMappings, mappings, names)

  return SourceMapDataImpl(file, sources, sourcesContent, !names.isNullOrEmpty(), mappings)
}

private fun readSourcePath(reader: JsonReaderEx): String? = PathUtil.toSystemIndependentName(
  reader.nextNullableString()?.trim { it <= ' ' })

private fun readMappings(value: String,
                         mappings: MutableList<MappingEntry>,
                         names: List<String>?) {
  if (value.isEmpty()) {
    return
  }

  var line = 0
  var column = 0
  val charIterator = CharSequenceIterator(value)
  var sourceIndex = 0
  var sourceLine = 0
  var sourceColumn = 0
  var nameIndex = 0
  var prevEntry: MutableEntry? = null

  fun addEntry(entry: MutableEntry) {
    if (prevEntry != null) {
      prevEntry!!.nextGenerated = entry
    }
    prevEntry = entry
    mappings.add(entry)
  }

  while (charIterator.hasNext()) {
    if (charIterator.peek() == ',') {
      charIterator.next()
    }
    else {
      while (charIterator.peek() == ';') {
        line++
        column = 0
        charIterator.next()
        if (!charIterator.hasNext()) {
          return
        }
      }
    }

    column += Base64VLQ.decode(charIterator)
    if (isSeparator(charIterator)) {
      addEntry(UnmappedEntry(line, column))
      continue
    }

    val sourceIndexDelta = Base64VLQ.decode(charIterator)
    if (sourceIndexDelta != 0) {
      sourceIndex += sourceIndexDelta
    }
    sourceLine += Base64VLQ.decode(charIterator)
    sourceColumn += Base64VLQ.decode(charIterator)

    val entry: MutableEntry
    if (isSeparator(charIterator)) {
      entry = UnnamedEntry(line, column, sourceIndex, sourceLine, sourceColumn)
    }
    else {
      nameIndex += Base64VLQ.decode(charIterator)
      assert(names != null)
      entry = NamedEntry(names!![nameIndex], line, column, sourceIndex, sourceLine, sourceColumn)
    }
    addEntry(entry)
  }
}

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

private fun getMapping(reverseMappingsBySourceUrl: Array<MutableList<MappingEntry>?>, sourceIndex: Int): MutableList<MappingEntry> {
  var reverseMappings = reverseMappingsBySourceUrl[sourceIndex]
  if (reverseMappings == null) {
    reverseMappings = ArrayList()
    reverseMappingsBySourceUrl[sourceIndex] = reverseMappings
  }
  return reverseMappings
}

private fun isSeparator(charIterator: CharSequenceIterator): Boolean {
  if (!charIterator.hasNext()) {
    return true
  }

  val current = charIterator.peek()
  return current == ',' || current == ';'
}

internal interface MutableEntry : MappingEntry {
  override var nextGenerated: MappingEntry?
}

/**
 * Not mapped to a section in the original source.
 */
private data class UnmappedEntry(override val generatedLine: Int, override val generatedColumn: Int) : MappingEntry, MutableEntry {
  override val sourceLine = UNMAPPED

  override val sourceColumn = UNMAPPED

  override var nextGenerated: MappingEntry? = null
}

/**
 * Mapped to a section in the original source.
 */
private data class UnnamedEntry(override val generatedLine: Int,
                                override val generatedColumn: Int,
                                override val source: Int,
                                override val sourceLine: Int,
                                override val sourceColumn: Int) : MappingEntry, MutableEntry {
  override var nextGenerated: MappingEntry? = null
}

/**
 * Mapped to a section in the original source, and is associated with a name.
 */
private data class NamedEntry(override val name: String,
                              override val generatedLine: Int,
                              override val generatedColumn: Int,
                              override val source: Int,
                              override val sourceLine: Int,
                              override val sourceColumn: Int) : MappingEntry, MutableEntry {
  override var nextGenerated: MappingEntry? = null
}

// java CharacterIterator is ugly, next() impl, so, we reinvent
private class CharSequenceIterator(private val content: CharSequence) : CharIterator {
  private val length = content.length
  private var current = 0

  override fun next() = content.get(current++)

  fun peek() = content.get(current)

  override fun hasNext() = current < length
}

private class SourceMappingList(mappings: List<MappingEntry>) : MappingList(mappings) {

  override fun getLine(mapping: MappingEntry) = mapping.sourceLine

  override fun getColumn(mapping: MappingEntry) = mapping.sourceColumn
}

internal class GeneratedMappingList(mappings: List<MappingEntry>) : MappingList(mappings) {

  override fun getLine(mapping: MappingEntry) = mapping.generatedLine

  override fun getColumn(mapping: MappingEntry) = mapping.generatedColumn

  override fun getNext(mapping: MappingEntry): MappingEntry? {
    return mapping.nextGenerated
  }
}

