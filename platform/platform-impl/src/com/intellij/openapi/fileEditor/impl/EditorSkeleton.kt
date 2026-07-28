// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth.EXTRA_LARGE
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth.GUTTER_NORMAL
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth.GUTTER_SMALL
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth.LARGE
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth.NORMAL
import com.intellij.openapi.fileEditor.impl.EditorSkeletonBlock.SkeletonBlockWidth.SMALL
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.animation.Easing
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.time.Duration.Companion.milliseconds

/**
 * Component that paints a skeleton for a file editor.
 * This component is needed for Remote Dev when latency is quite high.
 *
 * The skeleton fades in over [skeletonDelayMs] while [cs] is active, then stays static.
 * [nowMs] is the clock the fade-in is measured against; tests replace it to advance the fade-in deterministically.
 */
@ApiStatus.Internal
class EditorSkeleton(
  cs: CoroutineScope,
  val project: Project,
  val skeletonDelayMs: Long,
  nowMs: () -> Long = System::currentTimeMillis,
) : JComponent() {
  private val colorManager = EditorSkeletonColorManager(skeletonDelayMs, nowMs)

  init {
    cs.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      while (isActive && !colorManager.isFadeInComplete) {
        delay(TICK_MS)
        tickFadeIn()
      }
    }

    // This listener is required for the rare case when the color scheme changes while the skeleton is showing.
    // Reload the cached colors and repaint the already-created component to keep it consistent with the new scheme.
    // If the skeleton has already been removed, repaint() is harmless: Swing discards repaint requests for detached components.
    project.messageBus.connect(cs).subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      cs.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
        colorManager.reloadColorScheme()
        repaint()
      }
    })

    layout = BorderLayout()
    isOpaque = false
    add(createGutterComponent(), BorderLayout.WEST)
    add(createEditorComponent(), BorderLayout.CENTER)
  }

  fun tickFadeIn() {
    colorManager.updateCurrentTime()
    repaint()
  }

  override fun paint(g: Graphics) {
    colorManager.markPainted()
    val g2 = g.create() as Graphics2D
    GraphicsUtil.setupAAPainting(g2)
    try {
      super.paint(g2)
    }
    finally {
      g2.dispose()
    }
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
      border = EditorSkeletonSideBorder(colorManager, SideBorder.RIGHT)
    }
  }

  /**
   * Creates skeleton line numbers component.
   */
  private fun createLineNumbersComponent(): JComponent {
    return JPanel().apply {
      layout = VerticalLayout(LINES_GAP, SwingConstants.RIGHT)
      border = JBUI.Borders.empty(SKELETON_OUTER_PADDING, LINE_NUMBERS_LEFT_PADDING, SKELETON_OUTER_PADDING, 0)
      isOpaque = false
      repeat(9) {
        add(EditorSkeletonBlock(GUTTER_SMALL, colorManager))
      }
      repeat(91) {
        add(EditorSkeletonBlock(GUTTER_NORMAL, colorManager))
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
          add(EditorSkeletonBlock(GUTTER_NORMAL, colorManager))
        }
        else {
          Empty(GUTTER_NORMAL)
        }
      }
    }
  }

  // The JPanel hierarchy should be the exact same as for the gutter component
  // This is to ensure that the lines stay y-aligned
  private fun createEditorComponent(): JComponent {
    return JPanel().apply {
      layout = HorizontalLayout(GUTTER_LINE_NUMBERS_AND_ICONS_GAP)
      isOpaque = false
      border = EditorSkeletonSideBorder(colorManager, SideBorder.LEFT)
      add(JPanel().apply {
        layout = VerticalLayout(LINES_GAP)
        border = JBUI.Borders.empty(SKELETON_OUTER_PADDING, EDITOR_LEFT_GAP)
        isOpaque = false
        addEditorBlocks()
      })
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
        addToCenter(EditorSkeletonBlock(block, colorManager))
      }
      blocksLine.add(blockPanel)
    }
    add(blocksLine)
  }

  /**
   * Adds an empty component like an empty editor line.
   */
  private fun JComponent.Empty(width: SkeletonBlockWidth = NORMAL) {
    add(Box.createRigidArea(JBDimension(width.width, EditorSkeletonBlock.HEIGHT)))
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
      get() = 8.milliseconds
    private val LINES_GAP
      get() = 6
    private val BLOCKS_GAP
      get() = 6

    /**
     * Oklab lightness distance between the editor background and a fully faded-in skeleton block.
     *
     * Oklab lightness is perceptually uniform, so one value keeps the skeleton contrast comparable in light and dark
     * themes, and only its direction depends on the theme.
     */
    private val SKELETON_LIGHTNESS_DELTA
      get() = 0.13

    internal val SKELETON_COLOR: Color get() {
      val background = EDITOR_BACKGROUND_COLOR
      val delta = if (ColorUtil.isDark(background)) SKELETON_LIGHTNESS_DELTA else -SKELETON_LIGHTNESS_DELTA
      return EditorSkeletonOklab.shiftLightness(background, delta)
    }

    @get:ApiStatus.Internal
    val EDITOR_BACKGROUND_COLOR: Color
      get() = EditorColorsManager.getInstance().globalScheme.defaultBackground
  }
}

