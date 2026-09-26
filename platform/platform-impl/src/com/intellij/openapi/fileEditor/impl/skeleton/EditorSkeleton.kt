// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton

import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.impl.skeleton.layout.Editor
import com.intellij.openapi.fileEditor.impl.skeleton.layout.Gutter
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonPanel
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Layout.HORIZONTAL
import com.intellij.openapi.fileEditor.impl.skeleton.rendering.EditorSkeletonColorManager
import com.intellij.openapi.fileEditor.impl.skeleton.rendering.EditorSkeletonRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.ui.paint.use
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBDimension
import kotlinx.coroutines.CoroutineScope
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics2D
import javax.swing.JComponent
import kotlin.math.ceil

/**
 * Shows a file editor skeleton with a renderer selected for the current toolkit.
 * The skeleton fades in over [skeletonDelayMs], then pulses when animation is enabled.
 * [nowMs] supplies the clock for rendering frames with a fixed timeline.
 */
class EditorSkeleton(
  val cs: CoroutineScope,
  val project: Project,
  val skeletonDelayMs: Long,
  nowMs: () -> Long = System::currentTimeMillis,
) : JComponent() {
  private val renderer: EditorSkeletonRenderer = EditorSkeletonRenderer.create()
  private val colorManager = EditorSkeletonColorManager(
    skeletonDelayMs = skeletonDelayMs,
    nowMs = { renderer.frameTimeMs(nowMs()) },
    animationEnabled = RegistryManager.getInstance().`is`(ANIMATION_ENABLED_KEY),
    animationDurationMs = RegistryManager.getInstance().intValue(ANIMATION_DURATION_KEY).toLong(),
  )

  private val skeletonPanel = EditorSkeletonPanel(HORIZONTAL, fillLast = true) {
    Gutter()
    Editor()
  }

  init {
    project.messageBus.connect(cs).subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      colorManager.reloadColorScheme()
    })

    layout = BorderLayout()
    isOpaque = false
    minimumSize = JBDimension(0, 0)
    preferredSize = JBDimension(skeletonPanel.preferredWidth, skeletonPanel.preferredHeight)
    add(renderer.component, BorderLayout.CENTER)
  }

  internal fun startAnimation() {
    renderer.startRendering(cs, ::paintFrame)
  }

  fun paintFrame(g: Graphics2D, width: Int, height: Int, scale: Float = JBUIScale.scale(1f)) {
    val colors = colorManager.frameColors()
    (g.create() as Graphics2D).use { graphics ->
      graphics.composite = AlphaComposite.Src
      graphics.color = colors.background
      graphics.fillRect(0, 0, width, height)

      graphics.composite = AlphaComposite.SrcOver
      graphics.scale(scale.toDouble(), scale.toDouble())
      GraphicsUtil.setupAAPainting(graphics)
      graphics.color = colors.blocks
      skeletonPanel.paint(graphics, 0, 0, ceil(width / scale.toDouble()).toInt(), ceil(height / scale.toDouble()).toInt())
    }
  }

  companion object {
    val EDITOR_BACKGROUND_COLOR: Color
      get() = EditorColorsManager.getInstance().globalScheme.defaultBackground

    private const val ANIMATION_ENABLED_KEY = "editor.skeleton.animation.enabled"
    private const val ANIMATION_DURATION_KEY = "editor.skeleton.animation.duration.ms"
  }
}
