// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.longs.LongArrayList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The outline of the background of one style.
 *
 * The outline is a pure function of the rows. It holds no editor, no graphics and no state between two calls.
 */
@ApiStatus.Internal
object BackgroundOutline {
  /**
   * Gives one path that fills the union of the rectangles of [styleId].
   *
   * The path needs the rule [Path2D.WIND_NON_ZERO], which leaves a hole empty.
   * Each corner gets the radius [radius], limited to one half of each of its two edges.
   * A concave corner and a convex corner take the same radius.
   * Every corner rounds, also the corner at the viewport edge of a background that fills the line.
   */
  @JvmStatic
  fun build(rows: BackgroundRows, styleId: Int, radius: Double): Path2D {
    val grid = OutlineGrid(rows, styleId)
    val path = Path2D.Double(Path2D.WIND_NON_ZERO)
    for (cycle in grid.trace()) {
      grid.appendCycle(path, cycle, radius)
    }
    return path
  }

  /**
   * Gives the boundary of the union of the rectangles of [styleId] as closed cycles of corners.
   *
   * The interior lies on the right of each edge. An outer cycle therefore turns clockwise on the screen,
   * and a hole turns counterclockwise. Two shapes that touch at one corner give two cycles.
   * A cycle holds no collinear corner, and it holds its last corner once.
   */
  @JvmStatic
  @TestOnly
  fun cycles(rows: BackgroundRows, styleId: Int): List<List<Point2D.Double>> {
    val grid = OutlineGrid(rows, styleId)
    return grid.trace().map { cycle -> cycle.map { corner -> grid.point(corner) } }
  }

  /**
   * Gives the background of [styleId] as rectangles to fill at the device pixels and corner pieces to fill with
   * antialiasing.
   *
   * The coordinates are aligned to the pixels of [device]. Together the pieces cover the shape of [build] for the aligned
   * rows, with the same corners. An edge too short for two corner squares of whole device pixels can change a pixel in
   * part.
   */
  @JvmStatic
  fun split(rows: BackgroundRows, styleId: Int, radius: Double, device: DeviceGrid): BackgroundPieces {
    return OutlineGrid(rows, styleId, device).split(radius, device)
  }
}

/**
 * The background of one style in two parts.
 *
 * [rectangles] holds four values for each rectangle: the left, top, right and bottom side. A paint fills them at the
 * device pixels. A paint fills each path of [corners] with antialiasing. Two paths keep at least one device pixel apart
 * along y, so that separate fills give the edges of one fill.
 */
@ApiStatus.Internal
class BackgroundPieces(
  val rectangles: DoubleArray,
  val corners: List<Path2D>,
)

/**
 * The rectangles of one style on the grid of their own coordinates.
 *
 * The distinct x coordinates give the cells of a row, and the distinct y coordinates give the bands.
 * A band is covered where a rectangle covers it, so the grid holds the union of the rectangles.
 * A corner is one point of the grid, and the code keeps it as the index pair `yIndex * xs.size + xIndex`.
 */
private class OutlineGrid(rows: BackgroundRows, styleId: Int, private val device: DeviceGrid? = null) {
  private val xs: DoubleArray
  private val ys: DoubleArray
  private val cellCount: Int
  private val bandCount: Int
  private val covered: BooleanArray
  private val runSquares = ArrayList<CornerPiece>()
  private var cuts = DoubleArray(18)
  private var holeStarts = DoubleArray(8)
  private var holeEnds = DoubleArray(8)

  init {
    var intervals = 0
    for (index in 0 until rows.size) {
      if (rows.styleId(index) == styleId) {
        intervals++
      }
    }
    val xValues = DoubleArray(intervals * 2)
    val yValues = DoubleArray(intervals * 2)
    var next = 0
    for (index in 0 until rows.size) {
      if (rows.styleId(index) != styleId) {
        continue
      }
      xValues[next] = alignedX(rows.start(index))
      xValues[next + 1] = alignedX(rows.end(index))
      yValues[next] = alignedY(rows.top(index))
      yValues[next + 1] = alignedY(rows.bottom(index))
      next += 2
    }
    xs = canonicalValues(xValues)
    ys = canonicalValues(yValues)
    cellCount = if (xs.size > 1) xs.size - 1 else 0
    bandCount = if (ys.size > 1) ys.size - 1 else 0
    covered = BooleanArray(cellCount * bandCount)
    for (index in 0 until rows.size) {
      if (rows.styleId(index) == styleId) {
        cover(rows, index)
      }
    }
  }

