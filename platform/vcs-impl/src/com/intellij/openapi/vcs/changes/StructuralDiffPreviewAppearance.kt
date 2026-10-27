// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.DiffSplitter
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffDividerDrawUtil
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffGutterRenderer
import com.intellij.diff.util.DiffLineMarkerRenderer
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import javax.swing.JComponent

/**
 * Gives the Commit diff tab of structural changes a quiet frame. The gutter shows line numbers and the colors of the changes, but no icons:
 * the hunks in the tree have the checkboxes and the rollback. The scroll bar shows the marks of errors and warnings, but no marks of the
 * changes, because the tree lists them. Removed lines are red, added lines are green, and changed lines are blue. A change has one color
 * from the left text to the right text.
 */
internal class StructuralDiffPreviewAppearance : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return
    if (context.getUserData(STRUCTURAL_PREVIEW_CONTEXT) != true || !StructuralChangesViewSettings.isEnabled(project)) return
    when (viewer) {
      is UnifiedDiffViewer -> quiet(viewer.editor, viewer)
      is TwosideTextDiffViewer -> for (side in Side.entries) quiet(viewer.getEditor(side), viewer)
    }
    if (viewer is SimpleDiffViewer) {
      // The viewer sets the painter of its divider in its init, after the extensions.
      viewer.addListener(object : DiffViewerListener() {
        override fun onInit() {
          val splitter = UIUtil.findComponentOfType(viewer.component, DiffSplitter::class.java) ?: return
          splitter.painter = MatchedLinksPainter(splitter.painter, viewer)
        }
      })
    }
  }

  /**
   * The gutter paints the colors of the changes only while it shows the line marker area, so the area stays and loses its icons. The
   * arrows that apply a change ignore the icon setting, so the editor drops them.
   */
  private fun quiet(editor: EditorEx, viewer: Disposable) {
    editor.settings.setGutterIconsShown(false)
    editor.settings.isFoldingOutlineShown = false
    (editor.markupModel as? EditorMarkupModel)?.setTrafficLightIconVisible(false)
    editor.colorsScheme = StructuralDiffColors(editor.colorsScheme)
    editor.markupModel.addMarkupModelListener(viewer, object : MarkupModelListener {
      override fun afterAdded(highlighter: RangeHighlighterEx) = quietRenderers(highlighter)

      override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleOrColorChanged: Boolean) {
        if (renderersChanged) quietRenderers(highlighter)
      }
    })
  }

  private fun quietRenderers(highlighter: RangeHighlighterEx) {
    if (highlighter.gutterIconRenderer is DiffGutterRenderer) highlighter.gutterIconRenderer = null
    matchGutterColor(highlighter)
  }

  /**
   * A line with changed words gets the light color in the text, but the full color next to its line numbers. The gutter gets the light
   * color too.
   */
  private fun matchGutterColor(highlighter: RangeHighlighterEx) {
    val renderer = highlighter.lineMarkerRenderer as? DiffLineMarkerRenderer ?: return
    if (renderer.editorMode != DiffDrawUtil.PaintMode.IGNORED || renderer.gutterMode != DiffDrawUtil.PaintMode.DEFAULT) return
    highlighter.lineMarkerRenderer = DiffLineMarkerRenderer(
      highlighter = renderer.highlighter,
      diffType = renderer.diffType,
      editorMode = renderer.editorMode,
      gutterMode = renderer.editorMode,
      hideWithoutLineNumbers = renderer.hideWithoutLineNumbers,
      isEmptyRange = renderer.isEmptyRange,
      isFirstLine = renderer.isFirstLine,
      isLastLine = renderer.isLastLine,
      alignedSides = renderer.alignedSides,
      clearThinLineBetweenGutterAndEditor = false,
    )
  }
}

/**
 * Paints the divider of the platform, and then the link of each line with changed words in the light color of the line. The platform paints
 * such a link in the full color, but the text and the gutter show the light one. An excluded change keeps the look of the platform.
 */
