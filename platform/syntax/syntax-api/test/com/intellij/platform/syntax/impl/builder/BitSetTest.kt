// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import org.junit.jupiter.api.Test

internal class BitSetTest {
  @Test
  fun `test empty`() {
    val set = MarkerOptionalData()
    assert(!set.contains(0))
    assert(!set.contains(1))
    assert(!set.contains(2))
  }

  @Test
  fun `test add and remove`() {
    for (i in 0..1000) {
      try {
        val set = MarkerOptionalData()
        set.add(i)
        assert(set.contains(i))
        assert(!set.contains(i + 1))
        if (i > 0) {
          assert(!set.contains(i - 1))
        }

        set.remove(i)
        assert(!set.contains(i))
      }
      catch (e: Throwable) {
        throw RuntimeException("Failed at $i", e)
      }
    }
  }

  @Test
  fun `test add many`() {
    val set = MarkerOptionalData()
    for (i in 0..1000) {
      set.add(i)
    }

    for (i in 0..1000) {
      assert(set.contains(i)) { "Not contains $i"}
    }

    assert(!set.contains(1001))

    set.remove(500)
    assert(!set.contains(500))
  }
}