// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import java.awt.Graphics2D

/**
 * The frames of one skeleton. Any thread can call both methods.
 */
internal interface EditorSkeletonFrames {
  /**
   * Returns the colors of the frame at the current time.
   */
  fun currentColors(): EditorSkeletonColorManager.FrameColors

  /**
   * Paints a frame of [width] by [height] with [colors] into [g]. [scale] is the user scale of the IDE.
   */
  fun paint(g: Graphics2D, width: Int, height: Int, scale: Float, colors: EditorSkeletonColorManager.FrameColors)
}
