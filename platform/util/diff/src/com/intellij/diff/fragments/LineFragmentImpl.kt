// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

import org.jetbrains.annotations.NonNls
import kotlin.jvm.JvmOverloads

open class LineFragmentImpl @JvmOverloads constructor(
  override val startLine1: Int,
  override val endLine1: Int,
  override val startLine2: Int,
  override val endLine2: Int,
  override val startOffset1: Int,
  override val endOffset1: Int,
  override val startOffset2: Int,
  override val endOffset2: Int,
  innerFragments: List<DiffFragment>? = null,
) : LineFragment {
  override val innerFragments: List<DiffFragment>? =
    dropWholeChangedFragments(innerFragments, endOffset1 - startOffset1, endOffset2 - startOffset2)

  constructor(fragment: LineFragment, fragments: List<DiffFragment>?) : this(fragment.startLine1, fragment.endLine1,
                                                                             fragment.startLine2, fragment.endLine2,
                                                                             fragment.startOffset1, fragment.endOffset1,
                                                                             fragment.startOffset2, fragment.endOffset2,
                                                                             fragments)

  init {
    require(startLine1 != endLine1 || startLine2 != endLine2) { "LineFragmentImpl should not be empty: " + toString() }
    require(startLine1 <= endLine1 &&
            startLine2 <= endLine2 &&
            startOffset1 <= endOffset1 &&
            startOffset2 <= endOffset2) {
      "LineFragmentImpl is invalid: " + toString()
    }
  }

  final override fun equals(o: Any?): Boolean {
    if (o !is LineFragmentImpl) return false

    val fragment = o
    return startLine1 == fragment.startLine1 &&
           endLine1 == fragment.endLine1 &&
           startLine2 == fragment.startLine2 &&
           endLine2 == fragment.endLine2 &&
           startOffset1 == fragment.startOffset1 &&
           endOffset1 == fragment.endOffset1 &&
           startOffset2 == fragment.startOffset2 &&
           endOffset2 == fragment.endOffset2 &&
           innerFragments == fragment.innerFragments
  }

  override fun hashCode(): Int {
    var result: Int = startLine1
    result = 31 * result + endLine1
    result = 31 * result + startLine2
    result = 31 * result + endLine2
    result = 31 * result + startOffset1
    result = 31 * result + endOffset1
    result = 31 * result + startOffset2
    result = 31 * result + endOffset2
    result = 31 * result + innerFragments.hashCode()
    return result
  }

  override fun toString(): @NonNls String {
    return "LineFragmentImpl: Lines [${startLine1}, ${endLine1}) - [${startLine2}, ${endLine2}); " +
           "Offsets [${startOffset1}, ${endOffset1}) - [${startOffset2}, ${endOffset2}); " +
           "Inner ${innerFragments?.size}"
  }
}

private fun dropWholeChangedFragments(fragments: List<DiffFragment>?, length1: Int, length2: Int): List<DiffFragment>? {
  if (fragments != null && fragments.size == 1) {
    val diffFragment = fragments[0]
    if (diffFragment.startOffset1 == 0 &&
        diffFragment.startOffset2 == 0 &&
        diffFragment.endOffset1 == length1 &&
        diffFragment.endOffset2 == length2) {
      return null
    }
  }
  return fragments
}
