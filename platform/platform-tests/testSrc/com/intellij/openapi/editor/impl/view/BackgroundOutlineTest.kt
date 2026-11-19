// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.util.ui.GraphicsUtil
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

private const val STYLE: Int = 1
private const val OTHER_STYLE: Int = 2
private const val ROW_HEIGHT: Double = 12.0
private const val RADIUS: Double = 2.0
private const val IMAGE_WIDTH: Int = 48
private const val IMAGE_HEIGHT: Int = 44
private const val MARGIN: Double = 2.0

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

  @Test
  fun `the split fills the pixels of the outline`() {
    for (shape in shapes()) {
      for (radius in doubleArrayOf(RADIUS, 2.5)) {
        for (scale in doubleArrayOf(1.0, 2.0)) {
          val outline = paint(scale) { graphics -> graphics.fill(BackgroundOutline.build(shape.rows, STYLE, radius)) }
          val split = paint(scale) { graphics -> fillSplit(graphics, shape.rows, radius) }
          assertArrayEquals(outline, split, "${shape.name}, radius $radius, scale $scale")
        }
      }
    }
  }

  @Test
  fun `the split puts its rectangles on the device pixels`() {
    val rows = BackgroundRows()
    rows.add(STYLE, 0.3, 12.6, 1.2, 30.7)
    rows.add(STYLE, 12.6, 25.1, 5.4, 18.3)
    val transform = AffineTransform(1.5, 0.0, 0.0, 1.5, 0.25, 0.75)
    val device = DeviceGrid.of(transform)!!

    val rectangles = BackgroundOutline.split(rows, STYLE, 2.5, device).rectangles

    assertTrue(rectangles.isNotEmpty())
    for (index in rectangles.indices step 4) {
      assertOnPixel(rectangles[index] * 1.5 + 0.25)
      assertOnPixel(rectangles[index + 1] * 1.5 + 0.75)
      assertOnPixel(rectangles[index + 2] * 1.5 + 0.25)
      assertOnPixel(rectangles[index + 3] * 1.5 + 0.75)
    }
  }

  @Test
  fun `the corner paths keep one device pixel apart`() {
    for (shape in shapes()) {
      for (scale in doubleArrayOf(1.0, 2.0)) {
        val device = DeviceGrid.of(AffineTransform.getScaleInstance(scale, scale))!!
        val bounds = BackgroundOutline.split(shape.rows, STYLE, 2.5, device).corners
          .map { it.bounds2D }
          .sortedBy { it.minY }
        for (index in 1 until bounds.size) {
          assertTrue(bounds[index].minY - bounds[index - 1].maxY >= 1 / scale, "${shape.name}, scale $scale")
        }
      }
    }
  }

  @Test
  fun `a device grid needs a transform without rotation, shear and mirror`() {
    assertNotNull(DeviceGrid.of(AffineTransform.getScaleInstance(2.0, 2.0)))
    assertNull(DeviceGrid.of(AffineTransform.getShearInstance(0.1, 0.0)))
    assertNull(DeviceGrid.of(AffineTransform.getRotateInstance(0.5)))
    assertNull(DeviceGrid.of(AffineTransform.getScaleInstance(-1.0, 1.0)))
  }

  /**
   * Gives shapes with each kind of corner: convex, concave, a hole, a narrow row, a short edge and two shapes that
   * touch at one corner.
   */
  private fun shapes(): List<Shape> {
    val oneRow = BackgroundRows()
    oneRow.addRow(0, 0.0, 30.0)
    val step = BackgroundRows()
    step.addRow(0, 0.0, 20.0)
    step.addRow(1, 10.0, 30.0)
    val hole = BackgroundRows()
    hole.addRow(0, 0.0, 30.0)
    hole.addRow(1, 0.0, 10.0)
    hole.addRow(1, 20.0, 30.0)
    hole.addRow(2, 0.0, 30.0)
    val narrow = BackgroundRows()
    narrow.addRow(0, 0.0, 3.0)
    val shortEdge = BackgroundRows()
    shortEdge.add(STYLE, 0.0, ROW_HEIGHT, 0.0, 40.0)
    shortEdge.add(STYLE, ROW_HEIGHT, ROW_HEIGHT + 5.0, 0.0, 20.0)
    shortEdge.add(STYLE, ROW_HEIGHT + 5.0, 2 * ROW_HEIGHT + 5.0, 0.0, 40.0)
    val touching = BackgroundRows()
    touching.addRow(0, 0.0, 10.0)
    touching.addRow(1, 10.0, 20.0)
    return listOf(
      Shape("one row", oneRow),
      Shape("a step", step),
      Shape("a hole", hole),
      Shape("a narrow row", narrow),
      Shape("a short edge", shortEdge),
      Shape("two shapes at one corner", touching),
    )
  }

  private class Shape(val name: String, val rows: BackgroundRows)

  /**
   * Fills the split of [rows] as the editor fills it: the rectangles at the device pixels and the corners with antialiasing.
   */
  private fun fillSplit(graphics: Graphics2D, rows: BackgroundRows, radius: Double) {
    val pieces = BackgroundOutline.split(rows, STYLE, radius, DeviceGrid.of(graphics.transform)!!)
    val rectangles = pieces.rectangles
    for (index in rectangles.indices step 4) {
      val rectangle = Rectangle2D.Double(
        rectangles[index],
        rectangles[index + 1],
        rectangles[index + 2] - rectangles[index],
        rectangles[index + 3] - rectangles[index + 1],
      )
      EditorPainter.fillRectExact(graphics, rectangle, Color.BLACK)
    }
    graphics.color = Color.BLACK
    for (corners in pieces.corners) {
      graphics.fill(corners)
    }
  }

  /**
   * Gives the pixels of an image that [fill] paints in black with antialiasing at [scale].
   */
  private fun paint(scale: Double, fill: (Graphics2D) -> Unit): IntArray {
    val width = (IMAGE_WIDTH * scale).toInt()
    val height = (IMAGE_HEIGHT * scale).toInt()
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      graphics.scale(scale, scale)
      graphics.translate(MARGIN, MARGIN)
      GraphicsUtil.setupAAPainting(graphics)
      graphics.color = Color.BLACK
      fill(graphics)
    } finally {
      graphics.dispose()
    }
    return image.getRGB(0, 0, width, height, null, 0, width)
  }

  private fun assertOnPixel(deviceCoordinate: Double) {
    assertTrue(abs(deviceCoordinate - round(deviceCoordinate)) < 1e-9, "$deviceCoordinate is not on a device pixel")
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
