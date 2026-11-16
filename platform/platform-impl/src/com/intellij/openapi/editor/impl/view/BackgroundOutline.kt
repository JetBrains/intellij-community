// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import it.unimi.dsi.fastutil.longs.LongArrayList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.util.Arrays
import kotlin.math.abs
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
}

/**
 * The rectangles of one style on the grid of their own coordinates.
 *
 * The distinct x coordinates give the cells of a row, and the distinct y coordinates give the bands.
 * A band is covered where a rectangle covers it, so the grid holds the union of the rectangles.
 * A corner is one point of the grid, and the code keeps it as the index pair `yIndex * xs.size + xIndex`.
 */
private class OutlineGrid(rows: BackgroundRows, styleId: Int) {
  private val xs: DoubleArray
  private val ys: DoubleArray
  private val cellCount: Int
  private val bandCount: Int
  private val covered: BooleanArray

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
      xValues[next] = rows.start(index)
      xValues[next + 1] = rows.end(index)
      yValues[next] = rows.top(index)
      yValues[next + 1] = rows.bottom(index)
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

  private fun cover(rows: BackgroundRows, index: Int) {
    val firstCell = gridIndexOf(xs, rows.start(index))
    val lastCell = gridIndexOf(xs, rows.end(index))
    val firstBand = gridIndexOf(ys, rows.top(index))
    val lastBand = gridIndexOf(ys, rows.bottom(index))
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

  companion object {
    /**
     * Two coordinates that differ by less than this are one coordinate of the grid.
     */
    private const val EPSILON: Double = 1e-2

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
  }
}
