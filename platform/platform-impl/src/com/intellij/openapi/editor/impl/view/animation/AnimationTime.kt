// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import kotlin.time.TimeSource

internal typealias AnimationTimeMark = TimeSource.Monotonic.ValueTimeMark

internal object AnimationClock {
  fun markAnimationNow(): AnimationTimeMark = TimeSource.Monotonic.markNow()
}
