// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.ThreeSide

open class MergeWordFragmentImpl(
  private val myStartOffset1: Int,
  private val myEndOffset1: Int,
  private val myStartOffset2: Int,
  private val myEndOffset2: Int,
  private val myStartOffset3: Int,
  private val myEndOffset3: Int
) : MergeWordFragment {
  constructor(range: MergeRange) : this(range.start1, range.end1,
                                        range.start2, range.end2,
                                        range.start3, range.end3)

  override fun getStartOffset(side: ThreeSide): Int {
    return side.select(myStartOffset1, myStartOffset2, myStartOffset3)
  }

  override fun getEndOffset(side: ThreeSide): Int {
    return side.select(myEndOffset1, myEndOffset2, myEndOffset3)
  }
}
