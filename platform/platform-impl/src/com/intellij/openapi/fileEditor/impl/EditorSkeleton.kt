// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.application.UI
import com.intellij.openapi.fileEditor.impl.EditorSkeleton.Companion.ANIMATION_DURATION_MS
import com.intellij.openapi.fileEditor.impl.EditorSkeleton.Companion.TICK_MS
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorUtil
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.*
import java.awt.*
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * Component that paints a skeleton animation for a file editor.
 * This component is needed for Remote Dev when latency is quite high.
 *
 * Animation lasts while [cs] is active.
 */
internal class EditorSkeleton(cs: CoroutineScope) : JComponent() {
  private val withAnimation = Registry.`is`("editor.skeleton.animation.enabled", true)
  private val currentTime = AtomicLong(System.currentTimeMillis())
  private val initialTime = currentTime.get()

  init {
    if (withAnimation) {
      cs.launch(Dispatchers.UI) {
        while (isActive) {
          delay(TICK_MS)
          currentTime.set(System.currentTimeMillis())
          repaint()
        }
      }
    }

    layout = BorderLayout()
    isOpaque = false
    add(createGutterComponent(), BorderLayout.WEST)
    add(createEditorComponent(), BorderLayout.CENTER)
  }

  /**
   * Creates skeleton gutter component with line numbers and gutter icons.
   */
  private fun createGutterComponent(): JComponent {
    return JPanel().apply {
      layout = HorizontalLayout(GUTTER_LINE_NUMBERS_AND_ICONS_GAP)
      isOpaque = false
      add(createLineNumbersComponent())
      add(createGutterIconsComponent())
      border = IdeBorderFactory.createBorder(BACKGROUND_COLOR, SideBorder.RIGHT)
    }
  }

  /**
   * Creates skeleton line numbers component.
   */
  private fun createLineNumbersComponent(): JComponent {
    return JPanel().apply {
      layout = VerticalLayout(LINES_GAP, SwingConstants.RIGHT)
      border = JBUI.Borders.empty(SKELETON_OUTER_PADDING, LINE_NUMBERS_LEFT_PADDING, 0, 0)
      isOpaque = false
      repeat(9) {
        add(EditorSkeletonBlock(GUTTER_SMALL, color = { currentColor() }))
      }
      repeat(100) {
        add(EditorSkeletonBlock(GUTTER_NORMAL, color = { currentColor() }))
      }
    }
  }

  /**
   * Creates component with skeleton gutter icons.
   */
  private fun createGutterIconsComponent(): JComponent {
    return JPanel().apply {
      layout = VerticalLayout(LINES_GAP, SwingConstants.RIGHT)
      border = JBUI.Borders.empty(SKELETON_OUTER_PADDING, 0, SKELETON_OUTER_PADDING, GUTTER_ICONS_RIGHT_PADDING)
      isOpaque = false
      repeat(100) {
        if (it in GUTTER_ICON_LINES) {
          add(EditorSkeletonBlock(GUTTER_NORMAL, color = { currentColor() }))
        }
        else {
          Empty()
        }
      }
    }
  }

  private fun createEditorComponent(): JComponent {
    return JPanel().apply {
      layout = VerticalLayout(LINES_GAP)
      border = JBUI.Borders.empty(SKELETON_OUTER_PADDING, EDITOR_LEFT_GAP)
      isOpaque = false
      addEditorBlocks()
    }
  }

  /**
   * Adds hardcoded skeleton template to the component.
   */
  private fun JComponent.addEditorBlocks() {
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
  private fun JComponent.Blocks(
    vararg blocks: SkeletonBlockWidth,
    indents: Int = 0,
  ) {
    val blocksLine = JPanel().apply {
      isOpaque = false
      layout = HorizontalLayout(BLOCKS_GAP)
      border = JBUI.Borders.emptyLeft(INDENT_WIDTH * indents)
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
  private fun JComponent.Empty() {
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
    if (!withAnimation) {
      return BACKGROUND_COLOR
    }

    val elapsed = currentTime.get() - initialTime
    val t = (elapsed % ANIMATION_DURATION_MS).toDouble() / ANIMATION_DURATION_MS.toDouble()
    val opacity = 0.3 + 0.3 * sin(2 * Math.PI * t)
    return ColorUtil.withAlpha(BACKGROUND_COLOR, opacity)
  }

  companion object {
    private val GUTTER_ICON_LINES
      get() = listOf(2, 4, 15, 25, 30)

    private val GUTTER_ICONS_RIGHT_PADDING
      get() = 22

    private val LINE_NUMBERS_LEFT_PADDING
      get() = 20

    private val GUTTER_LINE_NUMBERS_AND_ICONS_GAP
      get() = 4

    /**
     * Padding of skeleton on the top, left, bottom.
     *
     * But this padding shouldn't affect border between gutter and editor skeletons.
     */
    private val SKELETON_OUTER_PADDING
      get() = 2

    /**
     * Gap between gutter border and editor
     */
    private val EDITOR_LEFT_GAP
      get() = 6

    private val INDENT_WIDTH
      get() = 30
    private val TICK_MS
      get() = 40.milliseconds
    private val LINES_GAP
      get() = 6
    private val BLOCKS_GAP
      get() = 6
    private val ANIMATION_DURATION_MS
      get() = Registry.intValue("editor.skeleton.animation.duration.ms", 1500).toLong()
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
      val radius = JBUI.scale(RADIUS)
      g2.fillRoundRect(0, 0, width, height, 2 * radius, 2 * radius)
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
    // TODO: take HEIGHT from editor line height not constant
    val HEIGHT
      get() = 16
    private val RADIUS
      get() = 4
  }
}