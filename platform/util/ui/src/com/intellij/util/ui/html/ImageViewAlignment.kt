// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.github.benmanes.caffeine.cache.Caffeine
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.font.FontRenderContext
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.html.CSS
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument

private data class FontRenderKey(val font: Font, val context: FontRenderContext)

// The rendering context includes the display transform and text rendering hints.
private val xHeightCache = Caffeine.newBuilder().maximumSize(64).build<FontRenderKey, Float> {
  it.font.createGlyphVector(it.context, "x").visualBounds.height.toFloat()
}

/** Returns the image's baseline position as a fraction of its height. */
@ApiStatus.Internal
fun View.getImageVerticalAlignment(): Float {
  val document = document as HTMLDocument
  // Element attributes resolve through parents, but image alignment is not inherited.
  val cssAlignment = getImageCssAlignment(document)
  val htmlAlignment = element.attributes.takeIf { it.isDefined(HTML.Attribute.ALIGN) }
    ?.getAttribute(HTML.Attribute.ALIGN) as? String
  val alignment = cssAlignment ?: htmlAlignment
  return when {
    alignment.equals("top", ignoreCase = true) -> if (cssAlignment == null) 0f else getTopAlignment(document, alignToLine = true)
    alignment.equals("text-top", ignoreCase = true) -> getTopAlignment(document, alignToLine = false)
    alignment.equals("middle", ignoreCase = true) -> if (cssAlignment == null) 0.5f else getMiddleAlignment(document)
    alignment.equals("absmiddle", ignoreCase = true) || alignment.equals("abscenter", ignoreCase = true) -> getMiddleAlignment(document)
    else -> 1f
  }
}

internal fun View.getImageCssAlignment(document: HTMLDocument): String? {
  val attributes = element.attributes
  if (attributes.isDefined(CSS.Attribute.VERTICAL_ALIGN)) {
    attributes.getAttribute(CSS.Attribute.VERTICAL_ALIGN)?.let { return it.toString() }
  }
  // StyleSheet reads class and ID attributes directly only for branch elements.
  val imageElement = object : Element by element {
    override fun isLeaf(): Boolean = false
  }
  val cssValue = document.styleSheet.getRule(HTML.Tag.IMG, imageElement)?.getAttribute(CSS.Attribute.VERTICAL_ALIGN)
  // Swing's CSS value returns its stored string from toString() without allocating.
  return cssValue?.toString()
}

private fun View.getImageFont(document: HTMLDocument): Font {
  val viewAttributes = attributes
  // A plain View returns the element attributes without resolving CSS.
  val fontAttributes = if (viewAttributes == null || viewAttributes === element.attributes) {
    document.styleSheet.getViewAttributes(this)
  }
  else viewAttributes
  return document.getFont(fontAttributes)
}

private fun View.getTopAlignment(document: HTMLDocument, alignToLine: Boolean): Float {
  val font = getImageFont(document)
  val metrics = container?.getFontMetrics(font) ?: return 1f
  var ascent = metrics.ascent.toFloat()
  val row = parent
  if (alignToLine && row != null) {
    for (index in 0 until row.viewCount) {
      val sibling = row.getView(index)
      if (sibling === this) continue
      // Skip top-aligned images to prevent recursive baseline queries.
      if (sibling.element.name == "img" && sibling.getImageCssAlignment(document).equals("top", ignoreCase = true)) continue
      ascent = maxOf(ascent, sibling.getPreferredSpan(View.Y_AXIS) * sibling.getAlignment(View.Y_AXIS))
    }
  }
  // The baseline can lie below a short image, so the alignment can exceed one.
  return ascent / getPreferredSpan(View.Y_AXIS).coerceAtLeast(1f)
}

private fun View.getMiddleAlignment(document: HTMLDocument): Float {
  val font = getImageFont(document)
  val metrics = container?.getFontMetrics(font) ?: return 0.5f
  val xHeight = xHeightCache.get(FontRenderKey(font, metrics.fontRenderContext))
  val height = getPreferredSpan(View.Y_AXIS).coerceAtLeast(1f)
  // CSS middle places the image centre half an x-height above the text baseline.
  return (0.5f + xHeight / (2 * height)).coerceIn(0f, 1f)
}
