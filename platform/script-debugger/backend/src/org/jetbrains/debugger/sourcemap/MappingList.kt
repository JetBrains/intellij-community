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

import com.intellij.openapi.editor.Document
import java.util.*

interface Mappings {
  fun get(line: Int, column: Int): MappingEntry?

  fun getNextOnTheSameLine(index: Int, skipIfColumnEquals: Boolean = true): MappingEntry?

  fun getNext(mapping: MappingEntry): MappingEntry?

  fun getNextOnTheSameLine(mapping: MappingEntry): MappingEntry? {
    val nextMapping = getNext(mapping)
    return if (nextMapping != null && getLine(nextMapping) == getLine(mapping)) nextMapping else null
  }

  fun indexOf(line: Int, column: Int): Int

  fun getByIndex(index: Int): MappingEntry

  fun getLine(mapping: MappingEntry): Int

  fun getColumn(mapping: MappingEntry): Int
}

abstract class MappingList(private val mappings: List<MappingEntry>) : Mappings {
  val size: Int
    get() = mappings.size

  protected abstract val comparator: Comparator<MappingEntry>

  override fun indexOf(line: Int, column: Int): Int {
    var low = 0
    var high = mappings.size - 1
    if (getLine(mappings[low]) > line || getLine(mappings[high]) < line) {
      return -1
    }

    while (low <= high) {
      val middle = (low + high).ushr(1)
      val mapping = mappings[middle]
      val mappingLine = getLine(mapping)
      if (line == mappingLine) {
        if (column == getColumn(mapping)) {
          // find first
          var firstIndex = middle
          while (firstIndex > 0) {
            val prevMapping = mappings[firstIndex - 1]
            if (getLine(prevMapping) == line && getColumn(prevMapping) == column) {
              firstIndex--
            }
            else {
              break
            }
          }
          return firstIndex
        }
        else if (column < getColumn(mapping)) {
          if (column == 0 || column == -1) {
            // find first
            var firstIndex = middle
            while (firstIndex > 0 && getLine(mappings[firstIndex - 1]) == line) {
              firstIndex--
            }
            return firstIndex
          }

          if (middle == 0) {
            return -1
          }

          val prevMapping = mappings[middle - 1]
          when {
            line != getLine(prevMapping) -> return -1
            column >= getColumn(prevMapping) -> return middle - 1
            else -> high = middle - 1
          }
        }
        else {
          // https://code.google.com/p/google-web-toolkit/issues/detail?id=9103
          // We skipIfColumnEquals because GWT has two entries - source position equals, but generated no. We must use first entry (at least, in case of GWT it is correct)
          val nextMapping = getNextOnTheSameLine(middle)
          if (nextMapping == null) {
            return middle
          }
          else {
            low = middle + 1
          }
        }
      }
      else if (line > mappingLine) {
        low = middle + 1
      }
      else {
        high = middle - 1
      }
    }

    return -1
  }

  // todo honor Google Chrome bug related to paused location
  override fun get(line: Int, column: Int) = mappings.getOrNull(indexOf(line, column))

  private fun getNext(index: Int) = mappings.getOrNull(index + 1)

  override fun getNext(mapping: MappingEntry): MappingEntry? {
    var index = mappings.binarySearch(mapping, comparator)
    if (index < 0) {
      return null
    }
    index++

    var result: MappingEntry?
    do {
      result = mappings.getOrNull(index++)
    }
    while (mapping === result)
    return result
  }

  override fun getNextOnTheSameLine(index: Int, skipIfColumnEquals: Boolean): MappingEntry? {
    var nextMapping = getNext(index) ?: return null

    val mapping = getByIndex(index)
    if (getLine(nextMapping) != getLine(mapping)) {
      return null
    }

    if (skipIfColumnEquals) {
      var i = index
      // several generated segments can point to one source segment, so, in mapping list ordered by source, could be several mappings equal in terms of source position
      while (getColumn(nextMapping) == getColumn(mapping)) {
        nextMapping = getNextOnTheSameLine(++i, false) ?: return null
      }
    }

    return nextMapping
  }

  fun getEndOffset(mapping: MappingEntry, lineStartOffset: Int, document: Document): Int {
    val nextMapping = getNextOnTheSameLine(Collections.binarySearch(mappings, mapping, comparator))
    return if (nextMapping == null) document.getLineEndOffset(getLine(mapping)) else lineStartOffset + getColumn(nextMapping)
  }

  override fun getByIndex(index: Int) = mappings.get(index)

  // entries will be processed in this list order
  fun processMappingsInLine(line: Int, entryProcessor: MappingsProcessorInLine): Boolean {
    var low = 0
    var high = mappings.size - 1
    while (low <= high) {
      val middle = (low + high).ushr(1)
      val mapping = mappings.get(middle)
      val mappingLine = getLine(mapping)
      when {
        line == mappingLine -> {
          // find first
          var firstIndex = middle
          while (firstIndex > 0 && getLine(mappings.get(firstIndex - 1)) == line) {
            firstIndex--
          }

          var entry: MappingEntry? = mappings.get(firstIndex)
          do {
            var nextEntry: MappingEntry? = if (++firstIndex < mappings.size) mappings.get(firstIndex) else null
            if (nextEntry != null && getLine(nextEntry) != line) {
              nextEntry = null
            }

            if (!entryProcessor.process(entry!!, nextEntry)) {
              return true
            }

            entry = nextEntry
          }
          while (entry != null)
          return true
        }
        line > mappingLine -> low = middle + 1
        else -> high = middle - 1
      }
    }
    return false
  }
}

interface MappingsProcessorInLine {
  fun process(entry: MappingEntry, nextEntry: MappingEntry?): Boolean
}
