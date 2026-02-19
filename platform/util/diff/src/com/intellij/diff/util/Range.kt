// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import kotlin.jvm.JvmField

/**
 * Stores half-open intervals [start, end).
 */
class Range(start1: Int, end1: Int, start2: Int, end2: Int) {
  @JvmField
  val start1: Int
  @JvmField
  val end1: Int
  @JvmField
  val start2: Int
  @JvmField
  val end2: Int

  init {
    check(start1 <= end1 && start2 <= end2) { "[$start1, $end1, $start2, $end2]" }
    this.start1 = start1
    this.end1 = end1
    this.start2 = start2
    this.end2 = end2
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || this::class != o::class) return false

    val range = o as Range

    if (start1 != range.start1) return false
    if (end1 != range.end1) return false
    if (start2 != range.start2) return false
    if (end2 != range.end2) return false

    return true
  }

  override fun hashCode(): Int {
    var result = start1
    result = 31 * result + end1
    result = 31 * result + start2
    result = 31 * result + end2
    return result
  }

  override fun toString(): String {
    return "[$start1, $end1) - [$start2, $end2)"
  }

  val isEmpty: Boolean
    get() = start1 == end1 && start2 == end2
}
