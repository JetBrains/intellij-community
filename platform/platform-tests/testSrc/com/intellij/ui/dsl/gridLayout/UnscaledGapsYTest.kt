// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import org.junit.Test
import kotlin.test.assertTrue

class UnscaledGapsYTest {

  @Test
  fun testCopy() {
    val gaps = UnscaledGapsY(bottom = 10)
    assertTrue(equals(gaps, UnscaledGapsY(0, 10)))

    val gaps2 = gaps.copy(top = 1).copy(bottom = 3)
    assertTrue(equals(gaps2, UnscaledGapsY(1, 3)))
  }

  private fun equals(gaps1: UnscaledGapsY, gaps2: UnscaledGapsY): Boolean {
    return gaps1.top == gaps2.top && gaps1.bottom == gaps2.bottom
  }
}
