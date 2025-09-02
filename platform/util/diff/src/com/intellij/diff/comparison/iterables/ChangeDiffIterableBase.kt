// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables

import com.intellij.diff.util.Range
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ChangeDiffIterableBase(override val length1: Int, override val length2: Int) : DiffIterable {
  override fun changes(): Iterator<Range> {
    return ChangedIterator(createChangeIterable())
  }

  override fun unchanged(): Iterator<Range> {
    return UnchangedIterator(createChangeIterable(), this.length1,
                             this.length2)
  }

  private class ChangedIterator(private val myIterable: ChangeIterable) : Iterator<Range> {
    override fun hasNext(): Boolean {
      return myIterable.valid()
    }

    override fun next(): Range {
      val range = Range(myIterable.start1, myIterable.end1, myIterable.start2,
                        myIterable.end2)
      myIterable.next()
      return range
    }
  }

  private class UnchangedIterator(
    private val myIterable: ChangeIterable,
    private val myLength1: Int,
    private val myLength2: Int
  ) : Iterator<Range> {
    private var lastIndex1 = 0
    private var lastIndex2 = 0

    init {
      if (myIterable.valid()) {
        if (myIterable.start1 == 0 && myIterable.start2 == 0) {
          lastIndex1 = myIterable.end1
          lastIndex2 = myIterable.end2
          myIterable.next()
        }
      }
    }

    override fun hasNext(): Boolean {
      return myIterable.valid() || (lastIndex1 != myLength1 || lastIndex2 != myLength2)
    }

    override fun next(): Range {
      if (myIterable.valid()) {
        check((myIterable.start1 - lastIndex1 != 0) || (myIterable.start2 - lastIndex2 != 0))
        val chunk = Range(lastIndex1, myIterable.start1, lastIndex2,
                          myIterable.start2)

        lastIndex1 = myIterable.end1
        lastIndex2 = myIterable.end2

        myIterable.next()

        return chunk
      }
      else {
        check((myLength1 - lastIndex1 != 0) || (myLength2 - lastIndex2 != 0))
        val chunk = Range(lastIndex1, myLength1, lastIndex2, myLength2)

        lastIndex1 = myLength1
        lastIndex2 = myLength2

        return chunk
      }
    }
  }

  protected abstract fun createChangeIterable(): ChangeIterable

  protected interface ChangeIterable {
    fun valid(): Boolean

    fun next()

    val start1: Int

    val start2: Int

    val end1: Int

    val end2: Int
  }
}
