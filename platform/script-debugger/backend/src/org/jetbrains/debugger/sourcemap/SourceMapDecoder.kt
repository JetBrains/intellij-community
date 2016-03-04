/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.gson.stream.JsonToken
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.PathUtil
import com.intellij.util.SmartList
import com.intellij.util.UriUtil
import com.intellij.util.containers.isNullOrEmpty
import org.jetbrains.debugger.sourcemap.Base64VLQ.CharIterator
import org.jetbrains.io.JsonReaderEx
import java.io.IOException
import java.util.*

private val MAPPING_COMPARATOR_BY_SOURCE_POSITION = Comparator<MappingEntry> { o1, o2 ->
  if (o1.sourceLine == o2.sourceLine) {
    o1.sourceColumn - o2.sourceColumn
  }
  else {
    o1.sourceLine - o2.sourceLine
  }
}

val MAPPING_COMPARATOR_BY_GENERATED_POSITION: Comparator<MappingEntry> = Comparator { o1, o2 ->
  if (o1.generatedLine == o2.generatedLine) {
    o1.generatedColumn - o2.generatedColumn
  }
  else {
    o1.generatedLine - o2.generatedLine
  }
}

internal const val UNMAPPED = -1

// https://docs.google.com/document/d/1U1RGAehQwRypUTovF1KRlpiOFze0b-_2gc6fAH0KY0k/edit?hl=en_US
fun decodeSourceMap(`in`: CharSequence, sourceResolverFactory: (sourceUrls: List<String>, sourceContents: List<String>?) -> SourceResolver): SourceMap? {
  if (`in`.isEmpty()) {
    throw IOException("source map contents cannot be empty")
  }

  val reader = JsonReaderEx(`in`)
  reader.isLenient = true
  return parseMap(reader, 0, 0, ArrayList<MappingEntry>(), sourceResolverFactory)
}

