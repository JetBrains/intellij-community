// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal typealias CaretTimeMark = TimeSource.Monotonic.ValueTimeMark

internal object CaretClock {
  val TICK: Duration = 4.milliseconds
}
