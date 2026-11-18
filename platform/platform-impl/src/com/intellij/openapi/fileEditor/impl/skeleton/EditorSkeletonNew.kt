// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton

import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.impl.skeleton.layout.Editor
import com.intellij.openapi.fileEditor.impl.skeleton.layout.Gutter
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonPanel
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Layout.HORIZONTAL
import com.intellij.openapi.fileEditor.impl.skeleton.rendering.EditorSkeletonColorManager
import com.intellij.openapi.fileEditor.impl.skeleton.rendering.EditorSkeletonFrames
import com.intellij.openapi.fileEditor.impl.skeleton.rendering.EditorSkeletonRenderer
import com.intellij.openapi.fileEditor.impl.skeleton.rendering.SkeletonFrameColor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.DrawUtil
import com.intellij.ui.paint.useCopy
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBDimension
import kotlinx.coroutines.CoroutineScope
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Graphics2D
import javax.swing.JComponent
import kotlin.math.ceil

/**
 * Shows a file editor skeleton with a renderer selected for the current toolkit.
 * The skeleton fades in over [fadeInMs], then pulses unless the UI is simplified.
 *
 * Any thread can create the skeleton. The skeleton shows nothing until [start] runs on the EDT.
 */
internal class EditorSkeletonNew(
  private val cs: CoroutineScope,
  project: Project,
  fadeInMs: Long,
) : JComponent() {
  private val colorManager = EditorSkeletonColorManager(
    skeletonDelayMs = fadeInMs,
    nowMs = { renderer.frameTimeMs(System.currentTimeMillis()) },
    animationEnabled = !DrawUtil.isSimplifiedUI(),
    animationDurationMs = Registry.intValue("editor.skeleton.animation.duration.ms", 1500).toLong(),
  )
  private val frames = object : EditorSkeletonFrames {
    override fun currentColors(): SkeletonFrameColor {
      return colorManager.frameColors()
    }
    override fun paint(g: Graphics2D, width: Int, height: Int, scale: Float, colors: SkeletonFrameColor) {
      paintFrame(g, width, height, scale, colors)
    }
  }
  private val renderer: EditorSkeletonRenderer = EditorSkeletonRenderer.create(frames)

  init {
    project.messageBus.connect(cs).subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      colorManager.reloadColorScheme()
    })
    layout = BorderLayout()
    isOpaque = false
    minimumSize = JBDimension(0, 0)
    preferredSize = JBDimension(SKELETON_PANEL.preferredWidth, SKELETON_PANEL.preferredHeight)
  }

  /**
   * Adds the renderer and starts the frames in the scope of the skeleton.
   * Call it once on the EDT, after the skeleton is added to its parent.
   */
  fun start() {
    colorManager.reloadColorScheme()
    add(renderer.component, BorderLayout.CENTER)
    renderer.startRendering(cs)
  }

  private fun paintFrame(g: Graphics2D, width: Int, height: Int, scale: Float, colors: SkeletonFrameColor) {
    g.useCopy { graphics ->
      graphics.composite = AlphaComposite.Src
      graphics.color = colors.background
      graphics.fillRect(0, 0, width, height)

      graphics.composite = AlphaComposite.SrcOver
      graphics.scale(scale.toDouble(), scale.toDouble())
      GraphicsUtil.setupAAPainting(graphics)
      graphics.color = colors.blocks
      val scaledWidth = ceil(width / scale.toDouble()).toInt()
      val scaledHeight = ceil(height / scale.toDouble()).toInt()
      SKELETON_PANEL.paint(graphics, 0, 0, scaledWidth, scaledHeight)
    }
  }
}

/**
 * The layout of every skeleton. It is immutable, so all skeletons share it.
 */
private val SKELETON_PANEL: EditorSkeletonPanel = EditorSkeletonPanel(HORIZONTAL, fillLast = true) {
  Gutter()
  Editor()
}
