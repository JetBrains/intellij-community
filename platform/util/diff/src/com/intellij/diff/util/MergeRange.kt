// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import kotlin.jvm.JvmField

open class MergeRange(
  @JvmField val start1: Int,
  @JvmField val end1: Int,
  @JvmField val start2: Int,
  @JvmField val end2: Int,
  @JvmField val start3: Int,
  @JvmField val end3: Int
) {
  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || this::class != o::class) return false

    val range = o as MergeRange

    if (start1 != range.start1) return false
    if (end1 != range.end1) return false
    if (start2 != range.start2) return false
    if (end2 != range.end2) return false
    if (start3 != range.start3) return false
    if (end3 != range.end3) return false

    return true
  }

  override fun hashCode(): Int {
    var result = start1
    result = 31 * result + end1
    result = 31 * result + start2
    result = 31 * result + end2
    result = 31 * result + start3
    result = 31 * result + end3
    return result
  }

  override fun toString(): String {
    return "[$start1, $end1) - [$start2, $end2) - [$start3, $end3)"
  }

  open val isEmpty: Boolean
    get() = start1 == end1 && start2 == end2 && start3 == end3
}
