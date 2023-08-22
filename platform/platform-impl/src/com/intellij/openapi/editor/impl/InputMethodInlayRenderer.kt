// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.view.EditorPainter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.paint.LinePainter2D
import java.awt.*
import java.awt.geom.Rectangle2D
import kotlin.math.roundToInt

class InputMethodInlayRenderer(val text: String) : EditorCustomElementRenderer {
  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    val iterator = getFontIterator(inlay.editor, null)
    iterator.start(text, 0, text.length)
    var result = 0.0
    while (!iterator.atEnd()) {
      val font = iterator.font
      result += font.getStringBounds(text, iterator.start, iterator.end, iterator.fontInfo.fontRenderContext).width
      iterator.advance()
    }
    return result.roundToInt()
  }

  override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    val editor = inlay.editor
    g.color = textAttributes.foregroundColor

    val iterator = getFontIterator(editor, textAttributes.fontType)
    var currentX = targetRegion.x
    val y = targetRegion.y + editor.ascent

    iterator.start(text, 0, text.length)
    while (!iterator.atEnd()) {
      val subText = text.substring(iterator.start, iterator.end)

      g.font = iterator.font
      g.drawString(subText, currentX.toFloat(), y.toFloat())

      val metrics = iterator.fontInfo.fontMetrics()
      currentX += metrics.getStringBounds(subText, g).width
      iterator.advance()
    }

    val lineY = y + 1
    g.stroke = EditorPainter.IME_COMPOSED_TEXT_UNDERLINE_STROKE
    g.color = editor.colorsScheme.defaultForeground
    LinePainter2D.paint(g, targetRegion.minX, lineY, targetRegion.maxX, lineY)
  }

  private fun getFontIterator(editor: Editor, fontStyle: Int?): FontFallbackIterator {
    return FontFallbackIterator()
      .setPreferredFonts(editor.colorsScheme.fontPreferences)
      .setFontStyle(fontStyle ?: Font.PLAIN)
      .setFontRenderContext(FontInfo.getFontRenderContext(editor.contentComponent))
  }
}