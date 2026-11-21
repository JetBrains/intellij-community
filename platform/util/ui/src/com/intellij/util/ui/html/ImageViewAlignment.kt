// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import org.jetbrains.annotations.ApiStatus
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.html.CSS
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument

/** Returns the image's baseline position as a fraction of its height. */
@ApiStatus.Internal
fun View.getImageVerticalAlignment(): Float {
  val document = document as HTMLDocument
  // Element attributes resolve through parents, but image alignment is not inherited.
  val cssAlignment = getImageCssAlignment(document)
  val htmlAlignment = element.attributes.takeIf { it.isDefined(HTML.Attribute.ALIGN) }
    ?.getAttribute(HTML.Attribute.ALIGN)?.toString()?.lowercase()
  return when (cssAlignment ?: htmlAlignment) {
    "top" -> if (cssAlignment == null) 0f else getTopAlignment(document, alignToLine = true)
    "text-top" -> getTopAlignment(document, alignToLine = false)
    "middle" -> if (cssAlignment == null) 0.5f else getMiddleAlignment(document)
    "absmiddle", "abscenter" -> getMiddleAlignment(document)
    else -> 1f
  }
}

internal fun View.getImageCssAlignment(document: HTMLDocument): String? {
  // StyleSheet reads class and ID attributes directly only for branch elements.
  val imageElement = object : Element by element {
    override fun isLeaf(): Boolean = false
  }
  val cssValue = element.attributes.takeIf { it.isDefined(CSS.Attribute.VERTICAL_ALIGN) }
                   ?.getAttribute(CSS.Attribute.VERTICAL_ALIGN)
                 ?: document.styleSheet.getRule(HTML.Tag.IMG, imageElement)?.getAttribute(CSS.Attribute.VERTICAL_ALIGN)
  return cssValue?.toString()?.lowercase()
}

private fun View.getTopAlignment(document: HTMLDocument, alignToLine: Boolean): Float {
  val font = document.getFont(document.styleSheet.getViewAttributes(this))
  val metrics = container?.getFontMetrics(font) ?: return 1f
  var ascent = metrics.ascent.toFloat()
  val row = parent
  if (alignToLine && row != null) {
    for (index in 0 until row.viewCount) {
      val sibling = row.getView(index)
      // Skip top-aligned images to prevent recursive baseline queries.
      if (sibling === this || sibling.element.name == "img" && sibling.getImageCssAlignment(document) == "top") continue
      ascent = maxOf(ascent, sibling.getPreferredSpan(View.Y_AXIS) * sibling.getAlignment(View.Y_AXIS))
    }
  }
  // The baseline can lie below a short image, so the alignment can exceed one.
  return ascent / getPreferredSpan(View.Y_AXIS).coerceAtLeast(1f)
}

private fun View.getMiddleAlignment(document: HTMLDocument): Float {
  val font = document.getFont(document.styleSheet.getViewAttributes(this))
  val metrics = container?.getFontMetrics(font) ?: return 0.5f
  val xHeight = font.createGlyphVector(metrics.fontRenderContext, "x").visualBounds.height.toFloat()
  val height = getPreferredSpan(View.Y_AXIS).coerceAtLeast(1f)
  // CSS middle places the image centre half an x-height above the text baseline.
  return (0.5f + xHeight / (2 * height)).coerceIn(0f, 1f)
}
