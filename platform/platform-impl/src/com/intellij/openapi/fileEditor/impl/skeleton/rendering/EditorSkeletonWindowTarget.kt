// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import java.awt.GraphicsConfiguration
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import javax.swing.JRootPane

/**
 * The place of one [EditorSkeletonWindowRenderer] frame, as the EDT saw it. The EDT makes a new target for each change.
 *
 * [origin] and [clip] are in the coordinates of [window]. [rootPaneClip] is the same area in the coordinates of [rootPane].
 * While [covered] is true, an overlay can be on top of the renderer, so a frame goes through a Swing repaint.
 */
internal class EditorSkeletonWindowTarget(
  val window: Window,
  val graphicsConfiguration: GraphicsConfiguration,
  val origin: Point,
  val width: Int,
  val height: Int,
  val clip: Rectangle,
  val rootPane: JRootPane,
  val rootPaneClip: Rectangle,
  val scale: Float,
  val covered: Boolean,
  val frames: EditorSkeletonFrames,
)
