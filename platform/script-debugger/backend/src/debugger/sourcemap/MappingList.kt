// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
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

@ApiStatus.Internal
abstract class MappingList(mappings: List<MappingEntry>) : Mappings {
  val size: Int
    get() = mappings.size

  private val comparator: Comparator<MappingEntry> = Comparator.comparing(::getLine).thenComparing(::getColumn)

  // optimization: generated mappings already sorted
  private val mappings: List<MappingEntry> =
    if (this is GeneratedMappingList) mappings else mappings.sortedWith(comparator).toList()

  override fun indexOf(line: Int, column: Int): Int {
    var middle = mappings.binarySearch(comparison = {
      val compareLines = getLine(it).compareTo(line)
      if (compareLines != 0) compareLines else getColumn(it).compareTo(column)
    })

    if (middle == -1) middle = 0 // use first mapping for all points before it
    else if (middle < -1) middle = -middle - 2 // previous mapping includes this point

    // if mapping is from the previous line, take the first point from the needed line
    if (getLine(mappings[middle]) < line && middle < mappings.size) {
      var lastOfEquivalent = middle
      while (lastOfEquivalent < mappings.size - 1 &&
             getLine(mappings[lastOfEquivalent]) == getLine(mappings[lastOfEquivalent + 1]) &&
             getColumn(mappings[lastOfEquivalent]) == getColumn(mappings[lastOfEquivalent + 1])) {
        lastOfEquivalent++
      }

      if (lastOfEquivalent == mappings.size - 1) return -1
      middle = lastOfEquivalent + 1
    }

    while (middle > 0 &&
           getLine(mappings[middle]) == getLine(mappings[middle - 1]) &&
           getColumn(mappings[middle]) == getColumn(mappings[middle - 1])) {
      middle--
    }

    return if (line == getLine(mappings[middle])) middle else -1
  }

  override fun get(line: Int, column: Int): MappingEntry? = mappings.getOrNull(indexOf(line, column))

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
    val nextMapping = getNextOnTheSameLine(mappings.binarySearch(mapping, comparator))
    return if (nextMapping == null) document.getLineEndOffset(getLine(mapping)) else lineStartOffset + getColumn(nextMapping)
  }

  override fun getByIndex(index: Int): MappingEntry = mappings[index]

  // entries will be iterated in this list order
  fun getMappingsInLine(line: Int): Iterable<MappingEntry> {
    val middle = mappings.binarySearchBy(line) { getLine(it) }
    if (middle < 0) {
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

internal class SourceMappingList(mappings: List<MappingEntry>) : MappingList(mappings) {

  override fun getLine(mapping: MappingEntry) = mapping.sourceLine

  override fun getColumn(mapping: MappingEntry) = mapping.sourceColumn
}

internal class GeneratedMappingList(mappings: List<MappingEntry>) : MappingList(mappings) {

  override fun getLine(mapping: MappingEntry) = mapping.generatedLine

  override fun getColumn(mapping: MappingEntry) = mapping.generatedColumn

  override fun getNext(mapping: MappingEntry): MappingEntry? = mapping.nextGenerated
}

@ApiStatus.Internal
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
