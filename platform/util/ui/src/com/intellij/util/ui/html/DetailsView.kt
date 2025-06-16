// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.SizeRequirements
import javax.swing.text.Element
import javax.swing.text.View
import kotlin.math.max

internal class DetailsView(elem: Element, axis: Int) : BlockViewEx(elem, axis) {

  override fun paintChild(g: Graphics?, alloc: Rectangle?, index: Int) {
    if (index > 0 && summaryView.let { it != null && !it.expanded }) return
    super.paintChild(g, alloc, index)
  }

  override fun calculateMajorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
    val v = summaryView
    return if (v == null || v.expanded)
      super.calculateMajorAxisRequirements(axis, r)
    else
      calculateSummaryRequirements(v, axis, r).apply { alignment = 0.5f }
  }

  override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
    val v = summaryView
    return if (v == null || v.expanded)
      super.calculateMinorAxisRequirements(axis, r)
    else
      calculateSummaryRequirements(v, axis, r)
  }

  override fun layoutMajorAxis(targetSpan: Int, axis: Int, offsets: IntArray, spans: IntArray) {
    super.layoutMajorAxis(targetSpan, axis, offsets, spans)
    if (axis == Y_AXIS && summaryView.let { it != null && !it.expanded } && spans.isNotEmpty()) {
      val lastOffset = spans[0] + offsets[0]
      for (counter in 1 until spans.size) {
        offsets[counter] = lastOffset
        spans[counter] = 0
      }
    }
  }

  private fun calculateSummaryRequirements(v: View, axis: Int, r: SizeRequirements?): SizeRequirements {
    val min = v.getMinimumSpan(axis).toInt()
    val pref = v.getPreferredSpan(axis).toInt()
    val max = max(v.getMaximumSpan(axis).toInt(), if (axis == Y_AXIS) 0 else Int.Companion.MAX_VALUE)
    return (r ?: SizeRequirements().apply { alignment = 0.5f }).apply {
      preferred = pref
      minimum = min
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

  private val summaryView: SummaryView?
    get() =
      if (viewCount > 0)
        getView(0) as? SummaryView
      else null

}