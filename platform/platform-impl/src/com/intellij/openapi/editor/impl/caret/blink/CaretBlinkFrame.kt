// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.blink

import kotlin.time.Duration

internal data class CaretBlinkFrame(
  val opacity: Float?,
  val wantsPrefetch: Boolean,
  val nextDelay: Duration,
) {
  companion object {
    val DORMANT: CaretBlinkFrame = CaretBlinkFrame(null, false, Duration.INFINITE)
  }
}
