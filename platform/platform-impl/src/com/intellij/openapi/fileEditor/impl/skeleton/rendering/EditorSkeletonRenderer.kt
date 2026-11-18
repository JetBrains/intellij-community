// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CoroutineScope
import java.awt.Component
import kotlin.time.Duration.Companion.milliseconds

internal interface EditorSkeletonRenderer {
  val component: Component

  /**
   * Starts the rendering of [frames] in [cs].
   * The renderer renders a frame only while [component] is displayable.
   * While [component] is not displayable, the renderer does not wake up.
   */
  fun startRendering(cs: CoroutineScope, frames: EditorSkeletonFrames)

  fun frameTimeMs(nowMs: Long): Long = nowMs

  companion object {
    val TICK_MS = 8.milliseconds

    fun create(): EditorSkeletonRenderer {
      if (StartupUiUtil.isWaylandToolkit()) {
        return EditorSkeletonFallbackRenderer()
      }
      if (Registry.`is`("editor.skeleton.window.renderer", true)) {
        return EditorSkeletonWindowRenderer()
      }
      if (isWindowsFrontend()) {
        return EditorSkeletonFallbackRenderer()
      }
      return EditorSkeletonCanvas()
    }

    private fun isWindowsFrontend(): Boolean {
      return SystemInfoRt.isWindows && PlatformUtils.isJetBrainsClient()
    }
  }
}
