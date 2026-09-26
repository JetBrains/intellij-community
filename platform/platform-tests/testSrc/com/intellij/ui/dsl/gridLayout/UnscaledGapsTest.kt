// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import org.junit.Test
import kotlin.test.assertTrue

class UnscaledGapsTest {

  @Test
  fun testCopy() {
    val gaps = UnscaledGaps(10)
    assertTrue(equals(gaps, UnscaledGaps(10, 10, 10, 10)))

    val gaps2 = gaps.copy(top = 1).copy(left = 2).copy(bottom = 3).copy(right = 4)
    assertTrue(equals(gaps2, UnscaledGaps(1, 2, 3, 4)))
  }

  private fun equals(gaps1: UnscaledGaps, gaps2: UnscaledGaps): Boolean {
    return gaps1.top == gaps2.top && gaps1.left == gaps2.left && gaps1.bottom == gaps2.bottom && gaps1.right == gaps2.right
  }
}
