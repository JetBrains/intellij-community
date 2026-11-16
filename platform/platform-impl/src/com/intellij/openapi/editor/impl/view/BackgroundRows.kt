// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import org.jetbrains.annotations.ApiStatus

private const val INITIAL_CAPACITY: Int = 16

/**
 * The background rectangles of one paint, each one with the style that paints it.
 *
 * One interval is one rectangle: the row from [top] to [bottom], and the horizontal part from [start] to [end].
 * A caller adds the intervals in any order, and it can add two intervals of one style that overlap.
 * An interval that fills the line ends at the viewport edge, not at the clip edge.
 * So a narrow clip gives the same corners as a full paint.
 * [BackgroundOutline] and the painter read the union of the intervals of one style.
 */
@ApiStatus.Internal
class BackgroundRows {
  private var count = 0
  private var styleIds = IntArray(INITIAL_CAPACITY)
  private var tops = DoubleArray(INITIAL_CAPACITY)
  private var bottoms = DoubleArray(INITIAL_CAPACITY)
  private var starts = DoubleArray(INITIAL_CAPACITY)
  private var ends = DoubleArray(INITIAL_CAPACITY)

  /**
   * The number of intervals of every style.
   */
  val size: Int get() = count

  fun add(
    styleId: Int,
    top: Double,
    bottom: Double,
    start: Double,
    end: Double,
  ) {
    grow()
    styleIds[count] = styleId
    tops[count] = top
    bottoms[count] = bottom
    starts[count] = start
    ends[count] = end
    count++
  }

  fun styleId(index: Int): Int {
    return styleIds[checkIndex(index)]
  }

  fun top(index: Int): Double {
    return tops[checkIndex(index)]
  }

  fun bottom(index: Int): Double {
    return bottoms[checkIndex(index)]
  }

  fun start(index: Int): Double {
    return starts[checkIndex(index)]
  }

  fun end(index: Int): Double {
    return ends[checkIndex(index)]
  }

  private fun checkIndex(index: Int): Int {
    require(index in 0 until count) {
      "the index $index is outside the $count intervals"
    }
    return index
  }

  private fun grow() {
    if (count < styleIds.size) {
      return
    }
    val capacity = count * 2
    styleIds = styleIds.copyOf(capacity)
    tops = tops.copyOf(capacity)
    bottoms = bottoms.copyOf(capacity)
    starts = starts.copyOf(capacity)
    ends = ends.copyOf(capacity)
  }
}
