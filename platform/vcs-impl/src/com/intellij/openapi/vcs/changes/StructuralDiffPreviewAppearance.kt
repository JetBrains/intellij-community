// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffLineNumberConverter
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.ColorUtil
import java.awt.Color
import java.awt.Font
import java.util.function.IntUnaryOperator

/**
 * Gives the Commit diff tab of structural changes a quiet frame. The gutter shows line numbers only: the hunks in the tree have the
 * checkboxes and the rollback. Only the lines of the current version have numbers. A removed line of the unified viewer has none, and the
 * base side of the side-by-side viewer shows none, so its gutter takes no width. Removed lines are red, and added lines are green.
 */
internal class StructuralDiffPreviewAppearance : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return
    if (context.getUserData(STRUCTURAL_PREVIEW_CONTEXT) != true || !StructuralChangesViewSettings.isEnabled(project)) return
    when (viewer) {
      is UnifiedDiffViewer -> {
        quiet(viewer.editor, null)
        viewer.addListener(object : DiffViewerListener() {
          override fun onAfterRediff() = showCurrentLineNumbers(viewer)
        })
      }
      is TwosideTextDiffViewer -> {
        for (side in Side.entries) quiet(viewer.getEditor(side), side)
        viewer.getEditor(Side.LEFT).settings.isLineNumbersShown = false
      }
    }
  }

  private fun quiet(editor: EditorEx, side: Side?) {
    editor.settings.isLineMarkerAreaShown = false
    editor.settings.isFoldingOutlineShown = false
    (editor.markupModel as? EditorMarkupModel)?.setTrafficLightIconVisible(false)
    editor.colorsScheme = StructuralDiffColors(editor.colorsScheme, side)
  }

  /** The viewer sets a column for each side on each rediff, so this listener replaces them after it. */
  private fun showCurrentLineNumbers(viewer: UnifiedDiffViewer) {
    val side = viewer.masterSide
    val converter = DiffLineNumberConverter(viewer.foldingModel.hideLineNumberPredicate(0),
                                            IntUnaryOperator { viewer.transferLineFromOnesideStrict(side, it) })
    viewer.editor.gutter.setLineNumberConverter(converter, null)
  }
}

/**
 * Red removed lines and green added lines, mixed into the editor background. A line with changed words gets the light mix, and the words
 * get the strong one. In the side-by-side viewer, the left side shows a modification as removed, and the right side shows it as added.
 */
private class StructuralDiffColors(delegate: EditorColorsScheme, private val side: Side?) : DelegateColorScheme(delegate) {
  override fun getAttributes(key: TextAttributesKey?): TextAttributes? = paletteAttributes(key) ?: super.getAttributes(key)

  override fun getAttributes(key: TextAttributesKey?, useDefaults: Boolean): TextAttributes? =
    paletteAttributes(key) ?: super.getAttributes(key, useDefaults)

  private fun paletteAttributes(key: TextAttributesKey?): TextAttributes? {
    val modified = key == DiffColors.DIFF_MODIFIED && side != null
    val removed = key == DiffColors.DIFF_DELETED || modified && side == Side.LEFT
    val added = key == DiffColors.DIFF_INSERTED || modified && side == Side.RIGHT
    if (!removed && !added) return null
    val background = defaultBackground
    val tint = when {
      removed -> if (ColorUtil.isDark(background)) REMOVED_DARK else REMOVED_LIGHT
      else -> if (ColorUtil.isDark(background)) ADDED_DARK else ADDED_LIGHT
    }
    // The diff reads the foreground of a diff key as the background of a line with changed words.
    val line = ColorUtil.mix(background, tint.color, tint.line)
    val word = ColorUtil.mix(background, tint.color, tint.word)
    return TextAttributes(line, word, null, null, Font.PLAIN).also { it.errorStripeColor = tint.color }
  }

  /** A color and its shares in the background of a changed line and of a changed word. */
  private class Tint(val color: Color, val line: Double, val word: Double)

  @Suppress("UseJBColor") // The editor scheme picks the tint, not the IDE theme.
  private companion object {
    // Red looks brighter than green at the same share, so a removed line gets less of it.
    val REMOVED_DARK = Tint(Color(0xF85149), line = 0.12, word = 0.32)
    val ADDED_DARK = Tint(Color(0x2EA043), line = 0.16, word = 0.36)
    val REMOVED_LIGHT = Tint(Color(0xFF8182), line = 0.15, word = 0.38)
    val ADDED_LIGHT = Tint(Color(0x4AC26B), line = 0.15, word = 0.38)
  }
}
