// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.event.DocumentEvent

/**
 * An area of the text that [EditorMarkupRepainter] repaints, from a start offset to an end offset.
 * The two offsets are packed into one [Long], so an area costs no allocation.
 * [NONE] is no area at all, so a union with it gives the other area.
 * An area of zero length is an area, see [coversText].
 * [clamp] makes every other area valid, also an area with its start after its end.
 */
@JvmInline
internal value class RepaintArea private constructor(private val packed: Long) {
  /**
   * The area from [start] to [end].
   * The start goes into the high 32 bits, and the end goes into the low 32 bits.
   */
  constructor(start: Int, end: Int) : this((start.toLong() shl 32) or (end.toLong() and 0xFFFF_FFFFL))

  val start: Int
    get() = (packed shr 32).toInt()

  val end: Int
    get() = packed.toInt()

  fun isNone(): Boolean {
    return this == NONE
  }

  /**
   * Whether the area covers at least one character.
   */
  fun coversText(): Boolean {
    return start < end
  }

  fun union(other: RepaintArea): RepaintArea {
    return RepaintArea(minOf(start, other.start), maxOf(end, other.end))
  }

  /**
   * Returns this area in the text after the [change].
   * An offset in the replaced text moves to the edge of the new text, so the area also covers the new text.
   */
  fun movedBy(change: DocumentEvent): RepaintArea {
    if (isNone()) {
      return this
    }
    val movedStart = moveOffset(start, change, change.offset)
    val movedEnd = moveOffset(end, change, change.offset + change.newLength)
    return RepaintArea(movedStart, movedEnd)
  }

  /**
   * Returns the part of this area in the text of length [textLength].
   * [NONE] stays [NONE].
   */
  fun clamp(textLength: Int): RepaintArea {
    if (isNone()) {
      return this
    }
    val clampedStart = start.coerceIn(0, textLength)
    val clampedEnd = end.coerceIn(clampedStart, textLength)
    return RepaintArea(clampedStart, clampedEnd)
  }

  /**
   * Moves [offset] with the text of the [change].
   * An offset in the replaced text moves to [offsetInChange].
   */
  private fun moveOffset(offset: Int, change: DocumentEvent, offsetInChange: Int): Int {
    if (offset <= change.offset) {
      return offset
    }
    val oldChangeEnd = change.offset + change.oldLength
    if (offset >= oldChangeEnd) {
      return offset + change.newLength - change.oldLength
    }
    return offsetInChange
  }

  override fun toString(): String {
    return "RepaintArea($start, $end)"
  }

  companion object {
    val NONE: RepaintArea = RepaintArea(Int.MAX_VALUE, Int.MIN_VALUE)
  }
}