private class EditorSkeletonColorManager(
  private val skeletonDelayMs: Long,
  private val nowMs: () -> Long,
) {
  private var ramp = createRamp()
  private var fadeInStartTime: Long? = null
  private var currentColor = ramp.colorAt(0.0)
  private var hasBeenPainted = false
  private var fadeInComplete = false

  val color: Color
    get() = currentColor

  val isFadeInComplete: Boolean
    get() = fadeInComplete

  fun markPainted() {
    hasBeenPainted = true
  }

  fun updateCurrentTime() {
    val now = nowMs()
    if (fadeInStartTime == null) {
      if (!hasBeenPainted) return
      fadeInStartTime = now
      currentColor = ramp.colorAt(0.0)
      return
    }
    currentColor = colorAt(now)
  }

  fun reloadColorScheme() {
    ramp = createRamp()
    if (fadeInStartTime == null) {
      currentColor = ramp.colorAt(0.0)
    }
    else {
      updateCurrentTime()
    }
  }

  private fun createRamp(): EditorSkeletonOklab.Ramp {
    return EditorSkeletonOklab.ramp(EditorSkeleton.EDITOR_BACKGROUND_COLOR, EditorSkeleton.SKELETON_COLOR)
  }

  private fun colorAt(now: Long): Color {
    val progress = fadeInProgress(now - checkNotNull(fadeInStartTime))
    if (progress >= 1.0) {
      fadeInComplete = true
    }
    return ramp.colorAt(progress)
  }

  private val curve = Easing.bezier(0.4, 0.0, 1.0, 1.0)

  private fun fadeInProgress(elapsedMs: Long): Double {
    if (skeletonDelayMs <= 0) return 1.0
    return curve.calc(elapsedMs.coerceIn(0, skeletonDelayMs).toDouble() / skeletonDelayMs.toDouble())
  }
}

private class EditorSkeletonSideBorder(
  private val colorManager: EditorSkeletonColorManager,
  side: Int,
) : SideBorder(JBColor.border(), side) {
  override fun getLineColor(): Color = colorManager.color
}

/**
 * Represents a single skeleton block of different size (see [SkeletonBlockWidth]).
 *
 * [EditorSkeleton] requests repaint for this [EditorSkeletonBlock], which gets the current color from [EditorSkeletonColorManager].
 */
private class EditorSkeletonBlock(
  blockWidth: SkeletonBlockWidth,
  private val colorManager: EditorSkeletonColorManager,
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

    g.color = colorManager.color
    val radius = JBUI.scale(RADIUS)
    g.fillRoundRect(0, 0, width, height, 2 * radius, 2 * radius)
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
