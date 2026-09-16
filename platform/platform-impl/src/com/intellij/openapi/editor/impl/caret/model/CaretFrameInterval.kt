// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long the animation waits between two frames.
 *
 * A move needs a short interval to look smooth on a high refresh rate monitor. A blink can be slower, because
 * neighbouring opacity levels are hard to tell apart.
 */
internal object CaretFrameInterval {
  val MOVEMENT: Duration = 4.milliseconds

  val BLINK: Duration = 16.milliseconds
}
