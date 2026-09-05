// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal object CaretClock {
  val MOVEMENT_FRAME: Duration = 4.milliseconds

  val BLINK_FRAME: Duration = 16.milliseconds
}
