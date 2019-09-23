// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    if (mappings.isEmpty() || getLine(mappings[low]) > line || getLine(mappings[high]) < line) {
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
  override fun get(line: Int, column: Int): MappingEntry? = mappings.getOrNull(indexOf(line, column))

  private fun getNext(index: Int) = mappings.getOrNull(index + 1)

  override fun getNext(mapping: MappingEntry): MappingEntry? {
    if (comparator == MAPPING_COMPARATOR_BY_GENERATED_POSITION) {
      return mapping.nextGenerated
    }

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

  override fun getByIndex(index: Int): MappingEntry = mappings[index]

  // entries will be iterated in this list order
  fun getMappingsInLine(line: Int): Iterable<MappingEntry> {
    var low = 0
    var high = mappings.size - 1
    var middle: Int = -1

    loop@ while (low <= high) {
      middle = (low + high).ushr(1)
      val mapping = mappings[middle]
      val mappingLine = getLine(mapping)
      when {
        line == mappingLine -> {
          break@loop
        }
        line > mappingLine -> low = middle + 1
        else -> high = middle - 1
      }
    }

    if (middle == -1) {
      return emptyList()
    }

    var firstIndex = middle
    while (firstIndex > 0 && getLine(mappings[firstIndex - 1]) == line) {
      firstIndex--
    }

    var lastIndex = middle
    while (lastIndex < mappings.size - 1 && getLine(mappings[lastIndex + 1]) == line) {
      lastIndex++
    }

    return mappings.subList(firstIndex, lastIndex + 1)
  }

  fun processMappingsInLine(line: Int, entryProcessor: MappingsProcessorInLine): Boolean {
    val mappingsInLine = getMappingsInLine(line)
    return entryProcessor.processIterable(mappingsInLine)
  }
}

interface MappingsProcessorInLine {

  fun process(entry: MappingEntry, nextEntry: MappingEntry?): Boolean

  fun processIterable(mappingsInLine: Iterable<MappingEntry>): Boolean {
    val iterator = mappingsInLine.iterator()
    if (!iterator.hasNext()) return false
    var prevEntry = iterator.next()
    while (iterator.hasNext()) {
      val currentEntry = iterator.next()
      if (!process(prevEntry, currentEntry)) {
        return true
      }
      prevEntry = currentEntry
    }
    process(prevEntry, null)
    return true
  }
}
