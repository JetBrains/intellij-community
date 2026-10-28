// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@UsePMarkerImplementation
internal class MarkerSpecTest {
  @Test
  fun `packs all boundary flag combinations`() {
    for (flags in 0..7) {
      val left = flags and 1 != 0
      val right = flags and 2 != 0
      val sticky = flags and 4 != 0
      val spec = MarkerSpec.create(left, right, sticky)

      assertEquals(flags.toByte(), spec)
      assertEquals(left, MarkerSpec.isGreedyToLeft(spec))
      assertEquals(right, MarkerSpec.isGreedyToRight(spec))
      assertEquals(sticky, MarkerSpec.isStickingToRight(spec))
    }
  }

  @Test
  fun `with methods update only the selected flag`() {
    for (flags in 0..7) {
      val left = flags and 1 != 0
      val right = flags and 2 != 0
      val sticky = flags and 4 != 0
      val spec = MarkerSpec.create(left, right, sticky)

      for (value in listOf(false, true)) {
        assertEquals(MarkerSpec.create(value, right, sticky), MarkerSpec.withGreedyToLeft(spec, value))
        assertEquals(MarkerSpec.create(left, value, sticky), MarkerSpec.withGreedyToRight(spec, value))
        assertEquals(MarkerSpec.create(left, right, value), MarkerSpec.withStickyToRight(spec, value))
      }
    }
  }

  @Test
  fun `sticking to right defaults to false`() {
    for (left in listOf(false, true)) {
      for (right in listOf(false, true)) {
        assertEquals(MarkerSpec.create(left, right, false), MarkerSpec.create(left, right))
      }
    }
  }
}