private fun parseMap(reader: JsonReaderEx,
                     line: Int,
                     column: Int,
                     mappings: MutableList<MappingEntry>,
                     sourceResolverFactory: (sourceUrls: List<String>, sourceContents: List<String>?) -> SourceResolver): SourceMap? {
  reader.beginObject()
  var sourceRoot: String? = null
  var sourcesReader: JsonReaderEx? = null
  var names: List<String>? = null
  var encodedMappings: String? = null
  var file: String? = null
  var version = -1
  var sourcesContent: MutableList<String>? = null
  while (reader.hasNext()) {
    when (reader.nextName()) {
      "sections" -> throw IOException("sections is not supported yet")
      "version" -> {
        version = reader.nextInt()
      }
      "sourceRoot" -> {
        sourceRoot = readSourcePath(reader)
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
          names = ArrayList<String>()
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
        file = reader.nextString()
      }
      "sourcesContent" -> {
        reader.beginArray()
        if (reader.peek() != JsonToken.END_ARRAY) {
          sourcesContent = SmartList<String>()
          do {
            if (reader.peek() == JsonToken.STRING) {
              sourcesContent.add(StringUtilRt.convertLineSeparators(reader.nextString()))
            }
            else {
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

  val reverseMappingsBySourceUrl = arrayOfNulls<MutableList<MappingEntry>?>(sources.size)
  readMappings(encodedMappings!!, line, column, mappings, reverseMappingsBySourceUrl, names)

  val sourceToEntries = Array<MappingList?>(reverseMappingsBySourceUrl.size) {
    val entries = reverseMappingsBySourceUrl[it]
    if (entries == null) {
      null
    }
    else {
      entries.sortWith(MAPPING_COMPARATOR_BY_SOURCE_POSITION)
      SourceMappingList(entries)
    }
  }
  return SourceMap(file, GeneratedMappingList(mappings), sourceToEntries, sourceResolverFactory(sources, sourcesContent), !names.isNullOrEmpty())
}

private fun readSourcePath(reader: JsonReaderEx) = PathUtil.toSystemIndependentName(StringUtil.nullize(reader.nextString().trim { it <= ' ' }))

private fun readMappings(value: String,
                         initialLine: Int,
                         initialColumn: Int,
                         mappings: MutableList<MappingEntry>,
                         reverseMappingsBySourceUrl: Array<MutableList<MappingEntry>?>,
                         names: List<String>?) {
  if (value.isNullOrEmpty()) {
    return
  }

  var line = initialLine
  var column = initialColumn
  val charIterator = CharSequenceIterator(value)
  var sourceIndex = 0
  var reverseMappings: MutableList<MappingEntry> = getMapping(reverseMappingsBySourceUrl, sourceIndex)
  var sourceLine = 0
  var sourceColumn = 0
  var nameIndex = 0
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
      mappings.add(UnmappedEntry(line, column))
      continue
    }

    val sourceIndexDelta = Base64VLQ.decode(charIterator)
    if (sourceIndexDelta != 0) {
      sourceIndex += sourceIndexDelta
      reverseMappings = getMapping(reverseMappingsBySourceUrl, sourceIndex)
    }
    sourceLine += Base64VLQ.decode(charIterator)
    sourceColumn += Base64VLQ.decode(charIterator)

    val entry: MappingEntry
    if (isSeparator(charIterator)) {
      entry = UnnamedEntry(line, column, sourceIndex, sourceLine, sourceColumn)
    }
    else {
      nameIndex += Base64VLQ.decode(charIterator)
      assert(names != null)
      entry = NamedEntry(names!![nameIndex], line, column, sourceIndex, sourceLine, sourceColumn)
    }
    reverseMappings.add(entry)
    mappings.add(entry)
  }
}

private fun readSources(reader: JsonReaderEx, sourceRoot: String?): List<String> {
  reader.beginArray()
  val sources: List<String>
  if (reader.peek() == JsonToken.END_ARRAY) {
    sources = emptyList()
  }
  else {
    sources = SmartList<String>()
    do {
      var sourceUrl = readSourcePath(reader)
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
    reverseMappings = ArrayList<MappingEntry>()
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

/**
 * Not mapped to a section in the original source.
 */
private open class UnmappedEntry internal constructor(private val line: Int, private val column: Int) : MappingEntry() {
  override fun getGeneratedColumn() = column

  override fun getGeneratedLine() = line

  override fun getSourceLine() = UNMAPPED

  override fun getSourceColumn() = UNMAPPED
}

/**
 * Mapped to a section in the original source.
 */
private open class UnnamedEntry internal constructor(line: Int, column: Int, private val source: Int, private val sourceLine: Int, private val sourceColumn: Int) : UnmappedEntry(line, column) {
  override fun getSource() = source

  override fun getSourceLine() = sourceLine

  override fun getSourceColumn() = sourceColumn
}

/**
 * Mapped to a section in the original source, and is associated with a name.
 */
private class NamedEntry(private val name: String, line: Int, column: Int, source: Int, sourceLine: Int, sourceColumn: Int) : UnnamedEntry(line, column, source, sourceLine, sourceColumn) {
  override fun getName() = name
}

// java CharacterIterator is ugly, next() impl, so, we reinvent
private class CharSequenceIterator(private val content: CharSequence) : CharIterator {
  private val length = content.length
  private var current = 0

  override fun next() = content[current++]

  internal fun peek() = content[current]

  override fun hasNext() = current < length
}

private class SourceMappingList(mappings: List<MappingEntry>) : MappingList(mappings) {
  override fun getLine(mapping: MappingEntry) = mapping.sourceLine

  override fun getColumn(mapping: MappingEntry) = mapping.sourceColumn

  override fun getComparator() = MAPPING_COMPARATOR_BY_SOURCE_POSITION
}

private class GeneratedMappingList(mappings: List<MappingEntry>) : MappingList(mappings) {
  override fun getLine(mapping: MappingEntry) = mapping.generatedLine

  override fun getColumn(mapping: MappingEntry) = mapping.generatedColumn

  override fun getComparator() = MAPPING_COMPARATOR_BY_GENERATED_POSITION
}

