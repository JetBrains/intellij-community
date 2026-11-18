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
   * Starts the rendering of the frames of the renderer in [cs].
   * The renderer renders a frame only while [component] is displayable.
   * While [component] is not displayable, the renderer does not wake up.
   */
  fun startRendering(cs: CoroutineScope)

  fun frameTimeMs(nowMs: Long): Long = nowMs

  companion object {
    val TICK_MS = 8.milliseconds

    /**
     * Creates the renderer of [frames] for the current toolkit.
     */
    fun create(frames: EditorSkeletonFrames): EditorSkeletonRenderer {
      // Wayland shows a frame only after a commit of the window surface, and only Swing repaint commits it.
      if (StartupUiUtil.isWaylandToolkit()) {
        return EditorSkeletonRepaintRenderer(frames)
      }
      // The default: the frames do not wait for the EDT, and no heavyweight component turns on AWT mixing.
      if (Registry.`is`("editor.skeleton.direct.renderer.enabled", true)) {
        return EditorSkeletonDirectRenderer(frames)
      }
      // A Canvas on the Windows client hits JBR-10643.
      if (isWindowsFrontend()) {
        return EditorSkeletonRepaintRenderer(frames)
      }
      // The frames do not wait for the EDT, but the heavyweight Canvas turns on AWT mixing.
      return EditorSkeletonCanvas(frames)
    }

    private fun isWindowsFrontend(): Boolean {
      return SystemInfoRt.isWindows && PlatformUtils.isJetBrainsClient()
    }
  }
}
