// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.view.EditorPainter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.paint.LinePainter2D
import java.awt.*
import java.awt.geom.Rectangle2D

class InputMethodInlayRenderer(val text: String) : EditorCustomElementRenderer {
  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    val metrics = getFontMetrics(inlay.editor)
    return metrics.stringWidth(text)
  }

  override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    val editor = inlay.editor
    val metrics = getFontMetrics(editor)

    g.color = textAttributes.foregroundColor
    g.font = metrics.font
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(true))

    val y = targetRegion.y + editor.ascent
    g.drawString(text, targetRegion.x.toFloat(), y.toFloat())

    val lineY = y + 1
    g.stroke = EditorPainter.IME_COMPOSED_TEXT_UNDERLINE_STROKE
    g.color = editor.colorsScheme.defaultForeground
    LinePainter2D.paint(g, targetRegion.minX, lineY, targetRegion.maxX, lineY)
  }

  private fun getFontMetrics(editor: Editor): FontMetrics {
    val fontInfo = ComplementaryFontsRegistry.getFontAbleToDisplay(
      text, 0, text.length, Font.PLAIN,
      editor.colorsScheme.fontPreferences,
      FontInfo.getFontRenderContext(editor.contentComponent)
    )
    return fontInfo.fontMetrics()
  }
}