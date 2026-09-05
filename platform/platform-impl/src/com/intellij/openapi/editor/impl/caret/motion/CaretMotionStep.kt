// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import kotlin.time.Duration

internal data class CaretMotionStep(
  val moved: Boolean,
  val prefetch: List<CaretRectangle>?,
  val nextDelay: Duration,
) {
  companion object {
    val IDLE: CaretMotionStep = CaretMotionStep(moved = false, prefetch = null, nextDelay = Duration.INFINITE)
  }
}
