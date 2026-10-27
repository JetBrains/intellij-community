// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffGutterRenderer
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.ColorUtil
import java.awt.Color
import java.awt.Font

/**
 * Gives the Commit diff tab of structural changes a quiet frame. The gutter shows line numbers and the colors of the changes, but no icons:
 * the hunks in the tree have the checkboxes and the rollback. The scroll bar shows the marks of errors and warnings, but no marks of the
 * changes, because the tree lists them. Removed lines are red, added lines are green, and changed lines are blue.
 */
internal class StructuralDiffPreviewAppearance : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return
    if (context.getUserData(STRUCTURAL_PREVIEW_CONTEXT) != true || !StructuralChangesViewSettings.isEnabled(project)) return
    when (viewer) {
      is UnifiedDiffViewer -> quiet(viewer.editor, viewer)
      is TwosideTextDiffViewer -> for (side in Side.entries) quiet(viewer.getEditor(side), viewer)
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
      override fun afterAdded(highlighter: RangeHighlighterEx) = dropArrow(highlighter)

      override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleOrColorChanged: Boolean) {
        if (renderersChanged) dropArrow(highlighter)
      }
    })
  }

  private fun dropArrow(highlighter: RangeHighlighterEx) {
    if (highlighter.gutterIconRenderer is DiffGutterRenderer) highlighter.gutterIconRenderer = null
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
