/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff.impl.incrementalMerge

import com.intellij.diff.DiffTestCase
import com.intellij.diff.comparison.ComparisonMergeUtil
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.DiffIterableUtil.fair
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.Range
import com.intellij.openapi.util.TextRange

class MergeBuilderTest : DiffTestCase() {
  fun testEqual() {
    test(1, 1, 1) {
      addLeft(TextRange(0, 1), TextRange(0, 1))
      addRight(TextRange(0, 1), TextRange(0, 1))
    }
  }

  fun testWholeConflict() {
    test(1, 2, 3) {
      addExpected(TextRange(0, 1), TextRange(0, 2), TextRange(0, 3))
    }
  }

  fun testTailInsert() {
    test(1, 1, 2) {
      val range = TextRange(0, 1)
      addLeft(range, range)
      addRight(range, range)

      addExpected(TextRange(1, 1), TextRange(1, 1), TextRange(1, 2))
    }
  }

  fun testSameInsertsConflicts1() {
    test(2, 1, 2) {
      val base = TextRange(0, 1)
      val version = TextRange(1, 2)
      addLeft(base, version)
      addRight(base, version)

      addExpected(TextRange(0, 1), TextRange(0, 0), TextRange(0, 1))
    }
  }

  fun testSameInsertsConflicts2() {
    test(2, 2, 2) {
      val base = TextRange(1, 2)
      val version = TextRange(0, 1)
      addLeft(base, version)
      addRight(base, version)

      addExpected(TextRange(0, 0), TextRange(0, 1), TextRange(0, 0))
      addExpected(TextRange(1, 2), TextRange(2, 2), TextRange(1, 2))
    }
  }

  fun testHeadInsert() {
    test(1, 1, 2) {
      val range = TextRange(0, 1)
      addRight(range, TextRange(1, 2))
      addLeft(range, range)

      addExpected(TextRange(0, 0), TextRange(0, 0), TextRange(0, 1))
    }
  }

  fun testOneSideChange() {
    test(3, 2, 2) {
      addRight(TextRange(0, 2), TextRange(0, 2))
      addLeft(TextRange(1, 2), TextRange(2, 3))

      addExpected(TextRange(0, 2), TextRange(0, 1), TextRange(0, 1))
    }
  }

  fun testNotAllignedConflict() {
    test(3, 4, 3) {
      addLeft(TextRange(1, 3), TextRange(0, 2))
      addRight(TextRange(2, 4), TextRange(1, 3))

      addExpected(TextRange(0, 1), TextRange(0, 2), TextRange(0, 1))
      addExpected(TextRange(2, 3), TextRange(3, 4), TextRange(2, 3))
    }
  }

  fun testBug() {
    test(3, 2, 1) {
      addRight(TextRange(0, 1), TextRange(0, 1))
      addLeft(TextRange(0, 2), TextRange(0, 2))

      addExpected(TextRange(1, 3), TextRange(1, 2), TextRange(1, 1))
    }
  }

  fun testMultiChanges() {
    test(10, 10, 7) {
      addLeft(TextRange(1, 8), TextRange(1, 8))
      addRight(TextRange(1, 2), TextRange(0, 1))
      addRight(TextRange(3, 4), TextRange(1, 2))
      addRight(TextRange(4, 5), TextRange(3, 4))
      addRight(TextRange(6, 7), TextRange(5, 6))
      addLeft(TextRange(9, 10), TextRange(9, 10))

      addExpected(TextRange(0, 1), TextRange(0, 1), TextRange(0, 0))
      addExpected(TextRange(2, 3), TextRange(2, 3), TextRange(1, 1))
      addExpected(TextRange(4, 4), TextRange(4, 4), TextRange(2, 3))
      addExpected(TextRange(5, 6), TextRange(5, 6), TextRange(4, 5))
      addExpected(TextRange(7, 10), TextRange(7, 10), TextRange(6, 7))
    }
  }

  fun testNoIntersection() {
    test(3, 5, 3) {
      addLeft(TextRange(0, 1), TextRange(0, 1))
      addRight(TextRange(0, 2), TextRange(0, 2))
      addLeft(TextRange(3, 5), TextRange(1, 3))
      addRight(TextRange(4, 5), TextRange(2, 3))

      addExpected(TextRange(1, 2), TextRange(1, 4), TextRange(1, 2))
    }
  }

  private fun test(leftCount: Int, baseCount: Int, rightCount: Int,
                   block: Test.() -> Unit) {
    val test = Test(leftCount, baseCount, rightCount)
    block(test)
    test.check()
  }

  private inner class Test(val leftCount: Int, val baseCount: Int, val rightCount: Int) {
    private val leftUnchanged: MutableList<Range> = mutableListOf()
    private val rightUnchanged: MutableList<Range> = mutableListOf()
    private val expected: MutableList<MergeRange> = mutableListOf()

    fun addLeft(base: TextRange, left: TextRange) {
      leftUnchanged += Range(base.startOffset, base.endOffset,
                             left.startOffset, left.endOffset)
    }

    fun addRight(base: TextRange, right: TextRange) {
      rightUnchanged += Range(base.startOffset, base.endOffset,
                              right.startOffset, right.endOffset)
    }

    fun addExpected(left: TextRange, base: TextRange, right: TextRange) {
      expected += MergeRange(left.startOffset, left.endOffset,
                             base.startOffset, base.endOffset,
                             right.startOffset, right.endOffset)
    }

    fun check() {
      val left = fair(DiffIterableUtil.createUnchanged(leftUnchanged, baseCount, leftCount))
      val right = fair(DiffIterableUtil.createUnchanged(rightUnchanged, baseCount, rightCount))
      val actual = ComparisonMergeUtil.buildSimple(left, right, CANCELLATION)

      assertEquals(expected, actual)
    }
  }
}