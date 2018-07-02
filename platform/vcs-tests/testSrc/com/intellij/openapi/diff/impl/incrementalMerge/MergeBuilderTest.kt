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

import com.intellij.idea.IdeaLogger
import com.intellij.openapi.diff.impl.highlighting.FragmentSide
import com.intellij.openapi.diff.impl.util.ContextLogger
import com.intellij.openapi.util.TextRange
import com.intellij.util.Assertion
import junit.framework.TestCase

class MergeBuilderTest : TestCase() {
  private val myMergeBuilder = MergeBuilder(ContextLogger("TEST"))
  private val CHECK = Assertion()

  fun testEqual() {
    addLeft(TextRange(0, 1), TextRange(0, 1))
    addRight(TextRange(0, 1), TextRange(0, 1))
    CHECK.empty(finish(1, 1, 1))
  }

  fun testWholeConflict() {
    CHECK.singleElement(finish(1, 2, 3),
                        fragment(TextRange(0, 1), TextRange(0, 2), TextRange(0, 3)))
  }

  fun testTailInsert() {
    val range = TextRange(0, 1)
    addLeft(range, range)
    addRight(range, range)
    CHECK.singleElement(finish(1, 1, 2),
                        fragment(TextRange(1, 1), TextRange(1, 1), TextRange(1, 2)))
  }

  fun testSameInsertsConflicts1() {
    val base = TextRange(0, 1)
    val version = TextRange(1, 2)
    addLeft(base, version)
    addRight(base, version)
    CHECK.singleElement(finish(2, 1, 2),
                        fragment(TextRange(0, 1), TextRange(0, 0), TextRange(0, 1)))
  }

  fun testSameInsertsConflicts2() {
    val base = TextRange(1, 2)
    val version = TextRange(0, 1)
    addLeft(base, version)
    addRight(base, version)
    CHECK.compareAll(
      arrayOf(fragment(TextRange(0, 0), TextRange(0, 1), TextRange(0, 0)), fragment(TextRange(1, 2), TextRange(2, 2), TextRange(1, 2))),
      finish(2, 2, 2))
  }

  fun testHeadInsert() {
    val range = TextRange(0, 1)
    addRight(range, TextRange(1, 2))
    addLeft(range, range)
    CHECK.singleElement(finish(1, 1, 2),
                        fragment(TextRange(0, 0), TextRange(0, 0), TextRange(0, 1)))
  }

  fun testOneSideChange() {
    addRight(TextRange(0, 2), TextRange(0, 2))
    addLeft(TextRange(1, 2), TextRange(2, 3))
    CHECK.singleElement(finish(3, 2, 2),
                        fragment(TextRange(0, 2), TextRange(0, 1), TextRange(0, 1)))
  }

  fun testNotAllignedConflict() {
    addLeft(TextRange(1, 3), TextRange(0, 2))
    addRight(TextRange(2, 4), TextRange(1, 3))
    CHECK.compareAll(
      arrayOf(fragment(TextRange(0, 1), TextRange(0, 2), TextRange(0, 1)), fragment(TextRange(2, 3), TextRange(3, 4), TextRange(2, 3))),
      finish(3, 4, 3))
  }

  fun testBug() {
    addRight(TextRange(0, 1), TextRange(0, 1))
    addLeft(TextRange(0, 2), TextRange(0, 2))
    CHECK.compareAll(arrayOf(fragment(TextRange(1, 3), TextRange(1, 2), TextRange(1, 1))), finish(3, 2, 1))
  }

  fun testMultiChanges() {
    addLeft(TextRange(1, 8), TextRange(1, 8))
    addRight(TextRange(1, 2), TextRange(0, 1))
    addRight(TextRange(3, 4), TextRange(1, 2))
    addRight(TextRange(4, 5), TextRange(3, 4))
    addRight(TextRange(6, 7), TextRange(5, 6))
    addLeft(TextRange(9, 10), TextRange(9, 10))
    CHECK.compareAll(
      arrayOf(fragment(TextRange(0, 1), TextRange(0, 1), TextRange(0, 0)), fragment(TextRange(2, 3), TextRange(2, 3), TextRange(1, 1)),
              fragment(TextRange(4, 4), TextRange(4, 4), TextRange(2, 3)), fragment(TextRange(5, 6), TextRange(5, 6), TextRange(4, 5)),
              fragment(TextRange(7, 10), TextRange(7, 10), TextRange(6, 7))), finish(10, 10, 7))
  }

  fun testNoIntersection() {
    addLeft(TextRange(0, 1), TextRange(0, 1))
    addRight(TextRange(0, 2), TextRange(0, 2))
    addLeft(TextRange(3, 5), TextRange(1, 3))
    addRight(TextRange(4, 5), TextRange(2, 3))
    CHECK.compareAll(arrayOf(fragment(TextRange(1, 2), TextRange(1, 4), TextRange(1, 2))), finish(3, 5, 3))
  }

  private fun fragment(left: TextRange, base: TextRange, right: TextRange): MergeFragment {
    return MergeFragment(left, base, right)
  }

  private fun addRight(base: TextRange, right: TextRange) {
    myMergeBuilder.add(base, right, FragmentSide.SIDE2)
  }

  private fun addLeft(base: TextRange, left: TextRange) {
    myMergeBuilder.add(base, left, FragmentSide.SIDE1)
  }

  private fun finish(left: Int, base: Int, right: Int): List<MergeFragment> {
    return myMergeBuilder.finish(left, base, right)
  }

  @Throws(Throwable::class)
  override fun runTest() {
    try {
      super.runTest()
    }
    finally {
      if (IdeaLogger.ourErrorsOccurred != null) throw IdeaLogger.ourErrorsOccurred
    }
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    IdeaLogger.ourErrorsOccurred = null
  }
}
