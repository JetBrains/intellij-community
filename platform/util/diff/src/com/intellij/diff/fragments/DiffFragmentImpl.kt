// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

import org.jetbrains.annotations.NonNls

open class DiffFragmentImpl(
  override val startOffset1: Int,
  override val endOffset1: Int,
  override val startOffset2: Int,
  override val endOffset2: Int,
) : DiffFragment {
  init {
    require(startOffset1 != endOffset1 || startOffset2 != endOffset2) { "DiffFragmentImpl should not be empty: " + toString() }
    require(startOffset1 <= endOffset1 && startOffset2 <= endOffset2) { "DiffFragmentImpl is invalid: " + toString() }
  }

  final override fun equals(o: Any?): Boolean {
    if (o !is DiffFragmentImpl) return false

    val fragment = o
    return startOffset1 == fragment.startOffset1 &&
           endOffset1 == fragment.endOffset1 &&
           startOffset2 == fragment.startOffset2 &&
           endOffset2 == fragment.endOffset2
  }

  override fun hashCode(): Int {
    var result: Int = startOffset1
    result = 31 * result + endOffset1
    result = 31 * result + startOffset2
    result = 31 * result + endOffset2
    return result
  }

  override fun toString(): @NonNls String {
    return "DiffFragmentImpl [${startOffset1}, ${endOffset1}) - [${startOffset2}, ${endOffset2})"
  }
}
