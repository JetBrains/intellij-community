// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.codeInsight.actions.ChangedRangesShifter
import com.intellij.diff.DiffTestCase
import com.intellij.diff.comparison.ByCharRt
import com.intellij.diff.comparison.CancellationChecker
import com.intellij.diff.util.Range
import junit.framework.TestCase

class ChangedRangesShifterTest : TestCase() {

  fun testEmptyNoChanges() {
    val a = "abcdef"
    val b = "abcdef"
    val c = "abcdef"
    val actual = shift(a, b, c)
    DiffTestCase.assertEquals(emptyList<Range>(), actual)
  }

  fun testInsertionPropagates() {
    val a = "abcde"
    val b = "abXcde"
    val c = "abXcYde"
    val actual = shift(a, b, c)
    val expected = listOf(Range(2, 2, 2, 3))
    DiffTestCase.assertEquals(expected, actual)
  }

  fun testDeletionThenOffsetShift() {
    val a = "abcdEFgh"
    val b = "abcdgh"
    val c = "aXYbcdgh"
    val actual = shift(a, b, c)
    val expected = listOf(Range(4, 6, 6, 6))
    DiffTestCase.assertEquals(expected, actual)
  }

  fun testAdjacentChangesExtendTarget() {
    val a = "abcd"
    val b = "aXcd"
    val c = "aYZd"
    val actual = shift(a, b, c)
    val expected = listOf(Range(1, 3, 1, 3))
    DiffTestCase.assertEquals(expected, actual)
  }

  fun testChangeFromLatestIgnored() {
    val a = "abcde"
    val b = "aXcde"
    val c = "aXcYe"
    val actual = shift(a, b, c)
    val expected = listOf(Range(1, 2, 1, 2))
    DiffTestCase.assertEquals(expected, actual)
  }

  fun testInsertionErasure() {
    val a = "abcde"
    val b = "aXbcde"
    val c = "abcYe"
    val actual = shift(a, b, c)
    val expected = listOf<Range>()
    DiffTestCase.assertEquals(expected, actual)
  }

  fun testModificationErasure() {
    val a = "abcde"
    val b = "aXcde"
    val c = "abcYe"
    val actual = shift(a, b, c)
    val expected = listOf(Range(1, 2, 1, 2))
    DiffTestCase.assertEquals(expected, actual)
  }

  private fun shift(text1: String, text2: String, text3: String): List<Range> {
    val changes1 = ByCharRt.compare(text1, text2, CancellationChecker.EMPTY)
    val changes2 = ByCharRt.compare(text2, text3, CancellationChecker.EMPTY)
    val shifter = ChangedRangesShifter()
    return shifter.execute(changes1, changes2)
  }
}
