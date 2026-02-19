package com.intellij.ui.charts

import com.intellij.util.ui.ImageUtil
import org.junit.Assert
import org.junit.Test
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

class LineChartKotlinTest {
  @Test
  fun simpleCreation() {
    val chart = lineChart<Int, Int> {
      datasets {
        dataset {
          values {
            x = listOf(2, 3, 4)
            y = listOf(1, 2, 3)
          }
        }
      }
    }

    val xy = chart.findMinMax()

    Assert.assertEquals(2, xy.xMin)
    Assert.assertEquals(4, xy.xMax)
    Assert.assertEquals(1, xy.yMin)
    Assert.assertEquals(3, xy.yMax)
  }

  @Test
  fun checkDoubleCreation() {
    val chart = lineChart<Double, Double> {
      datasets {
        dataset {
          generate {
            x = generator(0.01).prepare(0.0, 100.0)
            y = { it }
          }
        }
      }
    }

    val xy = chart.findMinMax()

    Assert.assertEquals(0.0, xy.xMin, 1e-6)
    Assert.assertEquals(100.0, xy.xMax, 1e-6)
    Assert.assertEquals(0.0, xy.yMin, 1e-6)
    Assert.assertEquals(100.0, xy.yMax, 1e-6)
  }

  @Test
  fun testLinearChart() {
    val size = 1000
    val chart = lineChart<Double, Double> {
      dataset {
        generate {
          x = generator(10.0 / size).prepare(0.0, size / 10.0)
          y = { it }
        }
      }
    }
    val img = ImageUtil.createImage(size, size, BufferedImage.TYPE_INT_RGB)
    chart.component.apply {
      this.size = Dimension(size, size)
      invalidate()
      paint(img.createGraphics())
    }
    val xy = chart.findMinMax()
    for (i in 0..size) {
      val loc = chart.findLocation(xy, i / 10.0 to i / 10.0)
      Assert.assertEquals("x fails, i = $i", i.toDouble(), loc.x, 1e-6)
      Assert.assertEquals("y fails, i = $i", (size - i).toDouble(), loc.y, 1e-6)
    }
  }

  @Test
  fun testLinearChartAndScaled() {
    val size = 1000
    val chart = lineChart<Double, Double> {
      dataset {
        generate {
          x = generator(10.0 / size).prepare(0.0, size / 10.0)
          y = { it }
        }
      }
    }
    val img = ImageUtil.createImage(size, size, BufferedImage.TYPE_INT_RGB)
    chart.component.apply {
      this.size = Dimension(size, (size * 1.5).roundToInt())
      invalidate()
      paint(img.createGraphics())
    }
    val xy = chart.findMinMax()
    for (i in 0..size) {
      val loc = chart.findLocation(xy, i / 10.0 to i / 10.0)
      Assert.assertEquals("x fails, i = $i", i.toDouble(), loc.x, 1e-6)
      Assert.assertEquals("y fails, i = $i", (size - i) * 1.5, loc.y, 1e-6)
    }
  }
}