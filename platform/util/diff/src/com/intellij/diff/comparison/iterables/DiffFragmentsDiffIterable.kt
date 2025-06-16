// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables

import com.intellij.diff.fragments.DiffFragment

internal class DiffFragmentsDiffIterable(
  private val myFragments: Collection<DiffFragment>,
  length1: Int,
  length2: Int
) : ChangeDiffIterableBase(length1, length2) {
  override fun createChangeIterable(): ChangeIterable {
    return FragmentsChangeIterable(myFragments)
  }

  private class FragmentsChangeIterable(fragments: Collection<DiffFragment>) : ChangeIterable {
    private val myIterator: Iterator<DiffFragment> = fragments.iterator()
    private var myLast: DiffFragment? = null

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
      get() = myLast!!.startOffset1

    override val start2: Int
      get() = myLast!!.startOffset2

    override val end1: Int
      get() = myLast!!.endOffset1

    override val end2: Int
      get() = myLast!!.endOffset2
  }
}