  fun point(corner: Int): Point2D.Double {
    return Point2D.Double(xOf(corner), yOf(corner))
  }

  /**
   * Gives the cycles of the boundary. Each one holds its corners in the order of the trace.
   */
  fun trace(): List<IntArray> {
    val edges = buildEdges()
    val used = BooleanArray(edges.size)
    val cycles = ArrayList<IntArray>()
    for (start in edges.indices) {
      if (!used[start]) {
        cycles.add(traceCycle(edges, used, start))
      }
    }
    return cycles
  }

  fun appendCycle(path: Path2D.Double, cycle: IntArray, radius: Double) {
    if (cycle.size < 3) {
      return
    }
    val radii = DoubleArray(cycle.size)
    for (index in cycle.indices) {
      radii[index] = cornerRadius(cycle, index, radius)
    }
    val outgoing = directionOf(cycle[0], cycle[1])
    val x = xOf(cycle[0]) + radii[0] * dx(outgoing)
    val y = yOf(cycle[0]) + radii[0] * dy(outgoing)
    path.moveTo(x, y)
    for (index in 1 until cycle.size) {
      appendCorner(path, cycle, index, radii[index])
    }
    appendCorner(path, cycle, 0, radii[0])
    path.closePath()
  }

  private fun appendCorner(path: Path2D.Double, cycle: IntArray, index: Int, radius: Double) {
    val x = xOf(cycle[index])
    val y = yOf(cycle[index])
    val incoming = directionOf(cycle[(index + cycle.size - 1) % cycle.size], cycle[index])
    path.lineTo(x - radius * dx(incoming), y - radius * dy(incoming))
    if (radius <= 0) {
      return
    }
    val outgoing = directionOf(cycle[index], cycle[(index + 1) % cycle.size])
    path.quadTo(
      x,
      y,
      x + radius * dx(outgoing),
      y + radius * dy(outgoing),
    )
  }

  private fun cornerRadius(cycle: IntArray, index: Int, radius: Double): Double {
    val incoming = edgeLength(cycle[(index + cycle.size - 1) % cycle.size], cycle[index])
    val outgoing = edgeLength(cycle[index], cycle[(index + 1) % cycle.size])
    return min(radius, min(incoming, outgoing) / 2)
  }

  private fun traceCycle(edges: LongArray, used: BooleanArray, start: Int): IntArray {
    var corners = IntArray(8)
    var size = 0
    var edge = start
    while (true) {
      used[edge] = true
      if (size == corners.size) {
        corners = corners.copyOf(size * 2)
      }
      corners[size] = tailOf(edges[edge])
      size++
      val next = nextEdge(edges, used, edge, start)
      if (next == start) {
        break
      }
      edge = next
    }
    return withoutCollinear(corners, size)
  }

  /**
   * Gives the edge that continues the cycle of [edge].
   * Four edges meet where two shapes touch at one corner. The outgoing edge that turns right keeps the two apart.
   */
  private fun nextEdge(edges: LongArray, used: BooleanArray, edge: Int, start: Int): Int {
    val head = headOf(edges[edge])
    val incoming = directionOf(tailOf(edges[edge]), head)
    var best = -1
    var bestRank = Int.MAX_VALUE
    var index = firstEdgeFrom(edges, head)
    while (index < edges.size && tailOf(edges[index]) == head) {
      if (index == start || !used[index]) {
        val turn = (directionOf(head, headOf(edges[index])) - incoming + 4) % 4
        if (TURN_RANK[turn] < bestRank) {
          bestRank = TURN_RANK[turn]
          best = index
        }
      }
      index++
    }
    require(best >= 0) { "the boundary of the style has an open cycle" }
    return best
  }

