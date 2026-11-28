// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.util.ui.html.ParagraphViewEx.ParagraphRow
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

/** Returns the distance from the image top to its baseline as a fraction of the image height. */
@ApiStatus.Internal
fun View.getImageVerticalAlignment(): Float {
  val document = document as HTMLDocument
  val parent = parent
  val row = parent as? ParagraphRow
  val input = row?.getImageAlignmentInput(this, document)
  val mode = input?.mode ?: resolveImageAlignment(document)
  when (mode) {
    ImageAlignment.BASELINE -> return 1f
    ImageAlignment.LEGACY_TOP -> return 0f
    ImageAlignment.LEGACY_MIDDLE -> return 0.5f
    ImageAlignment.TOP, ImageAlignment.TEXT_TOP, ImageAlignment.MIDDLE -> Unit
  }

  val fontOffset = getImageFontOffset(document, mode, input)
  if (fontOffset.isNaN()) return if (mode == ImageAlignment.MIDDLE) 0.5f else 1f

  // CSS middle measures from the image centre. CSS top and text-top measure from its top.
  val baselineOffset = when (mode) {
      ImageAlignment.TOP -> {
        val siblingBaselineOffset = when {
          row != null -> row.getSiblingBaselineOffset(this, document, fontOffset)
          parent != null -> parent.calculateSiblingBaselineOffset(this, document)
          else -> 0f
        }
        maxOf(fontOffset, siblingBaselineOffset)
      }
      else -> fontOffset
  }
  val relativeOffset = baselineOffset / getPreferredSpan(View.Y_AXIS).coerceAtLeast(1f)
  // The baseline can lie below a short top-aligned image.
  return if (mode == ImageAlignment.MIDDLE) (0.5f + relativeOffset).coerceIn(0f, 1f) else relativeOffset
}

internal enum class ImageAlignment { BASELINE, TOP, TEXT_TOP, MIDDLE, LEGACY_TOP, LEGACY_MIDDLE }

internal class ImageAlignmentInput(val mode: ImageAlignment, var fontOffset: Float = Float.NaN)

internal fun View.resolveImageAlignment(document: HTMLDocument): ImageAlignment {
  // Element attributes resolve through parents, but image alignment is not inherited.
  val cssAlignment = getImageCssAlignment(document)
  val alignment = cssAlignment ?: (element.attributes.takeIf { it.isDefined(HTML.Attribute.ALIGN) }
    ?.getAttribute(HTML.Attribute.ALIGN) as? String)
  return when {
    alignment.equals("top", ignoreCase = true) -> if (cssAlignment == null) ImageAlignment.LEGACY_TOP else ImageAlignment.TOP
    alignment.equals("text-top", ignoreCase = true) -> ImageAlignment.TEXT_TOP
    alignment.equals("middle", ignoreCase = true) -> if (cssAlignment == null) ImageAlignment.LEGACY_MIDDLE else ImageAlignment.MIDDLE
    alignment.equals("absmiddle", ignoreCase = true) || alignment.equals("abscenter", ignoreCase = true) -> ImageAlignment.MIDDLE
    else -> ImageAlignment.BASELINE
  }
}

private fun View.getImageCssAlignment(document: HTMLDocument): String? {
  val image = element
  val attributes = image.attributes
  if (attributes.isDefined(CSS.Attribute.VERTICAL_ALIGN)) {
    attributes.getAttribute(CSS.Attribute.VERTICAL_ALIGN)?.let { return it.toString() }
  }
  // StyleSheet reads class and ID attributes directly only for branch elements.
  val imageElement = if (attributes.isDefined(HTML.Attribute.CLASS) || attributes.isDefined(HTML.Attribute.ID)) {
    object : Element by image {
      override fun isLeaf(): Boolean = false
    }
  }
  else image
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

internal fun View.calculateSiblingBaselineOffset(image: View, document: HTMLDocument): Float {
  // Plain Swing rows use the same scan. ParagraphRow can reuse inputs from the current pass.
  val row = this as? ParagraphRow
  var baselineOffset = 0f
  // The largest child baseline offset sets the distance from the row top to the baseline.
  for (index in 0 until viewCount) {
    val sibling = getView(index)
    if (sibling === image) continue
    // Skip CSS top images because they use this distance to calculate their own offsets.
    if (sibling.element.name == "img") {
      val mode = row?.getImageAlignmentInput(sibling, document)?.mode ?: sibling.resolveImageAlignment(document)
      if (mode == ImageAlignment.TOP) continue
    }
    val siblingBaselineOffset = sibling.getPreferredSpan(View.Y_AXIS) * sibling.getAlignment(View.Y_AXIS)
    baselineOffset = maxOf(baselineOffset, siblingBaselineOffset)
  }
  return baselineOffset
}

private fun View.getImageFontOffset(document: HTMLDocument, mode: ImageAlignment, input: ImageAlignmentInput?): Float {
  val cachedOffset = input?.fontOffset ?: Float.NaN
  if (!cachedOffset.isNaN()) return cachedOffset

  val component = container ?: return Float.NaN
  val font = getImageFont(document)
  val metrics = component.getFontMetrics(font) ?: return Float.NaN

  // CSS middle places the image centre half an x-height above the baseline.
  val offset = if (mode == ImageAlignment.MIDDLE) xHeightCache.get(FontRenderKey(font, metrics.fontRenderContext)) / 2
  else metrics.ascent.toFloat()
  if (input != null) input.fontOffset = offset
  return offset
}
