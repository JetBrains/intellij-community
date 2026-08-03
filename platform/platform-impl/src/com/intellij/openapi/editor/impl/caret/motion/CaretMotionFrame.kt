// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import kotlin.time.Duration

internal data class CaretMotionFrame(
  val locations: List<CaretRectangle>?,
  val stale: List<CaretRectangle>,
  val nextDelay: Duration,
) {
  companion object {
    val IDLE: CaretMotionFrame = CaretMotionFrame(null, emptyList(), Duration.INFINITE)
  }
}
