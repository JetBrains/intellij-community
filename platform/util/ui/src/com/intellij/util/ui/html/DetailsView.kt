// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.ScalableIcon
import com.intellij.util.asSafely
import com.intellij.util.ui.ExtendableHTMLViewFactory
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.Icon
import javax.swing.SizeRequirements
import javax.swing.text.AttributeSet
import javax.swing.text.Element
import javax.swing.text.View
import kotlin.math.max
import kotlin.math.min

internal class DetailsView(elem: Element, axis: Int) : BlockViewEx(elem, axis) {

  companion object {
    @JvmField
    val EXPANDED: Any = "expanded"
  }

  private var expanded: Boolean = false

  @ApiStatus.Internal
  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()
    expanded = element.attributes.getAttribute(HTML_Tag_DETAILS)
      ?.asSafely<AttributeSet>()?.getAttribute(EXPANDED) == true
  }

  override fun paintChild(g: Graphics, alloc: Rectangle, index: Int) {
    val summaryView = summaryView
    if (index > 0 && !expanded && summaryView != null) return
    super.paintChild(g, alloc, index)
    if (index == 0 && summaryView != null) {
      val g2d = g as Graphics2D
      val savedComposite = g2d.composite
      g2d.composite = AlphaComposite.SrcOver // support transparency
      val icon = chevronIcon
      val cssMargin = summaryView.cssMargin
      val cssPadding = summaryView.cssPadding
      val topInset = cssMargin.top + cssPadding.top + max(g.fontMetrics.height / 2 - icon.iconHeight / 2, 0)
      val leftInset = cssMargin.left + cssPadding.left
      icon.paintIcon(null, g, alloc.bounds.x + alloc.bounds.width - icon.iconWidth - leftInset, alloc.bounds.y + topInset)
      g2d.composite = savedComposite
    }
  }

  override fun calculateMajorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
    val v = summaryView
    return if (expanded || v == null)
      super.calculateMajorAxisRequirements(axis, r)
    else
      calculateSummaryRequirements(v, axis, r).apply { alignment = 0.5f }
  }

  override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
    val v = summaryView
    return if (expanded || v == null)
      super.calculateMinorAxisRequirements(axis, r)
    else
      calculateSummaryRequirements(v, axis, r)
  }

  override fun layoutMajorAxis(targetSpan: Int, axis: Int, offsets: IntArray, spans: IntArray) {
    super.layoutMajorAxis(targetSpan, axis, offsets, spans)
    if (axis == Y_AXIS && summaryView != null && spans.isNotEmpty()) {
      val lastOffset = spans[0] + offsets[0]
      for (counter in 1 until spans.size) {
        offsets[counter] = lastOffset
        spans[counter] = 0
      }
    }
  }

  override fun layoutMinorAxis(targetSpan: Int, axis: Int, offsets: IntArray, spans: IntArray) {
    super.layoutMinorAxis(targetSpan, axis, offsets, spans)
    if (axis == X_AXIS && summaryView != null && spans.isNotEmpty()) {
      val maxSpan = targetSpan - chevronIcon.iconWidth
      spans[0] = min(spans[0], maxSpan)
    }
  }

  private fun calculateSummaryRequirements(v: View, axis: Int, r: SizeRequirements?): SizeRequirements {
    val icon = chevronIcon
    val baseline = if (axis == Y_AXIS) icon.iconHeight else 0
    val min = max(v.getMinimumSpan(axis).toInt(), baseline)
    val pref = max(v.getPreferredSpan(axis).toInt(), baseline)
    val max = max(max(v.getMaximumSpan(axis).toInt(),  if (axis == Y_AXIS) 0 else Int.Companion.MAX_VALUE), baseline)

    val delta = if (axis == X_AXIS) icon.iconWidth else 0
    return (r ?: SizeRequirements().apply { alignment = 0.5f }).apply {
      preferred = pref + delta
      minimum = min + delta
      maximum = max
    }
  }

  override fun replace(index: Int, length: Int, elems: Array<out View>) {
    super.replace(index, length, elems)

    // Ensure that "summary" is the first view
    val firstSummaryIndex = (0 until viewCount).indexOfFirst { getView(it).element.name == "summary" }

    if (firstSummaryIndex > 0) {
      val summaryView = getView(firstSummaryIndex)
      super.replace(firstSummaryIndex, 1, arrayOf())
      super.replace(0, 0, arrayOf(summaryView))
    }
  }

  private val summaryView: View?
    get() =
      if (viewCount > 0)
        getView(0).takeIf { it.element.name == "summary" }
      else null

  private val chevronIcon: Icon
    get() {
      val icon = if (expanded)
        AllIcons.General.ChevronUp
      else
        AllIcons.General.ChevronDown
      val scaleFactor = container.asSafely<ExtendableHTMLViewFactory.ScaledHtmlJEditorPane>()?.contentsScaleFactor ?: 1f
      return if (icon is ScalableIcon && scaleFactor != 1f)
        icon.scale(scaleFactor)
      else
        icon
    }


}