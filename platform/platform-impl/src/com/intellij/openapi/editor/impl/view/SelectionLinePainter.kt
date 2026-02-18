// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.scale.JBUIScale.scale
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.math.abs

private const val EPSILON: Double = 1e-2

private sealed class CornerType(open val radius: Double) {
  object Straight : CornerType(0.0)
  class Rounded(override val radius: Double) : CornerType(radius)
  class InvertedRounded(override val radius: Double) : CornerType(radius)
}

private data class SelectionRectangle(
  val topLeft: Point2D,
  val bottomRight: Point2D,
)

private data class CaretSelection(
  val editor: EditorImpl,
  val start: VisualPosition,
  val end: VisualPosition,
  val originalEnd: VisualPosition = end,
) {
  fun contains(pos: Double): Boolean {
    val (startPos, endPos) = Pair(
      editor.visualPositionToPoint2D(start),
      editor.visualPositionToPoint2D(end)
    )

    return pos in (startPos.x - EPSILON..endPos.x + EPSILON)
  }
}

private operator fun VisualPosition.compareTo(other: VisualPosition): Int =
  compareValuesBy(this, other, { it.line }, { it.column })

private fun max(a: VisualPosition, b: VisualPosition): VisualPosition =
  if (a > b) a else b

private fun min(a: VisualPosition, b: VisualPosition): VisualPosition =
  if (a < b) a else b

private data class CaretLineSelectionProxy(
  private val start: Double,
  private val end: Double,
) {
  fun contains(pos: Double): Boolean = pos in (start - EPSILON..end + EPSILON)

  fun containsStrict(pos: Double): Boolean = pos in (start + EPSILON..end - EPSILON)
}

private data class CaretLineSelections(
  private val editor: EditorImpl,
  private val line: Int,
  private val selections: List<CaretSelection>,
  private val lineExtensionWidth: Double
) {
  private fun trimToLine(selection: CaretSelection): CaretSelection {
    val columnEnd = EditorUtil.getLastVisualLineColumnNumber(editor, line)
    return CaretSelection(
      editor,
      max(selection.start, VisualPosition(line, 0, selection.start.leansRight)),
      min(selection.end, VisualPosition(line, columnEnd, selection.end.leansRight))
    )
  }

  operator fun get(index: Int): CaretSelection = trimToLine(selections[index])

  private fun isRightExtended(): Boolean =
    !editor.isRightAligned && selections.lastOrNull()?.let { it.originalEnd.line > line } ?: false

  private fun isLeftExtended(): Boolean =
    editor.isRightAligned && selections.firstOrNull()?.let { it.start.line < line } ?: false

  private fun selectionBoundaries(index: Int): Pair<Double, Double> {
    val selection = this[index]
    val (rawStart, rawEnd) = Pair(
      editor.visualPositionToPoint2D(selection.start).x,
      editor.visualPositionToPoint2D(selection.end).x
    )

    return Pair(
      if (index == 0 && isLeftExtended()) rawStart - lineExtensionWidth else rawStart,
      if (index == selections.lastIndex && isRightExtended()) rawEnd + lineExtensionWidth else rawEnd
    )
  }

  fun hasSelectionEnd(left: Boolean, pos: Double): Boolean =
    selectionEndDistance(left, pos)?.let { abs(it) < EPSILON } ?: false

  fun selectionEndDistance(left: Boolean, pos: Double, precision: Double = EPSILON): Double? {
    if (selections.isEmpty()) return null

    var (lo, hi) = Pair(0, selections.size)
    while (lo + 1 != hi) {
      val mid = (lo + hi) / 2

      val (start, end) = selectionBoundaries(mid)

      val boundX = if (left) start else end
      if (boundX <= pos + precision) lo = mid else hi = mid
    }

    val (start, end) = selectionBoundaries(lo)
    val boundX = if (left) start else end
    val signedDistance = boundX - pos
    return signedDistance.takeIf { abs(it) < precision }
  }

  fun selectionContaining(pos: Double): CaretLineSelectionProxy? {
    if (selections.isEmpty()) return null

    var (lo, hi) = Pair(0, selections.size)
    while (lo + 1 != hi) {
      val mid = (lo + hi) / 2

      val (start, end) = selectionBoundaries(mid)

      if (end < pos - EPSILON) lo = mid // [_, end] visualPosition
      else if (start > pos + EPSILON) hi = mid // visualPosition [start, _]
      else return CaretLineSelectionProxy(start, end)
    }

    val (start, end) = selectionBoundaries(lo)
    return CaretLineSelectionProxy(start, end).takeIf { it.contains(pos) }
  }

  fun containsPosition(pos: Double, strict: Boolean): Boolean {
    return selectionContaining(pos)?.let { !strict || it.containsStrict(pos) } ?: false
  }
}

