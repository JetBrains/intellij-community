// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.codeInsight.actions.ChangedRangesShifter
import com.intellij.diff.DiffTestCase
import com.intellij.diff.comparison.ByCharRt
import com.intellij.diff.comparison.CancellationChecker
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.util.diff.BitSet

class ChangedRangesShifterAutoTest : DiffTestCase() {
  val RUNS = 30000
  val MAX_LENGTH = 30

  fun testRangesShifter() {
    doAutoTest(System.currentTimeMillis(), RUNS) { debugData ->
      val text1 = generateText(MAX_LENGTH, 12, emptyList())
      val text2 = generateText(MAX_LENGTH, 12, emptyList())
      val text3 = generateText(MAX_LENGTH, 12, emptyList())

      debugData.put("Text1", textToReadableFormat(text1))
      debugData.put("Text2", textToReadableFormat(text2))
      debugData.put("Text3", textToReadableFormat(text3))

      val changes1 = ByCharRt.compare(text1, text2, CancellationChecker.EMPTY)
      val changes2 = ByCharRt.compare(text2, text3, CancellationChecker.EMPTY)
      val shifter = ChangedRangesShifter()
      val result = shifter.execute(changes1, changes2)

      val resultIterable = DiffIterableUtil.create(result, changes1.length1, changes2.length2)
      DiffIterableUtil.verify(resultIterable)

      val wereModified = BitSet()
      changes1.iterateChanges().forEach { range -> wereModified.set(range.start1, range.end1) }

      val shiftedModified = BitSet()
      resultIterable.iterateChanges().forEach { range -> shiftedModified.set(range.start1, range.end1) }

      val forgotten = BitSet(wereModified.size, wereModified.toLongArray())
      forgotten.andNot(shiftedModified)
      assertTrue(forgotten.toString(), forgotten.isEmpty)
    }
  }
}