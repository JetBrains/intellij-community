// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables

import com.intellij.diff.util.Range

internal class InvertedDiffIterableWrapper(private val myIterable: DiffIterable) : DiffIterable {
  override val length1: Int
    get() = myIterable.length1

  override val length2: Int
    get() = myIterable.length2

  override fun changes(): Iterator<Range> {
    return myIterable.unchanged()
  }

  override fun unchanged(): Iterator<Range> {
    return myIterable.changes()
  }
}
