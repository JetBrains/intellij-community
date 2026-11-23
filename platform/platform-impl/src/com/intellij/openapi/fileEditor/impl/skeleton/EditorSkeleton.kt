// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics2D
import javax.swing.JComponent

/**
 * A stub that only keeps `EditorSkeletonFadeInSheetAction` of the devkit compiling. It paints nothing.
 * The file editor shows [EditorSkeletonNew].
 */
@Deprecated("The file editor does not use this class. It paints nothing.")
@ApiStatus.ScheduledForRemoval
@Suppress("unused", "UNUSED_PARAMETER")
class EditorSkeleton(
  cs: CoroutineScope,
  project: Project,
  skeletonDelayMs: Long,
  nowMs: () -> Long = System::currentTimeMillis,
) : JComponent() {
  @Deprecated("The file editor does not use this method. It paints nothing.")
  @ApiStatus.ScheduledForRemoval
  fun paintFrame(g: Graphics2D, width: Int, height: Int, scale: Float = 1f) {
  }

  companion object {
    @get:Deprecated("The file editor does not use this property. Read the default background of the global color scheme.")
    @get:ApiStatus.ScheduledForRemoval
    val EDITOR_BACKGROUND_COLOR: Color
      get() = EditorColorsManager.getInstance().globalScheme.defaultBackground
  }
}
