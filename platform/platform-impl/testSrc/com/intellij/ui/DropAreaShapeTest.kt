// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D

/**
 * Tests [createDropAreaShape]: a corner of the drop placeholder is rounded only at a corner of the corner area.
 */
internal class DropAreaShapeTest {
  @Test
  fun `the placeholder over the top of the right window rounds only the top right corner`() {
    val rightWindowTop = Rectangle(WIDTH / 2, 0, WIDTH / 2, HEIGHT / 2)
    val shape = createDropAreaShape(rightWindowTop, CORNER_AREA, ARC)
    assertThat(shape.bounds).isEqualTo(rightWindowTop)
    assertThat(shape.contains(WIDTH - 0.5, 0.5)).isFalse()
    assertThat(shape.contains(WIDTH / 2 + 0.5, 0.5)).isTrue()
    assertThat(shape.contains(WIDTH / 2 + 0.5, HEIGHT / 2 - 0.5)).isTrue()
    assertThat(shape.contains(WIDTH - 0.5, HEIGHT / 2 - 0.5)).isTrue()
  }

  @Test
  fun `a corner beside a window narrower than the arc stays square`() {
    val rightWindow = Rectangle(NEIGHBOR, 0, WIDTH - NEIGHBOR, HEIGHT)
    val shape = createDropAreaShape(rightWindow, CORNER_AREA, ARC)
    assertThat(shape.contains(NEIGHBOR + 0.25, 0.25)).isTrue()
    assertThat(shape.contains(NEIGHBOR + 0.25, HEIGHT - 0.25)).isTrue()
    assertThat(shape.contains(WIDTH - 0.25, 0.25)).isFalse()
    assertThat(shape.contains(WIDTH - 0.25, HEIGHT - 0.25)).isFalse()
  }

  @Test
  fun `an area larger than the corner area gives the rounded corner area`() {
    val shape = createDropAreaShape(Rectangle(-2, -2, WIDTH + 4, HEIGHT + 4), CORNER_AREA, ARC)
    assertThat(shape.bounds).isEqualTo(CORNER_AREA)
    assertThat(shape.contains(0.25, 0.25)).isFalse()
    assertThat(shape.contains(WIDTH - 0.25, 0.25)).isFalse()
    assertThat(shape.contains(0.25, HEIGHT - 0.25)).isFalse()
    assertThat(shape.contains(WIDTH - 0.25, HEIGHT - 0.25)).isFalse()
  }

  @Test
  fun `an empty area or an area out of the corner area gives an empty shape`() {
    assertThat(createDropAreaShape(Rectangle(0, 0, 0, HEIGHT), CORNER_AREA, ARC).bounds.isEmpty).isTrue()
    assertThat(createDropAreaShape(Rectangle(WIDTH / 2, HEIGHT / 2, WIDTH / 2, 0), CORNER_AREA, ARC).bounds.isEmpty).isTrue()
    assertThat(createDropAreaShape(Rectangle(WIDTH, 0, NARROW, HEIGHT), CORNER_AREA, ARC).bounds.isEmpty).isTrue()
  }

  @Test
  fun `an area with no corner at the corner area is a rectangle`() {
    val inner = Rectangle(WIDTH / 4, HEIGHT / 4, WIDTH / 2, HEIGHT / 2)
    assertThat(createDropAreaShape(inner, CORNER_AREA, ARC)).isEqualTo(inner)
    val atRightSide = Rectangle(WIDTH / 2, HEIGHT / 4, WIDTH / 2, HEIGHT / 2)
    assertThat(createDropAreaShape(atRightSide, CORNER_AREA, ARC)).isEqualTo(atRightSide)
  }

  @Test
  fun `an arc of zero gives the rectangle`() {
    assertThat(createDropAreaShape(CORNER_AREA, CORNER_AREA, 0)).isEqualTo(CORNER_AREA)
  }

  @Test
  fun `a narrow area at a corner takes the curve of the corner area`() {
    val rightStrip = Rectangle(WIDTH - NARROW, 0, NARROW, HEIGHT)
    val shape = createDropAreaShape(rightStrip, CORNER_AREA, ARC)
    // The points are on the two sides of a circle with the radius ARC / 2 and the center (WIDTH - 10, 10).
    assertThat(shape.contains(WIDTH - 0.25, 7.0)).isFalse()
    assertThat(shape.contains(WIDTH - 0.25, 8.5)).isTrue()
    assertThat(shape.contains(WIDTH - 3.75, 1.0)).isFalse()
    assertThat(shape.contains(WIDTH - 3.75, 3.0)).isTrue()
    assertThat(shape.contains(WIDTH - 0.25, HEIGHT - 7.0)).isFalse()
    assertThat(shape.contains(WIDTH - 0.25, HEIGHT - 8.5)).isTrue()
  }

