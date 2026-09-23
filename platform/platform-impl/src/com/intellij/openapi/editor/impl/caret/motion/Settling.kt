// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

@JvmInline
internal value class Settling private constructor(
  private val ticks: Int,
) {
  fun isComplete(): Boolean {
    return ticks >= SETTLE_TICKS
  }

  fun after(distance: Double): Settling {
    val isNearTarget = distance < SETTLE_EPSILON
    return if (isNearTarget) {
      Settling(ticks + 1)
    } else {
      RESTLESS
    }
  }

  companion object {
    val RESTLESS: Settling = Settling(0)
    val COMPLETE: Settling = Settling(SETTLE_TICKS)

    /**
     * How many consecutive ticks a caret must stay within [SETTLE_EPSILON] of its target before it counts as settled.
     */
    private const val SETTLE_TICKS = 3

    private const val SETTLE_EPSILON = 0.25
  }
}
