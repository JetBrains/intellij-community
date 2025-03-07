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

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.Url
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.debugger.sourcemap.Base64VLQ.CharIterator
import java.io.IOException
import java.nio.file.Path
import kotlin.math.min

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

@ApiStatus.Internal
fun decodeSourceMap(sourceMapData: CharSequence, sourceResolverFactory: (sourceUrls: List<String>) -> SourceResolver): SourceMap? {
  val data = SourceMapDataCache.getOrCreate(sourceMapData) ?: return null
  return OneLevelSourceMap(data, sourceResolverFactory(data.sourceMapData.sources))
}

internal fun parseMapSafely(sourceMapData: CharSequence, mapDebugName: String?): SourceMapDataImpl? {
  try {
    if (sourceMapData.isEmpty()) {
      throw IOException("source map contents cannot be empty")
    }
    val rawMap = readMap(sourceMapData) ?: return null
    return flattenAndDecodeMappings(rawMap)
  }
  catch (t: Throwable) {
    // WEB-9565
    logger<SourceMap>().error("Cannot decode sourcemap $mapDebugName", t, Attachment("sourceMap.txt", sourceMapData.toString()))
  }

  return null
}

// The great idea to flatten the source maps was borrowed from
// https://github.com/jridgewell/trace-mapping/blob/main/src/flatten-map.ts
// It allows us to simply preprocess the map and leave all the rest of the code untouched
internal fun flattenAndDecodeMappings(rawMap: SourceMapV3): SourceMapDataImpl? {
  val mappings = mutableListOf<MutableEntry>()
  val sources = mutableListOf<String?>()
  val sourcesContent = mutableListOf<String?>()
  val names = mutableListOf<String>()
  val ignoreList = mutableListOf<Int>()
  traverse(
    rawMap,
    mappings,
    sources,
    sourcesContent,
    names,
    ignoreList,
    lineOffset = 0,
    columnOffset = 0,
    stopLine = Int.MAX_VALUE,
    stopColumn = Int.MAX_VALUE,
    null
  )

  if (mappings.isEmpty()) {
    return null
  }

  return SourceMapDataImpl(
    rawMap.file,
    sources.map { it ?: "" },
    sourcesContent,
    names.isNotEmpty(),
    mappings,
    ignoreList
  )
}

private fun traverse(
  map: SourceMapV3,
  mappings: MutableList<MutableEntry>,
  sources: MutableList<String?>,
  sourcesContent: MutableList<String?>,
  names: MutableList<String>,
  ignoreList: MutableList<Int>,
  lineOffset: Int,
  columnOffset: Int,
  stopLine: Int,
  stopColumn: Int,
  lastEntry: MutableEntry?,
) {
  var lastEntry = lastEntry
  when (map) {
    is SectionedSourceMap -> {
      for ((i, section) in map.sections.withIndex()) {
        val offset = section.offset

        var sl = stopLine
        var sc = stopColumn
        if (i + 1 < map.sections.size) {
          val nextOffset = map.sections[i + 1].offset;
          sl = min(stopLine, lineOffset + nextOffset.line)

          if (sl == stopLine) {
            sc = min(stopColumn, columnOffset + nextOffset.column)
          }
          else if (sl < stopLine) {
            sc = columnOffset + nextOffset.column
          }
        }

        traverse(section.map,
                 mappings,
                 sources,
                 sourcesContent,
                 names,
                 ignoreList,
                 offset.line + lineOffset,
                 offset.column + columnOffset,
                 sl,
                 sc,
                 lastEntry
        )
        lastEntry = mappings.lastOrNull()
      }
    }
    is FlatSourceMap -> {
      val sourcesOffset = sources.size
      sources.addAll(map.sources)
      if (map.sourcesContent != null) {
        sourcesContent.addAll(map.sourcesContent)
      }
      else {
        // pad with nulls if sourcesContent is empty
        repeat(map.sources.size) {
          sourcesContent.add(null)
        }
      }
      map.ignoreList?.forEach { ignoreList.add(sourcesOffset + it) }
      map.names?.let { names.addAll(it) }

      readMappings(map.mappings, mappings, map.names, sourcesOffset, lineOffset, columnOffset, stopLine, stopColumn, lastEntry)
    }
  }
}

private fun readMappings(
  value: CharSequence,
  mappings: MutableList<MutableEntry>,
  names: List<String>?,
  sourcesOffset: Int,
  lineOffset: Int,
  columnOffset: Int,
  stopLine: Int,
  stopColumn: Int,
  lastEntry: MutableEntry?,
) {
  if (value.isEmpty()) {
    return
  }

  var line = 0
  var column = 0
  val charIterator = CharSequenceIterator(value)
  var sourceIndex = sourcesOffset
  var sourceLine = 0
  var sourceColumn = 0
  var nameIndex = 0
  var prevEntry: MutableEntry? = lastEntry

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

    val lineI = lineOffset + line
    val cOffset = if (line == 0) columnOffset else 0
    val colI = cOffset + column

    if (lineI >= stopLine && colI >= stopColumn) {
      return
    }

    if (isSeparator(charIterator)) {
      addEntry(UnmappedEntry(lineI, colI))
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
      entry = UnnamedEntry(lineI, colI, sourceIndex, sourceLine, sourceColumn)
    }
    else {
      nameIndex += Base64VLQ.decode(charIterator)
      assert(names != null)
      entry = NamedEntry(names!![nameIndex], lineI, colI, sourceIndex, sourceLine, sourceColumn)
    }
    addEntry(entry)
  }
}

private fun isSeparator(charIterator: CharSequenceIterator): Boolean {
  if (!charIterator.hasNext()) {
    return true
  }

  val current = charIterator.peek()
  return current == ',' || current == ';'
}

// java CharacterIterator is ugly, next() impl, so, we reinvent
private class CharSequenceIterator(private val content: CharSequence) : CharIterator {
  private val length = content.length
  private var current = 0

  override fun next() = content.get(current++)

  fun peek() = content.get(current)

  override fun hasNext() = current < length
}
