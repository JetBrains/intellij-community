// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables

import com.intellij.diff.util.Range
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class SubiterableDiffIterable
/**
 * @param myFirstIndex First range in `changed` that might affects our range.
 * This is an optimization to avoid O(changed.size) lookups of the first element for each subIterable.
 */(
  private val myChanged: List<Range>,
  private val myStart1: Int,
  private val myEnd1: Int,
  private val myStart2: Int,
  private val myEnd2: Int,
  private val myFirstIndex: Int
) : ChangeDiffIterableBase(myEnd1 - myStart1, myEnd2 - myStart2) {
  override fun createChangeIterable(): ChangeIterable {
    return SubiterableChangeIterable(myChanged, myStart1, myEnd1, myStart2, myEnd2, myFirstIndex)
  }

  private class SubiterableChangeIterable(
    private val myChanged: List<Range>,
    private val myStart1: Int,
    private val myEnd1: Int,
    private val myStart2: Int,
    private val myEnd2: Int,
    private var myIndex: Int
  ) : ChangeIterable {
    private var myLast: Range? = null

    init {
      next()
    }

    override fun valid(): Boolean {
      return myLast != null
    }

    override fun next() {
      myLast = null

      while (myIndex < myChanged.size) {
        val range = myChanged[myIndex]
        myIndex++

        if (range.end1 < myStart1 || range.end2 < myStart2) continue
        if (range.start1 > myEnd1 || range.start2 > myEnd2) break

        val newRange = Range(max(myStart1, range.start1) - myStart1, min(myEnd1, range.end1) - myStart1,
                             max(myStart2, range.start2) - myStart2, min(myEnd2, range.end2) - myStart2)
        if (newRange.isEmpty) continue

        myLast = newRange
        break
      }
    }

    override val start1: Int
      get() = myLast!!.start1

    override val start2: Int
      get() = myLast!!.start2

    override val end1: Int
      get() = myLast!!.end1

    override val end2: Int
      get() = myLast!!.end2
  }
}