  private fun withoutCollinear(corners: IntArray, size: Int): IntArray {
    val kept = IntArray(size)
    var count = 0
    for (index in 0 until size) {
      val previous = corners[(index + size - 1) % size]
      val current = corners[index]
      val next = corners[(index + 1) % size]
      if (directionOf(previous, current) != directionOf(current, next)) {
        kept[count] = current
        count++
      }
    }
    return kept.copyOf(count)
  }

  /**
   * Gives the edges of the boundary, sorted by their tail corner.
   */
  private fun buildEdges(): LongArray {
    val edges = LongArrayList()
    for (boundary in 0..bandCount) {
      appendHorizontalEdges(edges, boundary)
    }
    for (band in 0 until bandCount) {
      appendVerticalEdges(edges, band)
    }
    val result = edges.toLongArray()
    Arrays.sort(result)
    return result
  }

  /**
   * Adds the horizontal edges at the y coordinate [boundary].
   * A part that the band below covers alone is a top edge, and it runs to the right, with the interior below it.
   */
  private fun appendHorizontalEdges(edges: LongArrayList, boundary: Int) {
    var cell = 0
    while (cell < cellCount) {
      val below = isCovered(boundary, cell)
      val above = isCovered(boundary - 1, cell)
      if (below == above) {
        cell++
        continue
      }
      var end = cell + 1
      while (end < cellCount &&
             isCovered(boundary, end) == below &&
             isCovered(boundary - 1, end) == above) {
        end++
      }
      if (below) {
        val tail = cornerOf(cell, boundary)
        val head = cornerOf(end, boundary)
        edges.add(edgeOf(tail, head))
      } else {
        val tail = cornerOf(end, boundary)
        val head = cornerOf(cell, boundary)
        edges.add(edgeOf(tail, head))
      }
      cell = end
    }
  }

  /**
   * Adds the vertical edges of the band [band].
   * The left side of a covered part runs up, and the right side runs down, so the interior stays on the right.
   */
  private fun appendVerticalEdges(edges: LongArrayList, band: Int) {
    for (xIndex in 0..cellCount) {
      val right = isCovered(band, xIndex)
      val left = isCovered(band, xIndex - 1)
      if (right == left) {
        continue
      }
      if (right) {
        val tail = cornerOf(xIndex, band + 1)
        val head = cornerOf(xIndex, band)
        edges.add(edgeOf(tail, head))
      } else {
        val tail = cornerOf(xIndex, band)
        val head = cornerOf(xIndex, band + 1)
        edges.add(edgeOf(tail, head))
      }
    }
  }

  /**
   * Splits the shape into rectangles at the device pixels of [device] and antialiased corner pieces.
   * A convex corner leaves a square of whole device pixels out of the rectangles, at most one half of each of its edges.
   * A concave corner adds its fillet outside the rectangles.
   */
  fun split(radius: Double, device: DeviceGrid): BackgroundPieces {
    val pieces = ArrayList<CornerPiece>()
    for (cycle in trace()) {
      if (cycle.size < 3) {
        continue
      }
      for (index in cycle.indices) {
        val piece = cornerPiece(cycle, index, radius, device)
        if (piece != null) {
          pieces.add(piece)
        }
      }
    }
    return BackgroundPieces(rectanglesWithout(pieces), cornerPaths(pieces, device))
  }

  /**
   * Gives the piece of the corner [index] of [cycle], or null when the corner gets no radius.
   */
  private fun cornerPiece(cycle: IntArray, index: Int, radius: Double, device: DeviceGrid): CornerPiece? {
    val cornerRadius = cornerRadius(cycle, index, radius)
    if (cornerRadius <= 0) {
      return null
    }
    val previous = cycle[(index + cycle.size - 1) % cycle.size]
    val corner = cycle[index]
    val next = cycle[(index + 1) % cycle.size]
    val incoming = directionOf(previous, corner)
    val outgoing = directionOf(corner, next)
    var back = 0.0
    var forth = 0.0
    if ((outgoing - incoming + 4) % 4 == RIGHT_TURN) {
      back = min(wholePixels(device, incoming, cornerRadius), edgeLength(previous, corner) / 2)
      forth = min(wholePixels(device, outgoing, cornerRadius), edgeLength(corner, next) / 2)
    }
    return CornerPiece(xOf(corner), yOf(corner), cornerRadius, incoming, outgoing, back, forth)
  }

