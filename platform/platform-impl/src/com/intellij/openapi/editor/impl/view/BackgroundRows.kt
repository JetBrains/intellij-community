// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import org.jetbrains.annotations.ApiStatus

private const val INITIAL_CAPACITY: Int = 16

/**
 * The background rectangles of one paint, each one with the style that paints it.
 *
 * One interval is one rectangle: the row from [top] to [bottom], and the horizontal part from [start] to [end].
 * A caller adds the intervals in any order, and it can add two intervals of one style that overlap.
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
  private var flags = IntArray(INITIAL_CAPACITY)

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
    flags: Int,
  ) {
    grow()
    styleIds[count] = styleId
    tops[count] = top
    bottoms[count] = bottom
    starts[count] = start
    ends[count] = end
    this.flags[count] = flags
    count++
  }

  fun styleId(index: Int): Int = styleIds[checkIndex(index)]

  fun top(index: Int): Double = tops[checkIndex(index)]

  fun bottom(index: Int): Double = bottoms[checkIndex(index)]

  fun start(index: Int): Double = starts[checkIndex(index)]

  fun end(index: Int): Double = ends[checkIndex(index)]

  /**
   * Says that the interval ends at the fill edge of the paint.
   */
  fun isFillEdge(index: Int): Boolean = flags[checkIndex(index)] and FILL_EDGE != 0

  private fun checkIndex(index: Int): Int {
    require(index in 0 until count) { "the index $index is outside the $count intervals" }
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
    flags = flags.copyOf(capacity)
  }

  companion object {
    /**
     * The flag of an interval that ends at the fill edge of the paint, which is a cut of the clip.
     */
    const val FILL_EDGE: Int = 1
  }
}
