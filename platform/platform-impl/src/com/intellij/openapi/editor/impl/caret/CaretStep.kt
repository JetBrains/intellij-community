// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import kotlin.time.Duration

/**
 * What one advance of the animation produced: what has to be repainted, what is worth prefetching into the cache,
 * and when the next advance is due.
 *
 * @param version version of the state this step produced, so the loop can tell its own write from a concurrent one
 */
internal class CaretStep(
  val moved: Boolean,
  val opacityChanged: Boolean,
  val prefetch: List<CaretRectangle>?,
  val nextDelay: Duration,
  val version: Long,
) {
  /**
   * Whether nothing is animating, so the loop has no reason to run until the state changes again.
   */
  val isIdle: Boolean get() = nextDelay == Duration.INFINITE

  override fun toString(): String =
    "CaretStep(moved=$moved, opacityChanged=$opacityChanged, prefetch=$prefetch, nextDelay=$nextDelay)"

  companion object {
    val IDLE: CaretStep = CaretStep(
      moved = false,
      opacityChanged = false,
      prefetch = null,
      nextDelay = Duration.INFINITE,
      version = 0L,
    )
  }
}
