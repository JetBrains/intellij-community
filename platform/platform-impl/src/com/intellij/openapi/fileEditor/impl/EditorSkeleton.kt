// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth.*
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.*
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * Component that paints a skeleton animation for a file editor.
 * This component is needed for Remote Dev when latency is quite high.
 *
 * Animation lasts while [cs] is active.
 */
internal class EditorSkeleton(cs: CoroutineScope) : JComponent() {
  private val currentTime = AtomicLong(System.currentTimeMillis())
  private val initialTime = currentTime.get()

  init {
    cs.launch {
      while (isActive) {
        delay(TICK_MS)
        currentTime.set(System.currentTimeMillis())
        repaint()
      }
    }

    layout = VerticalLayout(LINES_GAP)
    border = JBUI.Borders.empty(2)
    isOpaque = false

    addBlocks()
  }

  /**
   * Adds hardcoded skeleton template to the component.
   */
  private fun addBlocks() {
    repeat(5) {
      Empty()
      Blocks(NORMAL, EXTRA_LARGE, SMALL)
      Empty()
      Blocks(LARGE, SMALL, SMALL)
      Empty()
      Blocks(SMALL)
      Blocks(EXTRA_LARGE, SMALL)
      Blocks(LARGE, indents = 1)
      Blocks(LARGE, indents = 1)
      Blocks(NORMAL, indents = 1)
      Blocks(SMALL, indents = 1)
      Blocks(NORMAL, SMALL, indents = 1)
      Empty()
      Blocks(NORMAL, NORMAL)
      Blocks(NORMAL, SMALL, indents = 1)
      Blocks(NORMAL, NORMAL, indents = 2)
      Blocks(NORMAL, NORMAL, SMALL, indents = 2)
      Empty()
      Blocks(NORMAL, NORMAL, indents = 1)
      Empty()
    }
  }

  /**
   * Adds component line with given [blocks]
   *
   * @param indents number of code-like indents (0, 1, 2, 3, etc.)
   * @see EditorSkeletonBlock
   */
  private fun Blocks(
    vararg blocks: SkeletonBlockWidth,
    indents: Int = 0,
  ) {
    val blocksLine = JPanel().apply {
      isOpaque = false
      layout = HorizontalLayout(BLOCKS_GAP)
      border = JBUI.Borders.emptyLeft(30 * indents)
    }
    for (block in blocks) {
      val blockPanel = BorderLayoutPanel().apply {
        isOpaque = false
        addToCenter(EditorSkeletonBlock(block) { currentColor() })
      }
      blocksLine.add(blockPanel)
    }
    add(blocksLine)
  }

  /**
   * Adds an empty component like an empty editor line.
   */
  private fun Empty() {
    add(BorderLayoutPanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(EditorSkeletonBlock.HEIGHT / 2 + BLOCKS_GAP / 2, 0)
    })
  }

  /**
   * Provides given background color for blocks, so they hide/appear with a fade effect.
   * This function is based on the [System.currentTimeMillis].
   * So we need to change current time quite frequently to have smooth animation (see [TICK_MS]).
   *
   * @see ANIMATION_DURATION_MS
   */
  private fun currentColor(): Color {
    val elapsed = currentTime.get() - initialTime
    val t = (elapsed % ANIMATION_DURATION_MS).toDouble() / ANIMATION_DURATION_MS.toDouble()
    val opacity = 0.5 + 0.5 * sin(2 * Math.PI * t)
    return ColorUtil.withAlpha(BACKGROUND_COLOR, opacity)
  }

  companion object {
    private val TICK_MS
      get() = 40.milliseconds
    private val LINES_GAP
      get() = 6
    private val BLOCKS_GAP
      get() = 6
    private val ANIMATION_DURATION_MS
      get() = 1500L
    private val BACKGROUND_COLOR
      get() = JBUI.CurrentTheme.Editor.BORDER_COLOR
  }
}

/**
 * Represents a single skeleton block of different size (see [SkeletonBlockWidth]).
 *
 * [EditorSkeleton] should requests repaint for this [EditorSkeletonBlock] and provide up-to-date [color].
 */
private class EditorSkeletonBlock(
  blockWidth: SkeletonBlockWidth,
  private val color: () -> Color,
) : JComponent() {
  init {
    val size = JBDimension(blockWidth.width, HEIGHT)
    preferredSize = size
    minimumSize = size
    maximumSize = size
    isOpaque = false
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      g2.color = color()
      g2.fillRoundRect(0, 0, width, height, 2 * RADIUS, 2 * RADIUS)
    }
    finally {
      g2.dispose()
    }
  }

  enum class SkeletonBlockWidth(val width: Int) {
    GUTTER_SMALL(10),
    GUTTER_NORMAL(16),
    SMALL(32),
    NORMAL(69),
    LARGE(184),
    EXTRA_LARGE(197),
  }

  companion object {
    val HEIGHT
      get() = 16
    private val RADIUS
      get() = JBUI.scale(4)
  }
}