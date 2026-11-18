// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import java.awt.GraphicsConfiguration
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import javax.swing.JRootPane

/**
 * The place of one [EditorSkeletonDirectRenderer] frame, as the EDT saw it. Two targets with equal values describe the same place.
 *
 * [origin], [clip] and [excluded] are in the coordinates of [window]. [rootPaneClip] is [clip] in the coordinates of [rootPane].
 * A frame draws into [clip] except [excluded], the areas of the overlays on top of the renderer.
 * While [covered] is true, the overlays or the glass pane can paint anywhere on the renderer, so a frame goes through a Swing repaint.
 *
 * The EDT creates each target from new AWT objects and publishes it to the render thread.
 * Nobody changes a target, its rectangles, its point or its list after that.
 */
internal data class EditorSkeletonWindowTarget(
  val window: Window,
  val graphicsConfiguration: GraphicsConfiguration,
  val origin: Point,
  val width: Int,
  val height: Int,
  val clip: Rectangle,
  val excluded: List<Rectangle>,
  val rootPane: JRootPane,
  val rootPaneClip: Rectangle,
  val scale: Float,
  val covered: Boolean,
)
