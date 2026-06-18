// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.scale.JBUIScale.scale
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.util.TreeSet
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

private const val EPSILON: Double = 1e-2

private sealed class CornerType(open val radius: Double) {
  object Straight : CornerType(0.0)
  class Rounded(override val radius: Double) : CornerType(radius)
  class InvertedRounded(override val radius: Double) : CornerType(radius)
}

private data class AxisBounds(val start: Double, val end: Double) : Comparable<AxisBounds> {
  override fun compareTo(other: AxisBounds): Int {
    return compareValuesBy(this, other, { it.start }, { it.end })
  }
}

internal abstract class IntervalSet<T : Comparable<T>> {
  val items: TreeSet<T> = TreeSet()

  abstract fun add(item: T)

  protected abstract fun getStart(item: T): Double
  protected abstract fun getEnd(item: T): Double

  fun withAllIntersecting(
    start: Double,
    end: Double,
    probe: T,
    action: (MutableIterator<T>, T) -> Unit,
  ) {
    val first = items.floor(probe) ?: items.ceiling(probe) ?: return

    val it = items.tailSet(first, true).iterator()
    while (it.hasNext()) {
      val current = it.next()
      if (getEnd(current) <= start) continue
      if (getStart(current) >= end) break

      action(it, current)
    }
  }
}

private class SelectionLine(val boundsY: AxisBounds) : IntervalSet<AxisBounds>(), Comparable<SelectionLine> {
  override fun getStart(item: AxisBounds): Double = item.start
  override fun getEnd(item: AxisBounds): Double = item.end

  override fun add(item: AxisBounds) {
    var (start, end) = item

    val existing = items.floor(item) ?: items.ceiling(item)
    if (existing == null) {
      items.add(item)
      return
    }

    val it = items.tailSet(existing).iterator()
    while (it.hasNext()) {
      val current = it.next()
      if (current.end < start - EPSILON) continue
      if (current.start > end + EPSILON) break

      start = min(start, current.start)
      end = max(end, current.end)
      it.remove()
    }
    items.add(AxisBounds(start, end))
  }

  override fun compareTo(other: SelectionLine): Int {
    return compareValuesBy(this, other) { it.boundsY }
  }

  fun withAllIntersectingBlocks(
    xStart: Double,
    xEnd: Double,
    blockAction: (MutableIterator<AxisBounds>, AxisBounds) -> Unit,
  ) {
    withAllIntersecting(xStart, xEnd, AxisBounds(xStart, xStart), blockAction)
  }

  fun invalidateSegment(xStart: Double, xEnd: Double) {
    val toAdd = buildList {
      withAllIntersectingBlocks(xStart, xEnd) { it, block ->
        it.remove()

        if (block.start < xStart) add(AxisBounds(block.start, xStart))
        if (block.end > xEnd) add(AxisBounds(xEnd, block.end))
      }
    }
    items.addAll(toAdd)
  }

  fun containsX(x: Double, strict: Boolean): Boolean {
    val probe = AxisBounds(x, x)
    return listOfNotNull(items.floor(probe), items.ceiling(probe)).any { (start, end) ->
      if (strict) {
        x > start + EPSILON && x < end - EPSILON
      } else {
        x >= start - EPSILON && x <= end + EPSILON
      }
    }
  }

  fun nearestStartDistance(x: Double, precision: Double): Double? = nearestEdgeDistance(x, precision) { it.start }
  fun nearestEndDistance(x: Double, precision: Double): Double? = nearestEdgeDistance(x, precision) { it.end }

  private fun nearestEdgeDistance(x: Double, precision: Double, edge: (AxisBounds) -> Double): Double? {
    val probe = AxisBounds(x, x)
    return sequenceOf(items.lower(probe), items.floor(probe), items.ceiling(probe), items.higher(probe))
      .filterNotNull()
      .map { edge(it) - x }
      .filter { abs(it) < precision }
      .minByOrNull { abs(it) }
  }
}

private class LineSet : IntervalSet<SelectionLine>() {
  override fun getStart(item: SelectionLine): Double = item.boundsY.start
  override fun getEnd(item: SelectionLine): Double = item.boundsY.end

  override fun add(item: SelectionLine) {
    val existing = items.ceiling(item)
    if (existing != null && existing.boundsY == item.boundsY) {
      existing.add(item.items.first())
    }
    else {
      items.add(item)
    }
  }

  fun lineAbove(line: SelectionLine): SelectionLine? {
    val candidate = items.lower(line) ?: return null
    return candidate.takeIf { abs(it.boundsY.end - line.boundsY.start) < EPSILON }
  }

  fun lineBelow(line: SelectionLine): SelectionLine? {
    val candidate = items.higher(line) ?: return null
    return candidate.takeIf { abs(it.boundsY.start - line.boundsY.end) < EPSILON }
  }
}