  @Test
  fun `a low area at a corner takes the same curve as a narrow area`() {
    val bottomStrip = Rectangle(0, HEIGHT - NARROW, WIDTH, NARROW)
    val shape = createDropAreaShape(bottomStrip, CORNER_AREA, ARC)
    assertThat(shape.contains(WIDTH - 7.0, HEIGHT - 0.25)).isFalse()
    assertThat(shape.contains(WIDTH - 8.5, HEIGHT - 0.25)).isTrue()
    assertThat(shape.contains(WIDTH - 1.0, HEIGHT - 3.75)).isFalse()
    assertThat(shape.contains(WIDTH - 3.0, HEIGHT - 3.75)).isTrue()
    assertThat(shape.contains(7.0, HEIGHT - 0.25)).isFalse()
    assertThat(shape.contains(8.5, HEIGHT - 0.25)).isTrue()
  }

  @Test
  fun `an area in the cut part of a corner gives an empty shape`() {
    assertThat(createDropAreaShape(Rectangle(0, 0, 2, 2), CORNER_AREA, ARC).bounds.isEmpty).isTrue()
  }

  @Test
  fun `an arc larger than the corner area gives circular corners`() {
    val low = Rectangle(0, 0, WIDTH, 12)
    val shape = createDropAreaShape(low, low, ARC)
    // The points are on the two sides of a circle with the radius 6 and the center (6, 6).
    assertThat(shape.contains(1.0, 1.0)).isFalse()
    assertThat(shape.contains(2.5, 2.5)).isTrue()
    // This point is out of a corner that is 10 pixels wide and 6 pixels high.
    assertThat(shape.contains(6.5, 0.2)).isTrue()
  }

  @Test
  fun `the shape has the points of the area that are in the rounded corner area`() {
    // A curve of the shape is nearer to its circle than CURVE_TOLERANCE. So the test checks no point between these two rounded areas.
    val inner = roundedSmallCornerArea(-CURVE_TOLERANCE)
    val outer = roundedSmallCornerArea(CURVE_TOLERANCE)
    for (area in areasNearCorners()) {
      val shape = createDropAreaShape(area, SMALL_CORNER_AREA, ARC)
      val wrongPoint = gridPoints(area).firstOrNull { point ->
        val inArea = area.contains(point)
        when {
          inArea && inner.contains(point) -> !shape.contains(point)
          !inArea || !outer.contains(point) -> shape.contains(point)
          else -> false
        }
      }
      assertThat(wrongPoint).describedAs("a wrong point of the shape of %s", area).isNull()
    }
  }
}

private const val WIDTH = 1000
private const val HEIGHT = 200
private const val ARC = 20
private const val NARROW = 4
/** The width of a window that is wider than the corner radius and narrower than the arc. */
private const val NEIGHBOR = 15
private val CORNER_AREA = Rectangle(0, 0, WIDTH, HEIGHT)

/** A corner area of few pixels, so a test can check each area near its corners. Its origin is not at zero. */
private val SMALL_CORNER_AREA = Rectangle(3, 5, 26, 24)
/** A cubic curve differs from a quarter of a circle by less than 0.03% of the radius. */
private const val CURVE_TOLERANCE = 0.01
private const val GRID_STEP = 0.5

/**
 * Returns the areas with each side at a side of [SMALL_CORNER_AREA], at an end of a curve, across a curve, or one pixel from these.
 */
private fun areasNearCorners(): List<Rectangle> {
  val distances = listOf(-1, 0, 1, 4, ARC / 2, ARC / 2 + 1)
  val xs = distances.flatMap { listOf(SMALL_CORNER_AREA.x + it, SMALL_CORNER_AREA.x + SMALL_CORNER_AREA.width - it) }
  val ys = distances.flatMap { listOf(SMALL_CORNER_AREA.y + it, SMALL_CORNER_AREA.y + SMALL_CORNER_AREA.height - it) }
  return buildList {
    for (left in xs) {
      for (right in xs.filter { it > left }) {
        for (top in ys) {
          for (bottom in ys.filter { it > top }) {
            add(Rectangle(left, top, right - left, bottom - top))
          }
        }
      }
    }
  }
}

/**
 * Returns [SMALL_CORNER_AREA] with the corners rounded by [ARC]. Each side and each curve is [growth] farther from the center.
 */
private fun roundedSmallCornerArea(growth: Double): RoundRectangle2D {
  val area = SMALL_CORNER_AREA
  val arc = ARC + 2 * growth
  return RoundRectangle2D.Double(area.x - growth, area.y - growth, area.width + 2 * growth, area.height + 2 * growth, arc, arc)
}

/**
 * Returns the points of a grid over [area] and one pixel around it. No point is on a side of a pixel.
 */
private fun gridPoints(area: Rectangle): Sequence<Point2D> = sequence {
  var y = area.y - 1 + GRID_STEP / 4
  while (y < area.maxY + 1) {
    var x = area.x - 1 + GRID_STEP / 4
    while (x < area.maxX + 1) {
      yield(Point2D.Double(x, y))
      x += GRID_STEP
    }
    y += GRID_STEP
  }
}
