// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.impl.util.MutableBitSet
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

internal class BitSetTest {
  @Test
  fun `test empty`() {
    val set = MutableBitSet()
    assertTrue(!set.contains(0))
    assertTrue(!set.contains(1))
    assertTrue(!set.contains(2))
  }

  @Test
  fun `test add and remove`() {
    for (i in 0..1000) {
      try {
        val set = MutableBitSet()
        set.add(i)
        assertTrue(set.contains(i))
        assertTrue(!set.contains(i + 1))
        if (i > 0) {
          assertTrue(!set.contains(i - 1))
        }

        set.remove(i)
        assertTrue(!set.contains(i))
      }
      catch (e: Throwable) {
        throw RuntimeException("Failed at $i", e)
      }
    }
  }

  @Test
  fun `test add many`() {
    val set = MutableBitSet()
    for (i in 0..1000) {
      set.add(i)
    }

    for (i in 0..1000) {
      assertTrue(set.contains(i), "Not contains $i")
    }

    assertTrue(!set.contains(1001))

    set.remove(500)
    assertTrue(!set.contains(500))
  }
}