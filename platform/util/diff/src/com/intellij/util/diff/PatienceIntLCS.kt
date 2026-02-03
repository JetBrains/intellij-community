// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class PatienceIntLCS @VisibleForTesting constructor(
  private val first: IntArray,
  private val second: IntArray,
  private val start1: Int,
  private val count1: Int,
  private val start2: Int,
  private val count2: Int,
  private val changes1: BitSet,
  private val changes2: BitSet
) {
  constructor(first: IntArray, second: IntArray) : this(first = first,
                                                        second = second,
                                                        start1 = 0,
                                                        count1 = first.size,
                                                        start2 = 0,
                                                        count2 = second.size,
                                                        changes1 = BitSet(first.size),
                                                        changes2 = BitSet(second.size))

  @JvmOverloads
  @Throws(FilesTooBigForDiffException::class)
  fun execute(failOnSmallReduction: Boolean = false) {
    val thresholdCheckCounter = if (failOnSmallReduction) 2 else -1
    execute(start1, count1, start2, count2, thresholdCheckCounter)
  }

  @Throws(FilesTooBigForDiffException::class)
  private fun execute(start1: Int, count1: Int, start2: Int, count2: Int, thresholdCheckCounter: Int) {
    var start1 = start1
    var count1 = count1
    var start2 = start2
    var count2 = count2
    var thresholdCheckCounter = thresholdCheckCounter
    if (count1 == 0 && count2 == 0) {
      return
    }

    if (count1 == 0 || count2 == 0) {
      addChange(start1, count1, start2, count2)
      return
    }

    val startOffset = matchForward(start1, count1, start2, count2)
    start1 += startOffset
    start2 += startOffset
    count1 -= startOffset
    count2 -= startOffset

    val endOffset = matchBackward(start1, count1, start2, count2)
    count1 -= endOffset
    count2 -= endOffset

    if (count1 == 0 || count2 == 0) {
      addChange(start1, count1, start2, count2)
    }
    else {
      if (thresholdCheckCounter == 0) checkReduction(count1, count2)
      thresholdCheckCounter = max(-1, thresholdCheckCounter - 1)

      val uniqueLCS = UniqueLCS(first, second, start1, count1, start2, count2)
      val matching = uniqueLCS.execute()

      if (matching == null) {
        if (thresholdCheckCounter >= 0) checkReduction(count1, count2)
        val intLCS = MyersLCS(first, second, start1, count1, start2, count2, changes1, changes2)
        intLCS.executeLinear()
      }
      else {
        var s1: Int
        var s2: Int
        var c1: Int
        var c2: Int
        val matched = matching[0].size
        check(matched > 0)

        c1 = matching[0][0]
        c2 = matching[1][0]

        execute(start1, c1, start2, c2, thresholdCheckCounter)

        for (i in 1..<matching[0].size) {
          s1 = matching[0][i - 1] + 1
          s2 = matching[1][i - 1] + 1

          c1 = matching[0][i] - s1
          c2 = matching[1][i] - s2

          if (c1 > 0 || c2 > 0) {
            execute(start1 + s1, c1, start2 + s2, c2, thresholdCheckCounter)
          }
        }

        if (matching[0][matched - 1] == count1 - 1) {
          s1 = count1 - 1
          c1 = 0
        }
        else {
          s1 = matching[0][matched - 1] + 1
          c1 = count1 - s1
        }
        if (matching[1][matched - 1] == count2 - 1) {
          s2 = count2 - 1
          c2 = 0
        }
        else {
          s2 = matching[1][matched - 1] + 1
          c2 = count2 - s2
        }

        execute(start1 + s1, c1, start2 + s2, c2, thresholdCheckCounter)
      }
    }
  }

  private fun matchForward(start1: Int, count1: Int, start2: Int, count2: Int): Int {
    val size = min(count1, count2)
    var idx = 0
    for (i in 0..<size) {
      if (first[start1 + i] != second[start2 + i]) break
      ++idx
    }
    return idx
  }

  private fun matchBackward(start1: Int, count1: Int, start2: Int, count2: Int): Int {
    val size = min(count1, count2)
    var idx = 0
    for (i in 1..size) {
      if (first[start1 + count1 - i] != second[start2 + count2 - i]) break
      ++idx
    }
    return idx
  }

  private fun addChange(start1: Int, count1: Int, start2: Int, count2: Int) {
    changes1.set(start1, start1 + count1)
    changes2.set(start2, start2 + count2)
  }

  val changes: Array<BitSet>
    get() = arrayOf(changes1, changes2)

  @Throws(FilesTooBigForDiffException::class)
  private fun checkReduction(count1: Int, count2: Int) {
    if (count1 * 2 < this@PatienceIntLCS.count1) return
    if (count2 * 2 < this@PatienceIntLCS.count2) return
    throw FilesTooBigForDiffException()
  }
}
