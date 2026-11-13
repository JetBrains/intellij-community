// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.awt.geom.Point2D
import kotlin.math.hypot

private const val STYLE: Int = 1
private const val OTHER_STYLE: Int = 2
private const val ROW_HEIGHT: Double = 12.0
private const val RADIUS: Double = 2.0

internal class BackgroundOutlineTest {
  @Test
  fun `one row gives one rectangle, and another style stays out of it`() {
    val rows = BackgroundRows()
    rows.addRow(0, 0.0, 30.0)
    rows.add(OTHER_STYLE, 0.0, ROW_HEIGHT, 40.0, 50.0)

    assertEquals(
      listOf(corners(0.0, 0.0, 30.0, 0.0, 30.0, 12.0, 0.0, 12.0)),
      cyclesOf(rows),
    )
    val path = BackgroundOutline.build(rows, STYLE, RADIUS)
    assertEquals(10, segmentCount(path))
    assertEquals(RADIUS, radiusAt(path, 0.0, 0.0))
    assertEquals(RADIUS, radiusAt(path, 30.0, 0.0))
    assertEquals(RADIUS, radiusAt(path, 30.0, 12.0))
  }

  @Test
  fun `two rows with a step to the right give one cycle`() {
    val rows = BackgroundRows()
    rows.addRow(0, 0.0, 20.0)
    rows.addRow(1, 10.0, 30.0)

    assertEquals(
      listOf(corners(0.0, 0.0, 20.0, 0.0, 20.0, 12.0, 30.0, 12.0, 30.0, 24.0, 10.0, 24.0, 10.0, 12.0, 0.0, 12.0)),
      cyclesOf(rows),
    )
    assertEquals(18, segmentCount(BackgroundOutline.build(rows, STYLE, RADIUS)))
  }

  @Test
  fun `two rows with a step to the left give one cycle`() {
    val rows = BackgroundRows()
    rows.addRow(0, 10.0, 30.0)
    rows.addRow(1, 0.0, 20.0)

    assertEquals(
      listOf(corners(10.0, 0.0, 30.0, 0.0, 30.0, 12.0, 20.0, 12.0, 20.0, 24.0, 0.0, 24.0, 0.0, 12.0, 10.0, 12.0)),
      cyclesOf(rows),
    )
    assertEquals(18, segmentCount(BackgroundOutline.build(rows, STYLE, RADIUS)))
  }

  @Test
  fun `two rows with equal edges give no corner between them`() {
    val rows = BackgroundRows()
    rows.addRow(0, 0.0, 30.0)
    rows.addRow(1, 0.0, 30.0)

    assertEquals(
      listOf(corners(0.0, 0.0, 30.0, 0.0, 30.0, 24.0, 0.0, 24.0)),
      cyclesOf(rows),
    )
    assertEquals(10, segmentCount(BackgroundOutline.build(rows, STYLE, RADIUS)))
  }

  @Test
  fun `a narrow row limits the radius to one half of its width`() {
    val rows = BackgroundRows()
    rows.addRow(0, 0.0, 4.0)

    assertEquals(
      listOf(corners(0.0, 0.0, 4.0, 0.0, 4.0, 12.0, 0.0, 12.0)),
      cyclesOf(rows),
    )
    val path = BackgroundOutline.build(rows, STYLE, 4.0)
    assertEquals(10, segmentCount(path))
    assertEquals(2.0, radiusAt(path, 0.0, 0.0))
  }

  @Test
  fun `the shorter edge of a corner limits its radius`() {
    val rows = BackgroundRows()
    rows.addRow(0, 0.0, 4.0)
    rows.addRow(1, 0.0, 40.0)

    assertEquals(
      listOf(corners(0.0, 0.0, 4.0, 0.0, 4.0, 12.0, 40.0, 12.0, 40.0, 24.0, 0.0, 24.0)),
      cyclesOf(rows),
    )
    val path = BackgroundOutline.build(rows, STYLE, 4.0)
    assertEquals(14, segmentCount(path))
    assertEquals(2.0, radiusAt(path, 4.0, 0.0))
    assertEquals(4.0, radiusAt(path, 4.0, 12.0))
  }