  private fun wholePixels(device: DeviceGrid, direction: Int, length: Double): Double {
    if (direction == LEFT || direction == RIGHT) {
      return device.wholePixelsX(length)
    }
    return device.wholePixelsY(length)
  }

  /**
   * Puts the [pieces] into paths. Two paths keep at least one device pixel apart along y.
   */
  private fun cornerPaths(pieces: List<CornerPiece>, device: DeviceGrid): List<Path2D> {
    val paths = ArrayList<Path2D>()
    var path: Path2D.Double? = null
    var pathBottom = 0.0
    for (piece in pieces.sortedBy { it.top }) {
      if (path == null || piece.top > pathBottom + device.pixelHeight) {
        path = Path2D.Double(Path2D.WIND_NON_ZERO)
        paths.add(path)
        pathBottom = piece.bottom
      } else {
        pathBottom = max(pathBottom, piece.bottom)
      }
      piece.appendTo(path)
    }
    return paths
  }

  /**
   * Gives the covered cells as rectangles, one run of cells for each band, without the squares of the [pieces].
   */
  private fun rectanglesWithout(pieces: List<CornerPiece>): DoubleArray {
    val squaresByBand = arrayOfNulls<ArrayList<CornerPiece>>(bandCount)
    for (piece in pieces) {
      if (!piece.hasSquare) {
        continue
      }
      var band = bandAt(piece.squareTop)
      while (band < bandCount && ys[band] < piece.squareBottom - EPSILON) {
        if (ys[band + 1] > piece.squareTop + EPSILON) {
          val squares = squaresByBand[band] ?: ArrayList<CornerPiece>().also { squaresByBand[band] = it }
          squares.add(piece)
        }
        band++
      }
    }
    val result = DoubleArrayList()
    for (band in 0 until bandCount) {
      var cell = 0
      while (cell < cellCount) {
        if (!isCovered(band, cell)) {
          cell++
          continue
        }
        var end = cell + 1
        while (end < cellCount && isCovered(band, end)) {
          end++
        }
        addRunWithout(result, xs[cell], xs[end], ys[band], ys[band + 1], squaresByBand[band])
        cell = end
      }
    }
    return result.toDoubleArray()
  }

  /**
   * Adds the run from [left] to [right] and from [top] to [bottom] without the squares of [candidates].
   */
  private fun addRunWithout(
    result: DoubleArrayList,
    left: Double,
    right: Double,
    top: Double,
    bottom: Double,
    candidates: List<CornerPiece>?,
  ) {
    runSquares.clear()
    if (candidates != null) {
      for (piece in candidates) {
        if (piece.squareLeft < right - EPSILON && piece.squareRight > left + EPSILON) {
          runSquares.add(piece)
        }
      }
    }
    if (runSquares.isEmpty()) {
      addRectangle(result, left, top, right, bottom)
      return
    }
    if (cuts.size < 2 + runSquares.size * 2) {
      cuts = DoubleArray(2 + runSquares.size * 2)
    }
    var cutCount = 0
    cuts[cutCount++] = top
    cuts[cutCount++] = bottom
    for (piece in runSquares) {
      cuts[cutCount++] = piece.squareTop.coerceIn(top, bottom)
      cuts[cutCount++] = piece.squareBottom.coerceIn(top, bottom)
    }
    Arrays.sort(cuts, 0, cutCount)
    var sliceTop = cuts[0]
    for (cut in 1 until cutCount) {
      val sliceBottom = cuts[cut]
      if (sliceBottom <= sliceTop + EPSILON) {
        continue
      }
      addSliceWithout(result, left, right, sliceTop, sliceBottom)
      sliceTop = sliceBottom
    }
  }

