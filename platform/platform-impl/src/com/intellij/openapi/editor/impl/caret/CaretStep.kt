// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import kotlin.time.Duration

internal class CaretStep(
  val moved: Boolean,
  val opacityChanged: Boolean,
  val prefetch: List<CaretRectangle>?,
  val nextDelay: Duration,
  val version: Long
) {
  val isIdle: Boolean get() = nextDelay == Duration.INFINITE

  override fun toString(): String = "CaretStep(moved=$moved, faded=$opacityChanged, prefetch=$prefetch, nextDelay=$nextDelay)"

  companion object {
    val IDLE: CaretStep = CaretStep(moved = false, opacityChanged = false, prefetch = null, nextDelay = Duration.INFINITE, version = 0L)
  }
}
