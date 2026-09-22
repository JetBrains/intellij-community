// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CoroutineScope
import java.awt.Component
import java.awt.Graphics2D
import kotlin.time.Duration.Companion.milliseconds

internal interface EditorSkeletonRenderer {
  val component: Component

  fun startRendering(cs: CoroutineScope, paintFrame: (Graphics2D, Int, Int, Float) -> Unit)

  fun frameTimeMs(nowMs: Long): Long = nowMs

  companion object {
    val TICK_MS = 8.milliseconds

    fun create(): EditorSkeletonRenderer = if (StartupUiUtil.isWaylandToolkit()) {
      EditorSkeletonWaylandRenderer()
    } else {
      EditorSkeletonCanvas()
    }
  }
}