  /**
   * Adds one slice of a run without the squares of [runSquares] that cover the whole slice.
   */
  private fun addSliceWithout(
    result: DoubleArrayList,
    left: Double,
    right: Double,
    sliceTop: Double,
    sliceBottom: Double,
  ) {
    if (holeStarts.size < runSquares.size) {
      holeStarts = DoubleArray(runSquares.size)
      holeEnds = DoubleArray(runSquares.size)
    }
    var holes = 0
    for (piece in runSquares) {
      if (piece.squareTop > sliceTop + EPSILON || piece.squareBottom < sliceBottom - EPSILON) {
        continue
      }
      val start = max(left, piece.squareLeft)
      val end = min(right, piece.squareRight)
      var position = holes
      while (position > 0 && holeStarts[position - 1] > start) {
        holeStarts[position] = holeStarts[position - 1]
        holeEnds[position] = holeEnds[position - 1]
        position--
      }
      holeStarts[position] = start
      holeEnds[position] = end
      holes++
    }
    var x = left
    for (index in 0 until holes) {
      if (holeStarts[index] > x + EPSILON) {
        addRectangle(result, x, sliceTop, holeStarts[index], sliceBottom)
      }
      x = max(x, holeEnds[index])
    }
    if (right > x + EPSILON) {
      addRectangle(result, x, sliceTop, right, sliceBottom)
    }
  }

  private fun addRectangle(result: DoubleArrayList, left: Double, top: Double, right: Double, bottom: Double) {
    result.add(left)
    result.add(top)
    result.add(right)
    result.add(bottom)
  }

  /**
   * Gives the band that holds [y], or the first band for a [y] above the grid.
   */
  private fun bandAt(y: Double): Int {
    val found = Arrays.binarySearch(ys, y)
    if (found >= 0) {
      return found
    }
    return max(0, -found - 2)
  }

  private fun alignedX(x: Double): Double {
    return device?.alignX(x) ?: x
  }

  private fun alignedY(y: Double): Double {
    return device?.alignY(y) ?: y
  }

  private fun cover(rows: BackgroundRows, index: Int) {
    val firstCell = gridIndexOf(xs, alignedX(rows.start(index)))
    val lastCell = gridIndexOf(xs, alignedX(rows.end(index)))
    val firstBand = gridIndexOf(ys, alignedY(rows.top(index)))
    val lastBand = gridIndexOf(ys, alignedY(rows.bottom(index)))
    for (band in firstBand until lastBand) {
      for (cell in firstCell until lastCell) {
        covered[band * cellCount + cell] = true
      }
    }
  }

  private fun isCovered(band: Int, cell: Int): Boolean {
    if (band !in 0 until bandCount || cell !in 0 until cellCount) {
      return false
    }
    return covered[band * cellCount + cell]
  }

  private fun firstEdgeFrom(edges: LongArray, corner: Int): Int {
    val found = Arrays.binarySearch(edges, corner.toLong() shl 32)
    return if (found >= 0) found else -found - 1
  }

  private fun edgeLength(from: Int, to: Int): Double {
    if (xIndexOf(from) != xIndexOf(to)) {
      return abs(xs[xIndexOf(to)] - xs[xIndexOf(from)])
    }
    return abs(ys[yIndexOf(to)] - ys[yIndexOf(from)])
  }

  private fun directionOf(from: Int, to: Int): Int {
    if (xIndexOf(to) > xIndexOf(from)) {
      return RIGHT
    }
    if (xIndexOf(to) < xIndexOf(from)) {
      return LEFT
    }
    return if (yIndexOf(to) > yIndexOf(from)) DOWN else UP
  }

  private fun cornerOf(xIndex: Int, yIndex: Int): Int {
    return yIndex * xs.size + xIndex
  }

  private fun xOf(corner: Int): Double {
    return xs[xIndexOf(corner)]
  }

  private fun yOf(corner: Int): Double {
    return ys[yIndexOf(corner)]
  }

  private fun xIndexOf(corner: Int): Int {
    return corner % xs.size
  }

  private fun yIndexOf(corner: Int): Int {
    return corner / xs.size
  }