internal class MatchedLinksPainter(
  private val original: DiffSplitter.Painter?,
  private val viewer: SimpleDiffViewer,
) : DiffSplitter.Painter {
  override fun paint(g: Graphics, divider: JComponent) {
    original?.paint(g, divider)
    val left: Editor = viewer.getEditor(Side.LEFT)
    val right: Editor = viewer.getEditor(Side.RIGHT)
    if (left.isDisposed || right.isDisposed || !divider.isShowing || !left.component.isShowing) return
    val graphics = DiffDividerDrawUtil.getDividerGraphics(g, divider, left.component)
    val config = GraphicsUtil.setupAAPainting(graphics)
    try {
      val clip = graphics.clipBounds
      val leftOffset = DiffDividerDrawUtil.getEditorTopOffset(left)
      val rightOffset = DiffDividerDrawUtil.getEditorTopOffset(right)
      for (change in viewer.diffChanges) {
        if (!change.isValid || change.isExcluded || change.isSkipped || change.fragment.innerFragments == null) continue
        // The platform draws a link with a folded side in another shape, so such a link keeps its color.
        if (DiffDividerDrawUtil.isIntervalFolded(left, change.getStartLine(Side.LEFT), change.getEndLine(Side.LEFT)) ||
            DiffDividerDrawUtil.isIntervalFolded(right, change.getStartLine(Side.RIGHT), change.getEndLine(Side.RIGHT))) continue
        val leftRange = DiffDrawUtil.getGutterMarkerPaintRange(left, change.getStartLine(Side.LEFT), change.getEndLine(Side.LEFT))
        val rightRange = DiffDrawUtil.getGutterMarkerPaintRange(right, change.getStartLine(Side.RIGHT), change.getEndLine(Side.RIGHT))
        val top = minOf(leftRange.y1 + leftOffset, rightRange.y1 + rightOffset)
        val bottom = maxOf(leftRange.y2 + leftOffset, rightRange.y2 + rightOffset)
        if (clip != null && top > clip.y + clip.height) break
        if (clip != null && bottom < clip.y) continue
        DiffDividerDrawUtil.DividerPolygon(leftRange.y1 + leftOffset, rightRange.y1 + rightOffset, leftRange.y2 + leftOffset,
                                           rightRange.y2 + rightOffset, change.diffType.getIgnoredColor(right), /* borderColor = */ null,
                                           /* dottedBorder = */ false)
          .paint(graphics, divider.width, /* curve = */ true)
      }
    }
    finally {
      config.restore()
      graphics.dispose()
    }
  }
}

/**
 * Red removed lines, green added lines, and blue changed lines, mixed into the editor background. A line with changed words gets the light
 * mix, and the words get the strong one.
 */
private class StructuralDiffColors(delegate: EditorColorsScheme) : DelegateColorScheme(delegate) {
  override fun getAttributes(key: TextAttributesKey?): TextAttributes? = paletteAttributes(key) ?: super.getAttributes(key)

  override fun getAttributes(key: TextAttributesKey?, useDefaults: Boolean): TextAttributes? =
    paletteAttributes(key) ?: super.getAttributes(key, useDefaults)

  private fun paletteAttributes(key: TextAttributesKey?): TextAttributes? {
    val background = defaultBackground
    val dark = ColorUtil.isDark(background)
    val tint = when (key) {
      DiffColors.DIFF_DELETED -> if (dark) REMOVED_DARK else REMOVED_LIGHT
      DiffColors.DIFF_INSERTED -> if (dark) ADDED_DARK else ADDED_LIGHT
      DiffColors.DIFF_MODIFIED -> if (dark) CHANGED_DARK else CHANGED_LIGHT
      else -> return null
    }
    // The diff reads the foreground of a diff key as the background of a line with changed words.
    val line = ColorUtil.mix(background, tint.color, tint.line)
    val word = ColorUtil.mix(background, tint.color, tint.word)
    // No error stripe color: the tree lists the changes, so the scroll bar shows only the marks of errors and warnings.
    return TextAttributes(line, word, null, null, Font.PLAIN)
  }

  /** A color and its shares in the background of a changed line and of a changed word. */
  private class Tint(val color: Color, val line: Double, val word: Double)

  @Suppress("UseJBColor") // The editor scheme picks the tint, not the IDE theme.
  private companion object {
    // The shares give the three colors about the same brightness.
    // At the same share, red looks brighter than green, and blue looks darker.
    val REMOVED_DARK = Tint(Color(0xF85149), line = 0.12, word = 0.32)
    val ADDED_DARK = Tint(Color(0x2EA043), line = 0.16, word = 0.36)
    val CHANGED_DARK = Tint(Color(0x3574F0), line = 0.16, word = 0.38)
    val REMOVED_LIGHT = Tint(Color(0xFF8182), line = 0.15, word = 0.38)
    val ADDED_LIGHT = Tint(Color(0x4AC26B), line = 0.15, word = 0.38)
    val CHANGED_LIGHT = Tint(Color(0x3574F0), line = 0.11, word = 0.24)
  }
}
