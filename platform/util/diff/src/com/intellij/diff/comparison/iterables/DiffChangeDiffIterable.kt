// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables

import com.intellij.util.diff.Diff

internal class DiffChangeDiffIterable(
  private val myChange: Diff.Change?,
  length1: Int,
  length2: Int
) : ChangeDiffIterableBase(length1, length2) {
  override fun createChangeIterable(): ChangeIterable {
    return DiffChangeChangeIterable(myChange)
  }

  private class DiffChangeChangeIterable(private var myChange: Diff.Change?) : ChangeIterable {
    override fun valid(): Boolean {
      return myChange != null
    }

    override fun next() {
      myChange = myChange!!.link
    }

    override val start1: Int
      get() = myChange!!.line0

    override val start2: Int
      get() = myChange!!.line1

    override val end1: Int
      get() = myChange!!.run { line0 + deleted }

    override val end2: Int
      get() = myChange!!.run { line1 + inserted }
  }
}