  private fun gridIndexOf(values: DoubleArray, value: Double): Int {
    val found = Arrays.binarySearch(values, value)
    if (found >= 0) {
      return found
    }
    val below = -found - 2
    require(below >= 0 && value - values[below] <= EPSILON) {
      "the value $value is outside the grid of the style"
    }
    return below
  }

  private fun headOf(edge: Long): Int {
    return (edge and 0xFFFFFFFFL).toInt()
  }

  private fun tailOf(edge: Long): Int {
    return (edge ushr 32).toInt()
  }

  private fun edgeOf(tail: Int, head: Int): Long {
    return (tail.toLong() shl 32) or head.toLong()
  }

  /**
   * Gives the sorted values, with the values that differ by less than [EPSILON] as one value.
   */
  private fun canonicalValues(values: DoubleArray): DoubleArray {
    if (values.isEmpty()) {
      return values
    }
    val sorted = values.copyOf()
    Arrays.sort(sorted)
    var count = 1
    for (index in 1 until sorted.size) {
      if (sorted[index] > sorted[count - 1] + EPSILON) {
        sorted[count] = sorted[index]
        count++
      }
    }
    return sorted.copyOf(count)
  }

  /**
   * One antialiased corner of [split].
   *
   * The curve runs from [radius] before the corner along the incoming edge to [radius] after it along the outgoing edge.
   * A convex corner covers the part of its square inside the curve, and the rectangles of [split] leave the square out.
   * The square reaches [back] along the incoming edge and [forth] along the outgoing edge.
   * A concave corner has no square. It covers its fillet between the corner and the curve.
   */
  private class CornerPiece(
    private val x: Double,
    private val y: Double,
    private val radius: Double,
    private val incoming: Int,
    private val outgoing: Int,
    private val back: Double,
    private val forth: Double,
  ) {
    private val innerX = x - back * dx(incoming) + forth * dx(outgoing)
    private val innerY = y - back * dy(incoming) + forth * dy(outgoing)
    private val startX = x - radius * dx(incoming)
    private val startY = y - radius * dy(incoming)
    private val endX = x + radius * dx(outgoing)
    private val endY = y + radius * dy(outgoing)

    val hasSquare: Boolean get() = back > 0 && forth > 0
    val squareLeft: Double get() = min(x, innerX)
    val squareRight: Double get() = max(x, innerX)
    val squareTop: Double get() = min(y, innerY)
    val squareBottom: Double get() = max(y, innerY)
    val top: Double = min(min(y, innerY), min(startY, endY))
    val bottom: Double = max(max(y, innerY), max(startY, endY))

    fun appendTo(path: Path2D.Double) {
      path.moveTo(x - back * dx(incoming), y - back * dy(incoming))
      path.lineTo(startX, startY)
      path.quadTo(x, y, endX, endY)
      path.lineTo(x + forth * dx(outgoing), y + forth * dy(outgoing))
      path.lineTo(innerX, innerY)
      path.closePath()
    }
  }

  companion object {
    /**
     * Two coordinates that differ by less than this are one coordinate of the grid.
     */
    private const val EPSILON: Double = 1e-2

    /**
     * The direction difference of a right turn. With the interior on the right, a right turn is a convex corner.
     */
    private const val RIGHT_TURN: Int = 1

    /**
     * The four directions of an edge, in the clockwise order of the screen.
     */
    private const val RIGHT: Int = 0
    private const val DOWN: Int = 1
    private const val LEFT: Int = 2
    private const val UP: Int = 3

    /**
     * The rank of a turn by the direction difference: the right turn wins, then straight, then the left turn, then back.
     */
    private val TURN_RANK: IntArray = intArrayOf(1, 0, 3, 2)

    private fun dx(direction: Int): Double {
      if (direction == RIGHT) {
        return 1.0
      }
      if (direction == LEFT) {
        return -1.0
      }
      return 0.0
    }

    private fun dy(direction: Int): Double {
      if (direction == DOWN) {
        return 1.0
      }
      if (direction == UP) {
        return -1.0
      }
      return 0.0
    }
  }
}
