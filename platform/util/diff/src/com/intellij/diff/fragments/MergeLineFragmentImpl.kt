// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.ThreeSide

open class MergeLineFragmentImpl(
  private val myStartLine1: Int,
  private val myEndLine1: Int,
  private val myStartLine2: Int,
  private val myEndLine2: Int,
  private val myStartLine3: Int,
  private val myEndLine3: Int
) : MergeLineFragment {
  constructor(fragment: MergeLineFragment) : this(fragment.getStartLine(ThreeSide.LEFT), fragment.getEndLine(ThreeSide.LEFT),
                                                  fragment.getStartLine(ThreeSide.BASE), fragment.getEndLine(ThreeSide.BASE),
                                                  fragment.getStartLine(ThreeSide.RIGHT), fragment.getEndLine(ThreeSide.RIGHT))

  constructor(range: MergeRange) : this(range.start1, range.end1,
                                        range.start2, range.end2,
                                        range.start3, range.end3)

  override fun getStartLine(side: ThreeSide): Int {
    return side.select(myStartLine1, myStartLine2, myStartLine3)
  }

  override fun getEndLine(side: ThreeSide): Int {
    return side.select(myEndLine1, myEndLine2, myEndLine3)
  }
}
