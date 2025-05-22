// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff

import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Algorithm for finding the longest common subsequence of two strings
 * Based on E.W. Myers / An O(ND) Difference Algorithm and Its Variations / 1986
 * O(ND) runtime, O(N) memory
 *
 *
 * Created by Anton Bannykh
 */
@ApiStatus.Internal
class MyersLCS(
  private val first: IntArray,
  private val second: IntArray,
  private val start1: Int,
  private val count1: Int,
  private val start2: Int,
  private val count2: Int,
  private val changes1: BitSet,
  private val changes2: BitSet
) {
  private val VForward: IntArray
  private val VBackward: IntArray

  constructor(first: IntArray, second: IntArray) : this(first = first,
                                                        second = second,
                                                        start1 = 0,
                                                        count1 = first.size,
                                                        start2 = 0,
                                                        count2 = second.size,
                                                        changes1 = BitSet(first.size),
                                                        changes2 = BitSet(second.size))

  init {
    changes1.set(start1, start1 + count1)
    changes2.set(start2, start2 + count2)

    val totalSequenceLength = count1 + count2
    VForward = IntArray(totalSequenceLength + 1)
    VBackward = IntArray(totalSequenceLength + 1)
  }

  /**
   * Runs O(ND) Myers algorithm where D is bound by A + B * sqrt(N)
   *
   *
   * Under certains assumptions about the distribution of the elements of the sequences the expected
   * running time of the myers algorithm is O(N + D^2). Thus under given constraints it reduces to O(N).
   */
  fun executeLinear() {
    try {
      val threshold = 20000 + 10 * sqrt((count1 + count2).toDouble()).toInt()
      execute(threshold, false)
    }
    catch (e: FilesTooBigForDiffException) {
      throw IllegalStateException(e) // should not happen
    }
  }

  fun execute() {
    try {
      execute(count1 + count2, false)
    }
    catch (e: FilesTooBigForDiffException) {
      throw IllegalStateException(e) // should not happen
    }
  }

  @Throws(FilesTooBigForDiffException::class)
  fun executeWithThreshold() {
    val threshold = max(20000 + 10 * sqrt((count1 + count2).toDouble()).toInt(),
                        DiffConfig.DELTA_THRESHOLD_SIZE)
    execute(threshold, true)
  }

  @Throws(FilesTooBigForDiffException::class)
  private fun execute(threshold: Int, throwException: Boolean) {
    if (count1 == 0 || count2 == 0) return
    execute(0, count1, 0, count2, min(threshold, count1 + count2), throwException)
  }

  //LCS( old[oldStart, oldEnd), new[newStart, newEnd) )
  @Throws(FilesTooBigForDiffException::class)
  private fun execute(
    oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int, differenceEstimate: Int,
    throwException: Boolean
  ) {
    check(oldStart <= oldEnd && newStart <= newEnd)
    if (oldStart < oldEnd && newStart < newEnd) {
      val oldLength = oldEnd - oldStart
      val newLength = newEnd - newStart
      VForward[newLength + 1] = 0
      VBackward[newLength + 1] = 0
      val halfD = (differenceEstimate + 1) / 2
      var xx: Int
      var kk: Int
      var td: Int
      td = -1
      kk = td
      xx = kk

      loop@ for (d in 0..halfD) {
        val L = newLength + max(-d, -newLength + ((d xor newLength) and 1))
        val R = newLength + min(d, oldLength - ((d xor oldLength) and 1))
        run {
          var k = L
          while (k <= R) {
            var x = if (k == L || k != R && VForward[k - 1] < VForward[k + 1]) VForward[k + 1] else VForward[k - 1] + 1
            val y = x - k + newLength
            x += commonSubsequenceLengthForward(oldStart + x, newStart + y,
                                                min(oldEnd - oldStart - x, newEnd - newStart - y))
            VForward[k] = x
            k += 2
          }
        }

        if ((oldLength - newLength) % 2 != 0) {
          var k = L
          while (k <= R) {
            if (oldLength - (d - 1) <= k && k <= oldLength + (d - 1)) {
              if (VForward[k] + VBackward[newLength + oldLength - k] >= oldLength) {
                xx = VForward[k]
                kk = k
                td = 2 * d - 1
                break@loop
              }
            }
            k += 2
          }
        }

        var k = L
        while (k <= R) {
          var x = if (k == L || k != R && VBackward[k - 1] < VBackward[k + 1]) VBackward[k + 1] else VBackward[k - 1] + 1
          val y = x - k + newLength
          x += commonSubsequenceLengthBackward(oldEnd - 1 - x, newEnd - 1 - y,
                                               min(oldEnd - oldStart - x, newEnd - newStart - y))
          VBackward[k] = x
          k += 2
        }

        if ((oldLength - newLength) % 2 == 0) {
          var k = L
          while (k <= R) {
            if (oldLength - d <= k && k <= oldLength + d) {
              if (VForward[oldLength + newLength - k] + VBackward[k] >= oldLength) {
                xx = oldLength - VBackward[k]
                kk = oldLength + newLength - k
                td = 2 * d
                break@loop
              }
            }
            k += 2
          }
        }
      }

      if (td > 1) {
        val yy = xx - kk + newLength
        val oldDiff = (td + 1) / 2
        if (0 < xx && 0 < yy) execute(oldStart, oldStart + xx, newStart, newStart + yy, oldDiff, throwException)
        if (oldStart + xx < oldEnd && newStart + yy < newEnd) execute(oldStart + xx, oldEnd, newStart + yy, newEnd, td - oldDiff, throwException)
      }
      else if (td >= 0) {
        var x = oldStart
        var y = newStart
        while (x < oldEnd && y < newEnd) {
          val commonLength = commonSubsequenceLengthForward(x, y, min(oldEnd - x, newEnd - y))
          if (commonLength > 0) {
            addUnchanged(x, y, commonLength)
            x += commonLength
            y += commonLength
          }
          else if (oldEnd - oldStart > newEnd - newStart) {
            ++x
          }
          else {
            ++y
          }
        }
      }
      else {
        //The difference is more than the given estimate
        if (throwException) throw FilesTooBigForDiffException()
      }
    }
  }

  private fun addUnchanged(start1: Int, start2: Int, count: Int) {
    changes1.set(this@MyersLCS.start1 + start1, this@MyersLCS.start1 + start1 + count, false)
    changes2.set(this@MyersLCS.start2 + start2, this@MyersLCS.start2 + start2 + count, false)
  }

  private fun commonSubsequenceLengthForward(oldIndex: Int, newIndex: Int, maxLength: Int): Int {
    var maxLength = maxLength
    var x = oldIndex
    var y = newIndex

    maxLength = min(maxLength, min(count1 - oldIndex, count2 - newIndex))
    while (x - oldIndex < maxLength && first[start1 + x] == second[start2 + y]) {
      ++x
      ++y
    }
    return x - oldIndex
  }

  private fun commonSubsequenceLengthBackward(oldIndex: Int, newIndex: Int, maxLength: Int): Int {
    var maxLength = maxLength
    var x = oldIndex
    var y = newIndex

    maxLength = min(maxLength, min(oldIndex, newIndex) + 1)
    while (oldIndex - x < maxLength && first[start1 + x] == second[start2 + y]) {
      --x
      --y
    }
    return oldIndex - x
  }

  val changes: Array<BitSet>
    get() = arrayOf(changes1, changes2)
}
