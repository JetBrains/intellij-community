// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.diff.DiffTestCase
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.checkin.StepIntersection

class IntersectionAutoTest : DiffTestCase() {
  val RUNS = 100000
  val LIST_SIZE = 25
  val RANGE_SIZE = 20

  fun testIntersection() {
    doAutoTest(System.currentTimeMillis(), RUNS) {
      val list1 = generateList(LIST_SIZE)
      val list2 = generateList(LIST_SIZE)

      val result1 = mutableListOf<Pair<TextRange, TextRange>>()
      val result2 = mutableListOf<Pair<TextRange, TextRange>>()
      StepIntersection.processIntersections(list1, list2, { it }, { it }, { it1, it2 -> result1.add(Pair(it1, it2)) })
      dumbProcessIntersections(list1, list2, { it }, { it }, { it1, it2 -> result2.add(Pair(it1, it2)) })

      if (result1 != result2) {
        println(list1.joinToString { "${it.startOffset}, ${it.endOffset}" })
        println(list2.joinToString { "${it.startOffset}, ${it.endOffset}" })
        println(result1)
        println(result2)

        assertEquals(result1, result2)
      }
    }
  }

  fun testSingleElementIntersection() {
    doAutoTest(System.currentTimeMillis(), RUNS) {
      val element1 = generateRange(0, RANGE_SIZE * LIST_SIZE, RANGE_SIZE * LIST_SIZE * 2)
      val list1 = listOf(element1)
      val list2 = generateList(LIST_SIZE)

      val result1 = mutableListOf<Pair<TextRange, TextRange>>()
      val result2 = mutableListOf<Pair<TextRange, TextRange>>()
      StepIntersection.processElementIntersections(element1, list2, { it }, { it }, { it1, it2 -> result1.add(Pair(it1, it2)) })
      dumbProcessIntersections(list1, list2, { it }, { it }, { it1, it2 -> result2.add(Pair(it1, it2)) })

      if (result1 != result2) {
        println(list1.joinToString { "${it.startOffset}, ${it.endOffset}" })
        println(list2.joinToString { "${it.startOffset}, ${it.endOffset}" })
        println(result1)
        println(result2)

        assertEquals(result1, result2)
      }
    }
  }

  private fun generateList(maxLength: Int): List<TextRange> {
    val result = mutableListOf<TextRange>()
    val length = RNG.nextInt(maxLength)

    var oldEnd = 0
    for (i in 1..length) {
      val range = generateRange(oldEnd, RANGE_SIZE, RANGE_SIZE)
      result += range
      oldEnd = range.endOffset + 1
    }
    return result
  }

  private fun generateRange(minStart: Int, maxOffset: Int, maxLength: Int): TextRange {
    val start = minStart + RNG.nextInt(maxOffset)
    val end = start + RNG.nextInt(maxLength) + 1
    return TextRange(start, end)
  }

  private fun <T, V> dumbProcessIntersections(elements1: List<T>,
                                              elements2: List<V>,
                                              convertor1: (T) -> TextRange,
                                              convertor2: (V) -> TextRange,
                                              intersectionConsumer: (T, V) -> Unit) {
    for (item1 in elements1) {
      for (item2 in elements2) {
        val range1 = convertor1(item1)
        val range2 = convertor2(item2)
        if (range1.intersects(range2)) {
          intersectionConsumer(item1, item2)
        }
      }
    }
  }
}