internal class SelectionVisualModel(
  private val editor: EditorImpl,
) {
  var yShift: Int = 0

  private val selectionBg get() = editor.selectionModel.textAttributes.backgroundColor
  private val lines = LineSet()

  fun paintBlock(block: Rectangle2D) {
    val yStart = block.y - yShift
    val singleton = SelectionLine(AxisBounds(yStart, yStart + block.height))
    singleton.add(AxisBounds(block.x, block.x + block.width))

    lines.add(singleton)
  }

  fun invalidateArea(area: Rectangle2D) {
    val yStart = area.y - yShift
    val yEnd = area.y + area.height - yShift
    val xStart = area.x
    val xEnd = area.x + area.width

    val lineProbe = SelectionLine(AxisBounds(yStart, yStart))
    lines.withAllIntersecting(yStart, yEnd, lineProbe) { _, line ->
      line.invalidateSegment(xStart, xEnd)
    }
  }

  fun paint(graphics: Graphics2D, clip: Rectangle2D, lineHeight: Int) {
    val (yStart, yEnd) = Pair(clip.y, clip.y + clip.height)
    val (xStart, xEnd) = Pair(clip.x, clip.x + clip.width)

    val oldAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.color = selectionBg
    graphics.translate(0.0, yShift.toDouble())
    try {
      val clipProbe = SelectionLine(AxisBounds(yStart, yStart))
      lines.withAllIntersecting(yStart, yEnd, clipProbe) { _, line ->
        line.withAllIntersectingBlocks(xStart, xEnd) { _, block ->
          paintRoundedSelection(graphics, line, block, lineHeight)
        }
      }
    }
    finally {
      graphics.translate(0.0, -yShift.toDouble())
      if (oldAA != null) graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA)
      else graphics.renderingHints.remove(RenderingHints.KEY_ANTIALIASING)
    }
  }

  private fun paintRoundedSelection(
    graphics: Graphics2D,
    line: SelectionLine,
    block: AxisBounds,
    lineHeight: Int,
  ) {
    val (top, bottom) = line.boundsY
    val (left, right) = block
    val radius = scale((lineHeight.toDouble() / 6.0).toFloat()).toDouble()
    val precision = 2 * radius

    val above = lines.lineAbove(line)
    val below = lines.lineBelow(line)

    val topLeftType = cornerType(
      above?.nearestStartDistance(left, precision),
      above?.containsX(left, strict = true) ?: false,
      isLeftCorner = true, radius,
    )
    val topRightType = cornerType(
      above?.nearestEndDistance(right, precision),
      above?.containsX(right, strict = true) ?: false,
      isLeftCorner = false, radius,
    )
    val bottomRightType = cornerType(
      below?.nearestEndDistance(right, precision),
      below?.containsX(right, strict = true) ?: false,
      isLeftCorner = false, radius,
    )
    val bottomLeftType = cornerType(
      below?.nearestStartDistance(left, precision),
      below?.containsX(left, strict = true) ?: false,
      isLeftCorner = true, radius,
    )

    paintRoundedBlock(graphics, left, top, right, bottom, radius, topLeftType, topRightType, bottomRightType, bottomLeftType)
  }

  private fun cornerType(
    signedDistance: Double?,
    hasSelectionAdjacent: Boolean,
    isLeftCorner: Boolean,
    radius: Double,
  ): CornerType {
    return when {
      signedDistance != null && abs(signedDistance) < EPSILON -> CornerType.Straight
      signedDistance != null -> {
        val cornerRadius = abs(signedDistance) / 2
        val thisLineExtendsFurther = if (isLeftCorner) signedDistance > 0 else signedDistance < 0
        if (thisLineExtendsFurther) CornerType.Rounded(cornerRadius) else CornerType.InvertedRounded(cornerRadius)
      }
      hasSelectionAdjacent -> CornerType.InvertedRounded(radius)
      else -> CornerType.Rounded(radius)
    }
  }

  private fun paintRoundedBlock(
    graphics: Graphics2D,
    left: Double, top: Double, right: Double, bottom: Double,
    radius: Double,
    topLeftType: CornerType, topRightType: CornerType, bottomRightType: CornerType, bottomLeftType: CornerType,
  ) {
    val path = Path2D.Double()

    path.moveTo(left, top + radius)
    when (topLeftType) {
      is CornerType.Straight -> {
        path.lineTo(left, top)
        path.lineTo(left + topLeftType.radius, top)
      }
      is CornerType.InvertedRounded -> {
        path.quadTo(left, top, left - topLeftType.radius, top)
        path.lineTo(left + topLeftType.radius, top)
      }
      is CornerType.Rounded -> {
        path.quadTo(left, top, left + topLeftType.radius, top)
      }
    }

    path.lineTo(right - topRightType.radius, top)
    when (topRightType) {
      is CornerType.Straight -> {
        path.lineTo(right, top)
        path.lineTo(right, top + radius)
      }
      is CornerType.InvertedRounded -> {
        path.lineTo(right, top)
        path.lineTo(right, top + radius)
        path.quadTo(right, top, right + topRightType.radius, top)
        path.lineTo(right, top)
        path.lineTo(right, top + radius)
      }
      is CornerType.Rounded -> {
        path.quadTo(right, top, right, top + radius)
      }
    }

    path.lineTo(right, bottom - radius)
    when (bottomRightType) {
      is CornerType.Straight -> {
        path.lineTo(right, bottom)
        path.lineTo(right - bottomRightType.radius, bottom)
      }
      is CornerType.InvertedRounded -> {
        path.quadTo(right, bottom, right + bottomRightType.radius, bottom)
        path.lineTo(right - bottomRightType.radius, bottom)
      }
      is CornerType.Rounded -> {
        path.quadTo(right, bottom, right - bottomRightType.radius, bottom)
      }
    }

    path.lineTo(left + bottomLeftType.radius, bottom)
    when (bottomLeftType) {
      is CornerType.Straight -> {
        path.lineTo(left, bottom)
        path.lineTo(left, bottom - radius)
      }
      is CornerType.InvertedRounded -> {
        path.lineTo(left, bottom)
        path.lineTo(left, bottom - radius)
        path.quadTo(left, bottom, left - bottomLeftType.radius, bottom)
        path.lineTo(left, bottom)
        path.lineTo(left, bottom - radius)
      }
      is CornerType.Rounded -> {
        path.quadTo(left, bottom, left, bottom - radius)
      }
    }

    path.lineTo(left, top + topLeftType.radius)
    path.closePath()

    graphics.fill(path)
  }
}