  @Test
  fun `a gap in the middle row gives a hole`() {
    val rows = BackgroundRows()
    rows.addRow(0, 0.0, 30.0)
    rows.addRow(1, 0.0, 10.0)
    rows.addRow(1, 20.0, 30.0)
    rows.addRow(2, 0.0, 30.0)

    assertEquals(
      listOf(
        corners(0.0, 0.0, 30.0, 0.0, 30.0, 36.0, 0.0, 36.0),
        corners(10.0, 12.0, 10.0, 24.0, 20.0, 24.0, 20.0, 12.0),
      ),
      cyclesOf(rows),
    )
    assertEquals(20, segmentCount(BackgroundOutline.build(rows, STYLE, RADIUS)))
  }

  @Test
  fun `two rectangles that share no x give two cycles`() {
    val rows = BackgroundRows()
    rows.addRow(0, 0.0, 10.0)
    rows.addRow(0, 20.0, 30.0)

    assertEquals(
      listOf(
        corners(0.0, 0.0, 10.0, 0.0, 10.0, 12.0, 0.0, 12.0),
        corners(20.0, 0.0, 30.0, 0.0, 30.0, 12.0, 20.0, 12.0),
      ),
      cyclesOf(rows),
    )
    assertEquals(20, segmentCount(BackgroundOutline.build(rows, STYLE, RADIUS)))
  }

  @Test
  fun `two rectangles that touch at one corner stay apart`() {
    val rows = BackgroundRows()
    rows.addRow(0, 0.0, 10.0)
    rows.addRow(1, 10.0, 20.0)

    assertEquals(
      listOf(
        corners(0.0, 0.0, 10.0, 0.0, 10.0, 12.0, 0.0, 12.0),
        corners(10.0, 12.0, 20.0, 12.0, 20.0, 24.0, 10.0, 24.0),
      ),
      cyclesOf(rows),
    )
    assertEquals(20, segmentCount(BackgroundOutline.build(rows, STYLE, RADIUS)))
  }

  private fun BackgroundRows.addRow(
    row: Int,
    start: Double,
    end: Double,
  ) {
    add(STYLE, row * ROW_HEIGHT, (row + 1) * ROW_HEIGHT, start, end)
  }

  /** Gives the cycles of [STYLE], each one from its corner at the top left, and the cycles in that order too. */
  private fun cyclesOf(rows: BackgroundRows): List<List<Point2D.Double>> {
    return BackgroundOutline.cycles(rows, STYLE)
      .map { fromTopLeft(it) }
      .sortedWith(compareBy({ it[0].y }, { it[0].x }))
  }

  private fun fromTopLeft(cycle: List<Point2D.Double>): List<Point2D.Double> {
    var first = 0
    for (index in cycle.indices) {
      if (cycle[index].y < cycle[first].y || cycle[index].y == cycle[first].y && cycle[index].x < cycle[first].x) {
        first = index
      }
    }
    return List(cycle.size) { cycle[(first + it) % cycle.size] }
  }

  private fun corners(vararg coordinates: Double): List<Point2D.Double> {
    return List(coordinates.size / 2) { Point2D.Double(coordinates[it * 2], coordinates[it * 2 + 1]) }
  }

  private fun segmentCount(path: Path2D): Int {
    var count = 0
    val iterator = path.getPathIterator(null)
    while (!iterator.isDone) {
      count++
      iterator.next()
    }
    return count
  }

  /** Gives the radius of the corner at ([x], [y]), which is 0 when the path has no curve there. */
  private fun radiusAt(path: Path2D, x: Double, y: Double): Double {
    val coordinates = DoubleArray(6)
    val iterator = path.getPathIterator(null)
    var currentX = 0.0
    var currentY = 0.0
    while (!iterator.isDone) {
      val type = iterator.currentSegment(coordinates)
      if (type == PathIterator.SEG_QUADTO) {
        if (coordinates[0] == x && coordinates[1] == y) {
          return hypot(currentX - x, currentY - y)
        }
        currentX = coordinates[2]
        currentY = coordinates[3]
      } else if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
        currentX = coordinates[0]
        currentY = coordinates[1]
      }
      iterator.next()
    }
    return 0.0
  }
}
