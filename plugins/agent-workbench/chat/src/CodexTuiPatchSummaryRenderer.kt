// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

internal data class CodexTuiPatchSummaryData(
  val verb: String,
  val target: String,
  val added: Int,
  val removed: Int,
)

internal class CodexTuiPatchSummaryRenderer(
  val data: CodexTuiPatchSummaryData,
) : CustomFoldRegionRenderer {
  override fun calcWidthInPixels(region: CustomFoldRegion): Int {
    return region.editor.scrollingModel.visibleArea.width.coerceAtLeast(region.editor.lineHeight)
  }

  override fun calcHeightInPixels(region: CustomFoldRegion): Int {
    return region.editor.lineHeight + JBUI.scale(SUMMARY_BAR_VERTICAL_PADDING * 2)
  }

  override fun paint(
    region: CustomFoldRegion,
    g: Graphics2D,
    targetRegion: Rectangle2D,
    textAttributes: TextAttributes,
  ) {
    paintSummaryBar(region.editor, g, targetRegion, data, isExpanded = false)
  }
}

internal class CodexTuiPatchExpandedHeaderRenderer(
  val data: CodexTuiPatchSummaryData,
) : EditorCustomElementRenderer {
  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return inlay.editor.scrollingModel.visibleArea.width.coerceAtLeast(inlay.editor.lineHeight)
  }

  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    return inlay.editor.lineHeight + JBUI.scale(SUMMARY_BAR_VERTICAL_PADDING * 2)
  }

  override fun paint(
    inlay: Inlay<*>,
    g: Graphics2D,
    targetRegion: Rectangle2D,
    textAttributes: TextAttributes,
  ) {
    paintSummaryBar(inlay.editor, g, targetRegion, data, isExpanded = true)
  }
}

private const val SUMMARY_BAR_VERTICAL_PADDING: Int = 4
private const val SUMMARY_BAR_ARC: Int = 8

private val ADDED_COLOR: JBColor = JBColor(0x1A7F37, 0x3FB950)
private val REMOVED_COLOR: JBColor = JBColor(0xCF222E, 0xF85149)
private val MUTED_FG: JBColor = JBColor(0x656D76, 0x8B949E)

private fun paintSummaryBar(
  editor: Editor,
  g: Graphics2D,
  targetRegion: Rectangle2D,
  data: CodexTuiPatchSummaryData,
  isExpanded: Boolean,
) {
  val x = targetRegion.x.toInt()
  val y = targetRegion.y.toInt()
  val width = targetRegion.width.toInt()
  val height = targetRegion.height.toInt()
  if (width <= 0 || height <= 0) return

  val saved = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

  val inset = JBUI.scale(2)
  val arc = JBUI.scale(SUMMARY_BAR_ARC).toDouble()
  val editorBg = editor.colorsScheme.defaultBackground
  val mixTarget = JBColor(Color.BLACK, Color.WHITE)
  g.color = ColorUtil.mix(editorBg, mixTarget, 0.06)
  g.fill(RoundRectangle2D.Double(
    (x + inset).toDouble(), (y + inset).toDouble(),
    (width - inset * 2).toDouble(), (height - inset * 2).toDouble(),
    arc, arc,
  ))

  val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
  val metrics = g.getFontMetrics(font)
  val textY = y + (height + metrics.ascent - metrics.descent) / 2
  var cx = x + JBUI.scale(12)

  g.color = MUTED_FG
  val chevronSize = JBUI.scale(7)
  val chevronY = y + (height - chevronSize) / 2
  if (isExpanded) {
    g.fillPolygon(
      intArrayOf(cx, cx + chevronSize, cx + chevronSize / 2),
      intArrayOf(chevronY, chevronY, chevronY + chevronSize),
      3,
    )
  }
  else {
    g.fillPolygon(
      intArrayOf(cx, cx + chevronSize, cx),
      intArrayOf(chevronY, chevronY + chevronSize / 2, chevronY + chevronSize),
      3,
    )
  }
  cx += chevronSize + JBUI.scale(8)

  g.font = font.deriveFont(Font.BOLD)
  g.color = MUTED_FG
  g.drawString(data.verb, cx, textY)
  cx += g.fontMetrics.stringWidth(data.verb) + JBUI.scale(6)

  g.font = font
  g.color = editor.colorsScheme.defaultForeground
  g.drawString(data.target, cx, textY)

  g.font = font
  val addedText = "+${data.added}"
  val removedText = "-${data.removed}"
  val addedWidth = metrics.stringWidth(addedText)
  val removedWidth = metrics.stringWidth(removedText)
  val gap = JBUI.scale(8)
  val rightMargin = JBUI.scale(16)

  var sx = x + width - rightMargin - removedWidth
  g.color = if (data.removed > 0) REMOVED_COLOR else MUTED_FG
  g.drawString(removedText, sx, textY)

  sx -= gap + addedWidth
  g.color = if (data.added > 0) ADDED_COLOR else MUTED_FG
  g.drawString(addedText, sx, textY)

  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, saved ?: RenderingHints.VALUE_ANTIALIAS_DEFAULT)
}
