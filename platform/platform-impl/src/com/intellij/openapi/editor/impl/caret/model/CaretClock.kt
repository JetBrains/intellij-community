// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal const val TICK_MS = 4

internal object CaretClock {
  val TICK: Duration = TICK_MS.milliseconds

  fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
}
