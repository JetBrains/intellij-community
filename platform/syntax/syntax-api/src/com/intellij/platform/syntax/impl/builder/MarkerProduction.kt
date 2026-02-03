// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.Logger
import com.intellij.util.fastutil.ints.IntArrayList
import com.intellij.util.fastutil.ints.isEmpty
import com.intellij.util.fastutil.ints.lastIndexOf
import fleet.util.multiplatform.linkToActual
import kotlin.math.abs
import kotlin.math.max

internal class MarkerProduction(
  private val myPool: MarkerPool,
  private val myOptionalData: MarkerOptionalData,
  private val logger: Logger,
  private val production: IntArrayList = IntArrayList(256)
) {

  val size get() = production.size

  fun isEmpty() = production.isEmpty()

  operator fun get(index: Int): Int = production[index]

  fun addBefore(marker: ProductionMarker, anchor: ProductionMarker) {
    production.add(indexOf(anchor), marker.markerId)
  }

  private fun indexOf(marker: ProductionMarker): Int {
    var idx = findLinearly(marker.markerId)
    if (idx < 0) {
      for (i in findMarkerAtLexeme(marker.getStartTokenIndex())..<production.size) {
        if (production[i] == marker.markerId) {
          idx = i
          break
        }
      }
    }
    if (idx < 0) {
      logger.error("Dropped or rolled-back marker")
    }
    return idx
  }

  private fun findLinearly(markerId: Int): Int {
    val low = max(0, (production.size - LINEAR_SEARCH_LIMIT))
    for (i in production.size - 1 downTo low) {
      if (production[i] == markerId) {
        return i
      }
    }
    return -1
  }

  private fun findMarkerAtLexeme(lexemeIndex: Int): Int {
    val i = binSearch(0, production.size - LINEAR_SEARCH_LIMIT) { mid ->
      getLexemeIndexAt(mid).compareTo(lexemeIndex)
    }
    return if (i < 0) -1 else findSameLexemeGroupStart(lexemeIndex, i)
  }

  private fun findSameLexemeGroupStart(lexemeIndex: Int, prodIndex: Int): Int {
    var prodIndex = prodIndex
    while (prodIndex > 0 && getLexemeIndexAt(prodIndex - 1) == lexemeIndex) {
      prodIndex--
    }
    return prodIndex
  }

  fun addMarker(marker: ProductionMarker) {
    production.add(marker.markerId)
  }

  fun rollbackTo(marker: ProductionMarker) {
    val idx = indexOf(marker)
    for (i in production.size - 1 downTo idx) {
      val markerId = production[i]
      if (markerId > 0) {
        val marker = myPool.get(markerId)
        myPool.freeMarker(marker)
      }
    }
    production.removeElements(idx, production.size)
  }

  fun hasErrorsAfter(marker: CompositeMarker): Boolean {
    for (i in indexOf(marker) + 1..<production.size) {
      val m = getStartMarkerAt(i)
      if (m != null && hasError(m)) return true
    }
    return false
  }

  private fun hasError(marker: ProductionMarker): Boolean {
    return marker is ErrorMarker || myOptionalData.getDoneError(marker.markerId) != null
  }

  fun dropMarker(marker: CompositeMarker) {
    if (marker.isDone) {
      production.removeAt(production.lastIndexOf(-marker.markerId))
    }
    production.removeAt(indexOf(marker))
    myPool.freeMarker(marker)
  }

  fun addDone(marker: CompositeMarker, anchorBefore: ProductionMarker?) {
    production.add(if (anchorBefore == null) production.size else indexOf(anchorBefore), -marker.markerId)
  }

  fun getMarkerAt(index: Int): ProductionMarker {
    val id = production.get(index)
    return myPool.get(if (id > 0) id else -id)
  }

  fun getStartMarkerAt(index: Int): ProductionMarker? {
    val id = production.get(index)
    return if (id > 0) myPool.get(id) else null
  }

  fun getDoneMarkerAt(index: Int): CompositeMarker? {
    val id = production.get(index)
    return if (id < 0) myPool.get(-id) as CompositeMarker? else null
  }

  fun getLexemeIndexAt(productionIndex: Int): Int {
    val id = production.get(productionIndex)
    val node = myPool.get(abs(id))
    return node.getLexemeIndex(id < 0)
  }

  fun confineMarkersToMaxLexeme(markersBefore: Int, lexemeIndex: Int) {
    for (k in markersBefore - 1 downTo 2) {
      val id = production[k]
      val marker = myPool.get(abs(id))
      val done = id < 0
      if (marker.getLexemeIndex(done) < lexemeIndex) break

      marker.setLexemeIndex(lexemeIndex, done)
    }
  }

  fun doHeavyChecksOnMarkerDone(doneMarker: CompositeMarker, anchorBefore: CompositeMarker?) {
    val idx = indexOf(doneMarker)

    var endIdx = production.size
    if (anchorBefore != null) {
      endIdx = indexOf(anchorBefore)
      if (idx > endIdx) {
        logger.error("'Before' marker precedes this one.")
      }
    }

    for (i in endIdx - 1 downTo idx + 1) {
      val item = getStartMarkerAt(i)
      if (item is CompositeMarker) {
        val otherMarker = item
        if (!otherMarker.isDone) {
          val debugAllocThis = myOptionalData.getAllocationTrace(doneMarker)
          val currentTrace = Throwable()
          if (debugAllocThis != null) {
            makeStackTraceRelative(debugAllocThis, currentTrace).printStackTrace()
          }
          val debugAllocOther = myOptionalData.getAllocationTrace(otherMarker)
          if (debugAllocOther != null) {
            makeStackTraceRelative(debugAllocOther, currentTrace).printStackTrace()
          }
          logger.error("Another not done marker added after this one. Must be done before this.")
        }
      }
    }
  }

  fun assertNoDoneMarkerAround(pivot: ProductionMarker) {
    val pivotIndex = indexOf(pivot)
    for (i in pivotIndex + 1..<production.size) {
      val m = getDoneMarkerAt(i)
      if (m != null && m.getStartTokenIndex() <= pivot.getStartTokenIndex() && indexOf(m) < pivotIndex) {
        throw AssertionError(
          "There's a marker of type '${m.getNodeType()}' that starts before and finishes after the current marker. See cause for its allocation trace.",
          myOptionalData.getAllocationTrace(m)
        )
      }
    }
  }
}

private const val LINEAR_SEARCH_LIMIT = 20

@Suppress("unused")
internal fun makeStackTraceRelative(th: Throwable, relativeTo: Throwable): Throwable = linkToActual()

/**
 * Performs binary search on the range [fromIndex, toIndex)
 * @param indexComparator a comparator which receives a middle index and returns the result of comparison of the value at this index and the goal value
 * (e.g., 0 if found, -1 if the value[middleIndex] < goal, or 1 if value[middleIndex] > goal)
 * @return index for which `indexComparator` returned 0 or `-insertionIndex-1` if wasn't found
 * @see java.util.Arrays.binarySearch
 * @see java.util.Collections.binarySearch
 */
private inline fun binSearch(fromIndex: Int, toIndex: Int, indexComparator: (Int) -> Int): Int {
  var low = fromIndex
  var high = toIndex - 1
  while (low <= high) {
    val mid = (low + high) ushr 1
    val cmp = indexComparator(mid)
    if (cmp < 0) low = mid + 1
    else if (cmp > 0) high = mid - 1
    else return mid
  }
  return -(low + 1)
}