internal class SelectionLinePainter(
  private val graphics: Graphics2D,
  private val lineHeight: Int,
  private val yShift: Int,
  private val editor: EditorImpl,
  private val lineExtensionWidth: Double,
) {
  private val radius = scale(lineHeight / 6.0f).toDouble()
  private val selectionBg = editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)
  private val leftExtensionWidth = if (editor.isRightAligned) lineExtensionWidth else 0.0
  private val rightExtensionWidth = if (editor.isRightAligned) 0.0 else lineExtensionWidth

  private val caretSelections by lazy {
    editor.caretModel.allCarets.map { caret ->
      val originalEnd = caret.selectionEndPosition
      val end = originalEnd.let {
        if (it.column != 0) it
        else {
          val columnNumber = runCatching {
            EditorUtil.getLastVisualLineColumnNumber(editor, it.line - 1)
          }
          if (columnNumber.isFailure) it else
          VisualPosition(
            it.line - 1,
            EditorUtil.getLastVisualLineColumnNumber(editor, it.line - 1),
          )
        }
      }
      CaretSelection(editor, caret.selectionStartPosition, end, originalEnd)
    }.sortedWith(compareBy<CaretSelection> { it.start.line }.thenBy { it.start.column })
  }

  private val allCarets by lazy { editor.caretModel.allCarets }

  fun isCFRInSelection(cfr: CustomFoldRegion): Boolean = allCarets.any {
    it.selectionStart <= cfr.startOffset && cfr.endOffset <= it.selectionEnd
  }

  private fun isBlockInlayInSelection(block: Inlay<*>) = caretSelections.any {
    val bounds = block.bounds ?: return@any false
    val (start, end) = Pair(editor.visualPositionToXY(it.start), editor.visualPositionToXY(it.end))
    val selectedRange = start.y..end.y

    bounds.y in selectedRange && (bounds.y + bounds.height) in selectedRange
  }

  private val customFoldRegions by lazy {
    val foldingModel = editor.foldingModel
    val regions = foldingModel.fetchTopLevel() ?: emptyArray()

    regions.filterIsInstance<CustomFoldRegion>().filter { isCFRInSelection(it) }
  }

  private fun caretSelectionsForLine(line: Int): CaretLineSelections {
    if (caretSelections.isEmpty()) return CaretLineSelections(editor, line, emptyList(), lineExtensionWidth)

    val lower = caretSelections.binarySearch { if (it.end.line < line) -1 else 1 }.let { -it - 1 }
    val upper = caretSelections.binarySearch { if (it.start.line <= line) -1 else 1 }.let { -it - 1 }

    return CaretLineSelections(editor, line, caretSelections.subList(lower, upper), lineExtensionWidth)
  }

  private fun yToVisualLine(y: Int): Int {
    return editor.yToVisualLine(y - yShift)
  }

  private fun visualLineToY(visualLine: Int): Int {
    return editor.visualLineToY(visualLine) + yShift
  }

  private fun customFoldRegionsFor(visualLine: Int): List<CustomFoldRegion> {
    return customFoldRegions.filter { cfr ->
      val startLine = editor.offsetToVisualLine(cfr.startOffset)
      val endLine = editor.offsetToVisualLine(cfr.endOffset)
      visualLine in startLine..endLine
    }
  }

  private fun blockInlayAbove(visualLine: Int): Inlay<*>? =
    editor.inlayModel.getBlockElementsForVisualLine(visualLine, true).lastOrNull()
      ?: editor.inlayModel.getBlockElementsForVisualLine(visualLine - 1, false).lastOrNull()

  private fun selectedBlockInlayAbove(visualLine: Int): Inlay<*>? =
    blockInlayAbove(visualLine)?.takeIf { isBlockInlayInSelection(it) }

  private fun blockInlayBelow(visualLine: Int): Inlay<*>? =
    editor.inlayModel.getBlockElementsForVisualLine(visualLine, false).firstOrNull()
      ?: editor.inlayModel.getBlockElementsForVisualLine(visualLine + 1, true).lastOrNull()

  private fun selectedBlockInlayBelow(visualLine: Int): Inlay<*>? =
    blockInlayBelow(visualLine)?.takeIf { isBlockInlayInSelection(it) }

  private fun cfrEndDistance(cfr: List<CustomFoldRegion>, x: Double, left: Boolean, precision: Double): Double? {
    return cfr
      .map { region ->
        val startX = editor.offsetToXY(region.startOffset).x.toDouble()
        val endX = startX + region.widthInPixels

        val signedDistance = (if (left) startX else endX) - x
        signedDistance to abs(signedDistance)
      }
      .filter { (_, absDistance) -> absDistance < precision }
      .minByOrNull { (_, absDistance) -> absDistance }
      ?.first
  }

  private fun selectionEndDistanceAboveLine(visualLine: Int, x: Double, left: Boolean, checkBlockInlays: Boolean = true, precision: Double = EPSILON): Double? {
    if (visualLine <= 0) return null

    customFoldRegionsFor(visualLine - 1).let {
      if (it.isNotEmpty()) return cfrEndDistance(it, x, left, precision)
    }

    if (checkBlockInlays) {
      selectedBlockInlayAbove(visualLine)?.bounds2D?.let {
        val boundX = if (left) it.x else it.x + it.width
        val signedDistance = boundX - x
        return signedDistance.takeIf { d -> abs(d) < precision }
      }
    }

    return caretSelectionsForLine(visualLine - 1).selectionEndDistance(left, x, precision)
  }
  
  private fun selectionEndDistanceBelowLine(visualLine: Int, x: Double, left: Boolean, checkBlockInlays: Boolean = true, precision: Double = EPSILON): Double? {
    if (visualLine >= editor.view.visibleLineCount) return null

    customFoldRegionsFor(visualLine).let {
      if (it.isNotEmpty()) return cfrEndDistance(it, x, left, precision)
    }

    if (checkBlockInlays) {
      selectedBlockInlayBelow(visualLine - 1)?.bounds2D?.let {
        val boundX = if (left) it.x else it.x + it.width
        val signedDistance = boundX - x
        return signedDistance.takeIf { d -> abs(d) < precision }
      }
    }

    return caretSelectionsForLine(visualLine).selectionEndDistance(left, x, precision)
  }

  private fun bounds(left: Double, right: Double, strict: Boolean) =
    if (strict) (left + EPSILON..right - EPSILON) else (left - EPSILON..right + EPSILON)

  private fun hasSelectionAboveLine(
    visualLine: Int, x: Double,
    strict: Boolean = false,
    checkBlockInlays: Boolean = true
  ): Boolean {
    if (visualLine <= 0) return false

    customFoldRegionsFor(visualLine - 1).let {
      if (it.isNotEmpty()) return it.any { cfr ->
        val startX = editor.offsetToXY(cfr.startOffset).x.toDouble()
        val (left, right) = startX to startX + cfr.widthInPixels

        x in bounds(left, right, strict)
      }
    }

    if (checkBlockInlays) {
      selectedBlockInlayAbove(visualLine)?.bounds2D?.let {
        val (left, right) = it.x to it.x + it.width

        return x in bounds(left, right, strict)
      }
    }

    return caretSelectionsForLine(visualLine - 1).containsPosition(x, strict)
  }

  private fun hasSelectionAbove(p: Point2D, strict: Boolean = false, checkBlockInlays: Boolean = true): Boolean =
    hasSelectionAboveLine(yToVisualLine(p.y.toInt()), p.x, strict, checkBlockInlays)

  private fun hasSelectionBelowLine(
    visualLine: Int,
    x: Double,
    strict: Boolean = false,
    checkBlockInlays: Boolean = true
  ): Boolean {
    customFoldRegionsFor(visualLine).let {
      if (it.isNotEmpty()) return it.any { cfr ->
        val startX = editor.offsetToXY(cfr.startOffset).x.toDouble()
        val (left, right) = startX to startX + cfr.widthInPixels

        x in bounds(left, right, strict)
      }
    }

    if (checkBlockInlays) {
      selectedBlockInlayBelow(visualLine - 1)?.bounds2D?.let {
        val (left, right) = it.x to it.x + it.width

        return x in bounds(left, right, strict)
      }
    }

    return caretSelectionsForLine(visualLine).containsPosition(x, strict)
  }

  /// HACK: see `hasSelectionEndBelow`
  private fun hasSelectionBelow(p: Point2D, strict: Boolean, checkBlockInlays: Boolean = true): Boolean =
    hasSelectionBelowLine(yToVisualLine(p.y.toInt() - lineHeight) + 1, p.x, strict, checkBlockInlays)

  private fun isSelectionLeftBound(block: SelectionRectangle): Boolean {
    val visualLine = yToVisualLine(block.topLeft.y.toInt())
    return customFoldRegionsFor(visualLine).isNotEmpty() || caretSelectionsForLine(visualLine).hasSelectionEnd(true, block.topLeft.x)
  }

  private fun isSelectionRightBound(block: SelectionRectangle): Boolean {
    val visualLine = yToVisualLine(block.topLeft.y.toInt())
    return customFoldRegionsFor(visualLine).isNotEmpty() || caretSelectionsForLine(visualLine).hasSelectionEnd(false, block.bottomRight.x)
  }

  fun isLineInSelection(x: Float, y: Int, width: Float): Boolean {
    val line = yToVisualLine(y)
    if (y != visualLineToY(line)) return false

    val selection = caretSelectionsForLine(line).selectionContaining(x.toDouble()) ?: return false
    return selection.contains((x + width).toDouble())
  }

  private fun paintRoundedBlock(block: SelectionRectangle, cornerTypes: Array<CornerType>) {
    val (topLeftType, topRightType, bottomRightType, bottomLeftType) = cornerTypes

    val path = Path2D.Double()

    val topLeft = block.topLeft
    path.moveTo(topLeft.x, topLeft.y + radius)
    when (topLeftType) {
      is CornerType.Straight -> {
        path.lineTo(topLeft.x, topLeft.y)
        path.lineTo(topLeft.x + topLeftType.radius, topLeft.y)
      }
      is CornerType.InvertedRounded -> {
        path.quadTo(topLeft.x, topLeft.y, topLeft.x - topLeftType.radius, topLeft.y)
        path.lineTo(topLeft.x + topLeftType.radius, topLeft.y)
      }
      is CornerType.Rounded -> {
        path.quadTo(topLeft.x, topLeft.y, topLeft.x + topLeftType.radius, topLeft.y)
      }
    }

    val topRight = Point2D.Double(block.bottomRight.x, block.topLeft.y)

    path.lineTo(topRight.x - topRightType.radius, topRight.y)
    when (topRightType) {
      is CornerType.Straight -> {
        path.lineTo(topRight.x, topRight.y)
        path.lineTo(topRight.x, topRight.y + radius)
      }
      is CornerType.InvertedRounded -> {
        path.lineTo(topRight.x, topRight.y)
        path.lineTo(topRight.x, topRight.y + radius)
        path.quadTo(topRight.x, topRight.y, topRight.x + topRightType.radius, topRight.y)
        path.lineTo(topRight.x, topRight.y)
        path.lineTo(topRight.x, topRight.y + radius)
      }
      is CornerType.Rounded -> {
        path.quadTo(topRight.x, topRight.y, topRight.x, topRight.y + radius)
      }
    }

    val bottomRight = block.bottomRight
    path.lineTo(bottomRight.x, block.bottomRight.y - radius)
    when (bottomRightType) {
      is CornerType.Straight -> {
        path.lineTo(bottomRight.x, block.bottomRight.y)
        path.lineTo(bottomRight.x - bottomRightType.radius, block.bottomRight.y)
      }
      is CornerType.InvertedRounded -> {
        path.quadTo(bottomRight.x, block.bottomRight.y, bottomRight.x + bottomRightType.radius, block.bottomRight.y)
        path.lineTo(bottomRight.x - bottomRightType.radius, block.bottomRight.y)
      }
      is CornerType.Rounded -> {
        path.quadTo(bottomRight.x, block.bottomRight.y, bottomRight.x - bottomRightType.radius, block.bottomRight.y)
      }
    }

    val bottomLeft = Point2D.Double(block.topLeft.x, block.bottomRight.y)

    path.lineTo(bottomLeft.x + bottomLeftType.radius, block.bottomRight.y)
    when (bottomLeftType) {
      is CornerType.Straight -> {
        path.lineTo(bottomLeft.x, bottomLeft.y)
        path.lineTo(bottomLeft.x, bottomLeft.y - radius)
      }
      is CornerType.InvertedRounded -> {
        path.lineTo(bottomLeft.x, bottomLeft.y)
        path.lineTo(bottomLeft.x, bottomLeft.y - radius)
        path.quadTo(bottomLeft.x, bottomLeft.y, bottomLeft.x - bottomLeftType.radius, bottomLeft.y)
        path.lineTo(bottomLeft.x, bottomLeft.y)
        path.lineTo(bottomLeft.x, bottomLeft.y - radius)
      }
      is CornerType.Rounded -> {
        path.quadTo(bottomLeft.x, bottomLeft.y, bottomLeft.x, bottomLeft.y - radius)
      }
    }

    path.lineTo(topLeft.x, topLeft.y + topLeftType.radius)
    path.closePath()

    val oldAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    graphics.color = selectionBg
    graphics.fill(path)

    if (oldAA != null) {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA)
    }
    else {
      graphics.renderingHints.remove(RenderingHints.KEY_ANTIALIASING)
    }
  }

  private fun selectionEndDistanceAbove(p: Point2D, left: Boolean, checkBlockInlays: Boolean = true): Double? =
    selectionEndDistanceAboveLine(yToVisualLine(p.y.toInt()), p.x, left, checkBlockInlays, 2 * radius)

  /// HACK: we substract lineHeight and add 1 to get the next line because just running `yToVisualLine` will return the previous one
  /// if there are any inlays below this one so we jump to the beginning of the current line and add 1
  private fun selectionEndDistanceBelow(p: Point2D, left: Boolean, checkBlockInlays: Boolean = true): Double? =
    selectionEndDistanceBelowLine(yToVisualLine(p.y.toInt() - lineHeight) + 1, p.x, left, checkBlockInlays, 2 * radius)

  private fun cornerTypeForSelectionEnd(
    signedDistance: Double?,
    hasSelectionAdjacent: Boolean,
    isBound: Boolean,
    isLeftCorner: Boolean,
  ): CornerType {
    return when {
      !isBound -> CornerType.Straight
      signedDistance != null && abs(signedDistance) < EPSILON -> CornerType.Straight
      signedDistance != null -> {
        val radius = abs(signedDistance) / 2
        
        val thisLineExtendsFurther = if (isLeftCorner) signedDistance > 0 else signedDistance < 0
        if (thisLineExtendsFurther) CornerType.Rounded(radius) else CornerType.InvertedRounded(radius)
      }
      hasSelectionAdjacent -> CornerType.InvertedRounded(radius)
      else -> CornerType.Rounded(radius)
    }
  }

  private fun paintRoundedSelection(block: SelectionRectangle) {
    val (leftBound, rightBound) = Pair(isSelectionLeftBound(block), isSelectionRightBound(block))

    val topLeftDistance = selectionEndDistanceAbove(block.topLeft, left = true)
    val topLeftType = cornerTypeForSelectionEnd(
      topLeftDistance,
      hasSelectionAbove(block.topLeft, strict = true),
      leftBound,
      isLeftCorner = true,
    )

    val topRight = Point2D.Double(block.bottomRight.x, block.topLeft.y)
    val topRightDistance = selectionEndDistanceAbove(topRight, left = false)
    val topRightType = cornerTypeForSelectionEnd(
      topRightDistance,
      hasSelectionAbove(topRight, strict = true),
      rightBound,
      isLeftCorner = false,
    )

    val bottomRightDistance = selectionEndDistanceBelow(block.bottomRight, left = false)
    val bottomRightType = cornerTypeForSelectionEnd(
      bottomRightDistance,
      hasSelectionBelow(block.bottomRight, strict = true),
      rightBound,
      isLeftCorner = false,
    )

    val bottomLeft = Point2D.Double(block.topLeft.x, block.bottomRight.y)
    val bottomLeftDistance = selectionEndDistanceBelow(bottomLeft, left = true)
    val bottomLeftType = cornerTypeForSelectionEnd(
      bottomLeftDistance,
      hasSelectionBelow(bottomLeft, strict = true),
      leftBound,
      isLeftCorner = true,
    )

    paintRoundedBlock(block, arrayOf(topLeftType, topRightType, bottomRightType, bottomLeftType))
  }

  private val Inlay<*>.bounds2D: Rectangle2D get() {
    return bounds?.let {
      val start = it.x.toDouble() - leftExtensionWidth
      val end = it.x.toDouble() + it.width + rightExtensionWidth
      Rectangle2D.Double(
        start,
        it.y.toDouble() + yShift,
        end - start,
        it.height.toDouble()
      )
    } ?: throw IllegalStateException("Inlay should not be folded")
  }

  private fun cornerTypesForFirstBlockInlay(
    bottomVisualLine: Int,
    bounds: Rectangle2D,
  ): Pair<CornerType, CornerType> {
    val (startX, endX) = Pair(bounds.x, bounds.x + bounds.width)

    val topLeftDistance = selectionEndDistanceAboveLine(bottomVisualLine, startX, left = true, checkBlockInlays = false, precision = 2 * radius)
    val topLeftType = cornerTypeForSelectionEnd(
      topLeftDistance,
      hasSelectionAboveLine(bottomVisualLine, startX, strict = true, checkBlockInlays = false),
      isBound = true,
      isLeftCorner = true,
    )

    val topRightDistance = selectionEndDistanceAboveLine(bottomVisualLine, endX, left = false, checkBlockInlays = false, precision = 2 * radius)
    val topRightType = cornerTypeForSelectionEnd(
      topRightDistance,
      hasSelectionAboveLine(bottomVisualLine, endX, strict = true, checkBlockInlays = false),
      isBound = true,
      isLeftCorner = false,
    )

    return Pair(topLeftType, topRightType)
  }

  private fun cornerTypesForLastBlockInlay(
    bottomVisualLine: Int,
    bounds: Rectangle2D,
  ): Pair<CornerType, CornerType> {
    val (startX, endX) = Pair(bounds.x, bounds.x + bounds.width)

    val bottomLeftDistance = selectionEndDistanceBelowLine(bottomVisualLine, startX, left = true, checkBlockInlays = false, precision = 2 * radius)
    val bottomLeftType = cornerTypeForSelectionEnd(
      bottomLeftDistance,
      hasSelectionBelowLine(bottomVisualLine, startX, strict = true, checkBlockInlays = false),
      isBound = true,
      isLeftCorner = true,
    )

    val bottomRightDistance = selectionEndDistanceBelowLine(bottomVisualLine, endX, left = false, checkBlockInlays = false, precision = 2 * radius)
    val bottomRightType = cornerTypeForSelectionEnd(
      bottomRightDistance,
      hasSelectionBelowLine(bottomVisualLine, endX, strict = true, checkBlockInlays = false),
      isBound = true,
      isLeftCorner = false,
    )

    return Pair(bottomLeftType, bottomRightType)
  }

  private fun allBlockInlaysAbove(bottomVisualLine: Int): List<Inlay<*>> =
    editor.inlayModel.getBlockElementsForVisualLine(bottomVisualLine - 1, false) +
      editor.inlayModel.getBlockElementsForVisualLine(bottomVisualLine, true)

  fun isAllBlockInlaysAboveSelected(bottomVisualLine: Int): Boolean =
    allBlockInlaysAbove(bottomVisualLine).all { isBlockInlayInSelection(it) }

  fun paintAllBlockInlaysAbove(bottomVisualLine: Int) {
    val allInlays = allBlockInlaysAbove(bottomVisualLine)
    assert(allInlays.isNotEmpty()) { "There should be at least one block inlay" }

    val (firstInlay, lastInlay) = allInlays.run { Pair(first(), last()) }

    val (firstTopLeftType, firstTopRightType) = cornerTypesForFirstBlockInlay(
      bottomVisualLine,
      firstInlay.bounds2D
    )

    val (lastBottomLeftType, lastBottomRightType) = cornerTypesForLastBlockInlay(
      bottomVisualLine,
      lastInlay.bounds2D
    )

    val cornerTypesFor = { bounds: Rectangle2D, otherBounds: Rectangle2D ->
      val leftSignedDistance = otherBounds.x - bounds.x
      val leftType = when {
        abs(leftSignedDistance) < EPSILON -> CornerType.Straight
        abs(leftSignedDistance) < 2 * radius -> {
          val radius = abs(leftSignedDistance) / 2
          if (leftSignedDistance > 0) CornerType.Rounded(radius) else CornerType.InvertedRounded(radius)
        }
        bounds.x in (otherBounds.x + EPSILON..otherBounds.x + otherBounds.width - EPSILON) -> CornerType.InvertedRounded(radius)
        else -> CornerType.Rounded(radius)
      }

      val rightX = bounds.x + bounds.width
      val otherRightX = otherBounds.x + otherBounds.width
      val rightSignedDistance = otherRightX - rightX
      val rightType = when {
        abs(rightSignedDistance) < EPSILON -> CornerType.Straight
        abs(rightSignedDistance) < 2 * radius -> {
          val radius = abs(rightSignedDistance) / 2
          if (rightSignedDistance < 0) CornerType.Rounded(radius) else CornerType.InvertedRounded(radius)
        }
        rightX in (otherBounds.x + EPSILON..otherBounds.x + otherBounds.width - EPSILON) -> CornerType.InvertedRounded(radius)
        else -> CornerType.Rounded(radius)
      }

      Pair(leftType, rightType)
    }

    for ((idx, inlay) in allInlays.withIndex()) {
      val bounds = inlay.bounds2D

      val (topLeftType, topRightType) = when (idx) {
        0 -> firstTopLeftType to firstTopRightType
        else -> cornerTypesFor(bounds, allInlays[idx - 1].bounds2D)
      }

      val (bottomLeftType, bottomRightType) = when (idx) {
        allInlays.lastIndex -> lastBottomLeftType to lastBottomRightType
        else -> cornerTypesFor(bounds, allInlays[idx + 1].bounds2D)
      }

      val block = SelectionRectangle(
        topLeft = Point2D.Double(bounds.x, bounds.y),
        bottomRight = Point2D.Double(bounds.x + bounds.width, bounds.y + bounds.height),
      )

      paintRoundedBlock(
        block,
        arrayOf(topLeftType, topRightType, bottomRightType, bottomLeftType),
      )
    }
  }

  private fun paint(rect: Rectangle2D) {
    if (Registry.`is`("editor.old.full.horizontal.selection.enabled") || editor.isColumnMode) {
      LOG.error("Using the new selection painting is disabled or editor is in column mode but SelectionLinePainter.paint was called, proceeding with caution")
      EditorPainter.fillRectExact(
        graphics,
        rect,
        selectionBg
      )
      return
    }

    val selectionRect = SelectionRectangle(
      topLeft = Point2D.Double(rect.x, rect.y),
      bottomRight = Point2D.Double(rect.x + rect.width, rect.y + rect.height),
    )
    paintRoundedSelection(selectionRect)
  }

  private var currentRect: Rectangle2D? = null
  fun flush() {
    currentRect?.let { paint(it) }
    currentRect = null
  }

  fun paintSelection(rect: Rectangle2D) {
    currentRect?.let { if (abs(it.y - rect.y) > EPSILON) flush() }

    val intersects = currentRect?.run { x + width + EPSILON >= rect.x } ?: false
    if (!intersects) {
      currentRect?.let { paint(it) }
      currentRect = null
    }

    currentRect = currentRect?.createUnion(rect) ?: rect
  }
}

private val LOG = logger<SelectionLinePainter>()
