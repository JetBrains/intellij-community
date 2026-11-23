// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search

import com.intellij.util.text.StringSearcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LowLevelSearchUtilCacheTest {
  /**
   * The pattern overlaps itself, so an occurrence crosses every boundary of the cached range.
   */
  @Test
  fun `a request after a cached one finds the occurrences of a fresh scan`() {
    val pattern = "aba"
    val text = "abababa.aba..aba"
    val searcher = StringSearcher(pattern, true, true, false, false)
    for (firstStart in 0..text.length) {
      for (firstEnd in firstStart..text.length) {
        for (start in 0..text.length) {
          for (end in start..text.length) {
            val instance = String(text.toCharArray())
            find(instance, firstStart, firstEnd, searcher)
            val expected = (start..end - pattern.length).filter { text.startsWith(pattern, it) }
            assertEquals(expected, find(instance, start, end, searcher), "[$firstStart, $firstEnd) then [$start, $end)")
          }
        }
      }
    }
  }

  private fun find(text: CharSequence, start: Int, end: Int, searcher: StringSearcher): List<Int> {
    val found = ArrayList<Int>()
    LowLevelSearchUtil.processTexts(text, start, end, searcher) { offset ->
      found.add(offset)
      true
    }
    return found
  }
}
