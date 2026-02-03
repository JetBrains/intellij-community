// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff

import com.intellij.util.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class UniqueLCS internal constructor(
  private val first: IntArray,
  private val second: IntArray,
  private val start1: Int,
  private val count1: Int,
  private val start2: Int,
  private val count2: Int
) {
  constructor(first: IntArray, second: IntArray) : this(first = first,
                                                        second = second,
                                                        start1 = 0,
                                                        count1 = first.size,
                                                        start2 = 0,
                                                        count2 = second.size)

  fun execute(): Array<IntArray>? {
    // map: key -> (offset1 + 1)
    // match: offset1 -> (offset2 + 1)
    val map = Int2ObjectOpenHashMap<Int>(count1 + count2)
    val match = IntArray(count1)

    for (i in 0..<count1) {
      val index = start1 + i
      val value = map[first[index]] ?: 0

      if (value == -1) continue
      if (value == 0) {
        map.put(first[index], i + 1)
      }
      else {
        map.put(first[index], -1)
      }
    }

    var count = 0
    for (i in 0..<count2) {
      val index = start2 + i
      val value = map[second[index]] ?: 0

      if (value == 0 || value == -1) continue
      if (match[value - 1] == 0) {
        match[value - 1] = i + 1
        count++
      }
      else {
        match[value - 1] = 0
        map.put(second[index], -1)
        count--
      }
    }

    if (count == 0) {
      return null
    }

    // Largest increasing subsequence on unique elements
    val sequence = IntArray(count)
    val lastElement = IntArray(count)
    val predecessor = IntArray(count1)

    var length = 0
    for (i in 0..<count1) {
      if (match[i] == 0) continue

      val j = binarySearch(sequence, match[i], length)
      if (j == length || match[i] < sequence[j]) {
        sequence[j] = match[i]
        lastElement[j] = i
        predecessor[i] = if (j > 0) lastElement[j - 1] else -1
        if (j == length) {
          length++
        }
      }
    }

    val ret = arrayOf(IntArray(length), IntArray(length))

    var i = length - 1
    var curr = lastElement[length - 1]
    while (curr != -1) {
      ret[0][i] = curr
      ret[1][i] = match[curr] - 1
      i--
      curr = predecessor[curr]
    }

    return ret
  }

  companion object {
    // find max i: a[i] < val
    // return i + 1
    // assert a[i] != val
    private fun binarySearch(sequence: IntArray, value: Int, length: Int): Int {
      val i = sequence.binarySearch(value, 0, length)
      check(i < 0)
      return -i - 1
    }
  }
}
