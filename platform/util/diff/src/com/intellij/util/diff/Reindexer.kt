// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff

import com.intellij.util.fastutil.ints.IntArrayList
import com.intellij.util.fastutil.ints.toArray
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class Reindexer {
  private val myOldIndices = arrayOfNulls<IntArray>(2)
  private val myOriginalLengths = intArrayOf(-1, -1)
  private val myDiscardedLengths = intArrayOf(-1, -1)

  fun discardUnique(ints1: IntArray, ints2: IntArray): Array<IntArray> {
    val discarded1 = discard(ints2, ints1, 0)
    return arrayOf(discarded1, discard(discarded1, ints2, 1))
  }

  @TestOnly
  fun idInit(length1: Int, length2: Int) {
    myOriginalLengths[0] = length1
    myOriginalLengths[1] = length2
    myDiscardedLengths[0] = length1
    myDiscardedLengths[1] = length2
    for (j in 0..1) {
      val originalLength = myOriginalLengths[j]
      myOldIndices[j] = IntArray(originalLength) { it }
    }
  }

  @TestOnly
  fun restoreIndex(index: Int, array: Int): Int {
    return myOldIndices[array]!![index]
  }

  private fun discard(needed: IntArray, toDiscard: IntArray, arrayIndex: Int): IntArray {
    myOriginalLengths[arrayIndex] = toDiscard.size
    val sorted1: IntArray = createSorted(needed)
    val discarded = IntArrayList(toDiscard.size)
    val oldIndices = IntArrayList(toDiscard.size)
    for (i in toDiscard.indices) {
      val index = toDiscard[i]
      if (sorted1.binarySearch(index) >= 0) {
        discarded.add(index)
        oldIndices.add(i)
      }
    }
    myOldIndices[arrayIndex] = oldIndices.toArray()
    myDiscardedLengths[arrayIndex] = discarded.size
    return discarded.toArray()
  }

  fun reindex(discardedChanges: Array<BitSet>, builder: LCSBuilder) {
    val changes1: BitSet
    val changes2: BitSet

    if (myDiscardedLengths[0] == myOriginalLengths[0] && myDiscardedLengths[1] == myOriginalLengths[1]) {
      changes1 = discardedChanges[0]
      changes2 = discardedChanges[1]
    }
    else {
      changes1 = BitSet(myOriginalLengths[0])
      changes2 = BitSet(myOriginalLengths[1])
      var x = 0
      var y = 0
      while (x < myDiscardedLengths[0] || y < myDiscardedLengths[1]) {
        if ((x < myDiscardedLengths[0] && y < myDiscardedLengths[1]) && !discardedChanges[0][x] && !discardedChanges[1][y]) {
          x = increment(myOldIndices[0]!!, x, changes1, myOriginalLengths[0])
          y = increment(myOldIndices[1]!!, y, changes2, myOriginalLengths[1])
        }
        else if (discardedChanges[0][x]) {
          changes1[getOriginal(myOldIndices[0]!!, x)] = true
          x = increment(myOldIndices[0]!!, x, changes1, myOriginalLengths[0])
        }
        else if (discardedChanges[1][y]) {
          changes2[getOriginal(myOldIndices[1]!!, y)] = true
          y = increment(myOldIndices[1]!!, y, changes2, myOriginalLengths[1])
        }
      }
      if (myDiscardedLengths[0] == 0) {
        changes1.set(0, myOriginalLengths[0])
      }
      else {
        changes1.set(0, myOldIndices[0]!![0])
      }
      if (myDiscardedLengths[1] == 0) {
        changes2.set(0, myOriginalLengths[1])
      }
      else {
        changes2.set(0, myOldIndices[1]!![0])
      }
    }

    var x = 0
    var y = 0
    while (x < myOriginalLengths[0] && y < myOriginalLengths[1]) {
      val startX = x
      while (x < myOriginalLengths[0] && y < myOriginalLengths[1] && !changes1[x] && !changes2[y]) {
        x++
        y++
      }
      if (x > startX) builder.addEqual(x - startX)
      var dx = 0
      var dy = 0
      while (x < myOriginalLengths[0] && changes1[x]) {
        dx++
        x++
      }
      while (y < myOriginalLengths[1] && changes2[y]) {
        dy++
        y++
      }
      if (dx != 0 || dy != 0) builder.addChange(dx, dy)
    }
    if (x != myOriginalLengths[0] || y != myOriginalLengths[1]) builder.addChange(myOriginalLengths[0] - x, myOriginalLengths[1] - y)
  }

  companion object {
    private fun createSorted(ints1: IntArray): IntArray {
      val sorted1 = ints1.copyOf()
      sorted1.sort()
      return sorted1
    }

    private fun getOriginal(indexes: IntArray, i: Int): Int {
      return indexes[i]
    }

    private fun increment(indexes: IntArray, i: Int, set: BitSet, length: Int): Int {
      if (i + 1 < indexes.size) {
        set.set(indexes[i] + 1, indexes[i + 1])
      }
      else {
        set.set(indexes[i] + 1, length)
      }
      return i + 1
    }
  }
}

internal fun IntArray.binarySearch(element: Int, fromIndex: Int = 0, toIndex: Int = size): Int {
  rangeCheck(size, fromIndex, toIndex)

  var l = fromIndex
  var r = toIndex - 1

  while (l <= r) {
    val m = (l + r) / 2
    val midElement = get(m)

    if (midElement < element) {
      l = m + 1
    } else if (midElement > element) {
      r = m - 1
    } else {
      return m
    }
  }
  return -(l + 1)
}

private fun rangeCheck(size: Int, fromIndex: Int, toIndex: Int) {
  when {
    fromIndex > toIndex -> throw IllegalArgumentException("fromIndex ($fromIndex) is greater than toIndex ($toIndex).")
    fromIndex < 0 -> throw IndexOutOfBoundsException("fromIndex ($fromIndex) is less than zero.")
    toIndex > size -> throw IndexOutOfBoundsException("toIndex ($toIndex) is greater than size ($size).")
  }
}
