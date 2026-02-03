// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables

import com.intellij.diff.util.Range

internal class ExpandedDiffIterable(
  private val myIterable: DiffIterable,
  private val myOffset1: Int,
  private val myOffset2: Int,
  length1: Int,
  length2: Int
) : ChangeDiffIterableBase(length1, length2) {
  override fun createChangeIterable(): ChangeIterable {
    return ShiftedChangeIterable(myIterable, myOffset1, myOffset2)
  }

  private class ShiftedChangeIterable(iterable: DiffIterable, private val myOffset1: Int, private val myOffset2: Int) : ChangeIterable {
    private val myIterator: Iterator<Range> = iterable.changes()

    private var myLast: Range? = null

    init {
      next()
    }

    override fun valid(): Boolean {
      return myLast != null
    }

    override fun next() {
      myLast = if (myIterator.hasNext()) myIterator.next() else null
    }

    override val start1: Int
      get() = myLast!!.start1 + myOffset1

    override val start2: Int
      get() = myLast!!.start2 + myOffset2

    override val end1: Int
      get() = myLast!!.end1 + myOffset1

    override val end2: Int
      get() = myLast!!.end2 + myOffset2
  }
}
