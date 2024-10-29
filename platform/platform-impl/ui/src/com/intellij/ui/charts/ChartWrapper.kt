// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.charts

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import org.intellij.lang.annotations.MagicConstant
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

interface ChartComponent {
  fun paintComponent(g: Graphics2D)
}

/**
 * Holds instance of ChartWrapper in which has been added.
 */
abstract class Overlay<T: ChartWrapper>: ChartComponent {

  var wrapper: ChartWrapper? = null
    set(value) {
      field = value
      afterChartInitialized()
    }

  var mouseLocation: Point? = null

  /**
   * Typed value for {@code wrapper}.
   *
   * Normally wrapper is already initialized when added to ChartWrapper.
   */
  val chart: T
    @Suppress("UNCHECKED_CAST") get() = wrapper as T

  open fun afterChartInitialized() { }

  fun Point.toChartSpace(): Point? = wrapper?.let {
    if (it.margins.left < x && x < it.width - it.margins.right && it.margins.top < y && y < it.height - it.margins.bottom) {
      Point(x - it.margins.left, y - it.margins.top)
    } else null
  }

}

interface XYChartComponent<X: Number, Y: Number> {
  val ranges: MinMax<X, Y>
}

abstract class GridChartWrapper<X: Number, Y: Number>: ChartWrapper(), XYChartComponent<X, Y> {

  abstract override val ranges: Grid<X, Y>
  val grid: Grid<X, Y>
    get() = ranges
  val gridWidth: Int
    get() = width - (margins.left + margins.right)
  val gridHeight: Int
    get() = height - (margins.top + margins.bottom)

  var gridColor: Color = JBColor(Color(0xF0F0F0), Color(0x313335))
  var gridLabelColor: Color = ColorUtil.withAlpha(JBColor.foreground(), 0.6)

  protected fun paintGrid(grid: Graphics2D, chart: Graphics2D, xy: MinMax<X, Y>) {
    val gc = Color(gridColor.rgb)
    val gcd = gc.darker()
    val bounds = grid.clipBounds
    val xOrigin: Int = if (ranges.xOriginInitialized) findX(xy, ranges.xOrigin).toInt() else 0
    val yOrigin: Int = if (ranges.yOriginInitialized) findY(xy, ranges.yOrigin).toInt() else height

    var tmp: Int // — helps to filter line drawing, when lines are met too often

    // draws vertical grid lines
    tmp = -1
    ranges.xLines.apply {
      min = xy.xMin
      max = xy.xMax
    }.forEach {
      val gl = GridLine(it, xy, SwingConstants.VERTICAL).apply(ranges.xPainter::accept)
      val px = findGridLineX(gl, it).roundToInt()
      if (gl.paintLine) {
        if ((tmp - px).absoluteValue < 1) { return@forEach } else { tmp = px }
        grid.color = if (gl.majorLine) gcd else gc
        grid.drawLine(px, bounds.y, px, bounds.y + bounds.height)
      }
      gl.label?.let { label ->
        chart.color = gridLabelColor
        val (x, y) = findGridLabelOffset(gl, chart)
        chart.drawString(label, px + margins.left - x.toInt(), yOrigin - margins.bottom + y.toInt())
      }
    }

    // draws horizontal grid lines
    tmp = -1
    ranges.yLines.apply {
      min = xy.yMin
      max = xy.yMax
    }.forEach {
      val gl = GridLine(it, xy).apply(ranges.yPainter::accept)
      val py = findGridLineY(gl, it).roundToInt()
      if (gl.paintLine) {
        if ((tmp - py).absoluteValue < 1) { return@forEach } else { tmp = py }
        grid.color = if (gl.majorLine) gcd else gc
        grid.drawLine(bounds.x, py, bounds.x + bounds.width, py)
      }
      gl.label?.let { label ->
        chart.color = gridLabelColor
        val (x, y) = findGridLabelOffset(gl, chart)
        chart.drawString(label, xOrigin + margins.left - x.toInt(), py + margins.top + y.toInt() )
      }
    }
  }

  protected open fun findGridLineX(gl: GridLine<X, Y, *>, x: X): Double = findX(gl.xy, x)

  protected open fun findGridLineY(gl: GridLine<X, Y, *>, y: Y): Double = findY(gl.xy, y)

  abstract fun findMinMax(): MinMax<X, Y>

  protected open fun findX(xy: MinMax<X, Y>, x: X): Double {
    val width = width - (margins.left + margins.right)
    return width * (x.toDouble() - xy.xMin.toDouble()) / (xy.xMax.toDouble() - xy.xMin.toDouble())
  }

  protected open fun findY(xy: MinMax<X, Y>, y: Y): Double {
    val height = height - (margins.top + margins.bottom)
    return height - height * (y.toDouble() - xy.yMin.toDouble()) / (xy.yMax.toDouble() - xy.yMin.toDouble())
  }

  protected open fun findGridLabelOffset(line: GridLine<*, *, *>, g: Graphics2D): Coordinates<Double, Double> {
    val s = JBUI.scale(4).toDouble()
    val b = g.fontMetrics.getStringBounds(line.label, null)
    val x = when (line.horizontalAlignment) {
      SwingConstants.RIGHT -> -s
      SwingConstants.CENTER -> b.width / 2
      SwingConstants.LEFT -> b.width + s
      else -> -s
    }
    val y = b.height - when (line.verticalAlignment) {
      SwingConstants.TOP -> b.height + s
      SwingConstants.CENTER -> b.height / 2 + s / 2 // compensate
      SwingConstants.BOTTOM -> 0.0
      else -> 0.0
    }
    return x to y
  }
}

abstract class ChartWrapper : ChartComponent {
  var width: Int = 0
    private set
  var height: Int = 0
    private set
  var background: Color = JBColor.background()
  var overlays: List<ChartComponent> = mutableListOf()
    set(value) {
      field.forEach { if (it is Overlay<*>) it.wrapper = null }
      (field as MutableList<ChartComponent>).addAll(value)
      field.forEach { if (it is Overlay<*>) it.wrapper = this }
    }
  var margins: Insets = Insets(0, 0, 0, 0)

  open fun paintOverlay(g: Graphics2D) {
    overlays.forEach {
      it.paintComponent(g)
    }
  }

  open val component: JComponent by lazy {
    createCentralPanel().apply {
      with(MouseAware()) {
        addMouseMotionListener(this)
        addMouseListener(this)
      }
    }
  }

  fun update(): Unit = component.repaint()

  protected open fun createCentralPanel(): JComponent = CentralPanel()

  protected var mouseLocation: Point? = null
    private set(value) {
      field = value
      overlays.forEach { if (it is Overlay<*>) it.mouseLocation = value }
    }

  private inner class MouseAware : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      mouseLocation = e.point
      component.repaint()
    }

    override fun mouseEntered(e: MouseEvent) {
      mouseLocation = e.point
      component.repaint()
    }

    override fun mouseExited(e: MouseEvent) {
      mouseLocation = null
      component.repaint()
    }

    override fun mouseDragged(e: MouseEvent) {
      mouseLocation = e.point
      component.repaint()
    }
  }

  private inner class CentralPanel : JComponent() {
    override fun paintComponent(g: Graphics) {
      (g as Graphics2D).clip(Rectangle(0, 0, width, height))
      g.color = this@ChartWrapper.background
      (g as Graphics2D).fill(g.clip)
      this@ChartWrapper.height = height
      this@ChartWrapper.width = width
      val gridGraphics = (g.create(0, 0, this@ChartWrapper.width, this@ChartWrapper.height) as Graphics2D).apply {
        clip(visibleRect)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        GraphicsUtil.setupAntialiasing(this)
      }
      try {
        this@ChartWrapper.paintComponent(gridGraphics)
      } finally {
        gridGraphics.dispose()
      }

      val overlayGraphics = (g.create() as Graphics2D).apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        GraphicsUtil.setupAntialiasing(this)
      }
      try {
        this@ChartWrapper.paintOverlay(overlayGraphics)
      } finally {
        overlayGraphics.dispose()
      }
    }
  }
}

open class Dataset<T> {

  var label: String? = null
  var lineColor: Paint? = JBColor.foreground()
  var fillColor: Paint? = null

  open var data: Iterable<T> = mutableListOf()

  fun add(vararg values: T) {
    addAll(values.toList())
  }

  fun addAll(values: Collection<T>) {
    (data as? MutableList<T>)?.addAll(values) ?: throw UnsupportedOperationException()
  }

  fun Color.transparent(alpha: Double): Color = ColorUtil.withAlpha(this, alpha)
}

data class Coordinates<X: Number, Y: Number>(val x: X, val y: Y) {

  companion object {
    @JvmStatic fun <X: Number, Y: Number> of(x: X, y: Y) : Coordinates<X, Y> = Coordinates(x, y)
  }

}

infix fun <X: Number, Y: Number> X.to(y: Y): Coordinates<X, Y> = Coordinates(this, y)

open class MinMax<X: Number, Y: Number> {

  lateinit var xMin: X
  private val xMinInitialized
    get() = this::xMin.isInitialized

  lateinit var xMax: X
  val xMaxInitialized: Boolean
    get() = this::xMax.isInitialized

  lateinit var yMin: Y
  private val yMinInitialized
    get() = this::yMin.isInitialized

  lateinit var yMax: Y
  val yMaxInitialized: Boolean
    get() = this::yMax.isInitialized

  fun process(point: Coordinates<X, Y>) {
    val (x, y) = point
    process(x, y)
  }

  fun process(x: X, y: Y) {
    processX(x)
    processY(y)
  }

  private fun processX(x: X) {
    xMin = if (!xMinInitialized || xMin.toDouble() > x.toDouble()) x else xMin
    xMax = if (!xMaxInitialized || xMax.toDouble() < x.toDouble()) x else xMax
  }

  private fun processY(y: Y) {
    yMin = if (!yMinInitialized || yMin.toDouble() > y.toDouble()) y else yMin
    yMax = if (!yMaxInitialized || yMax.toDouble() < y.toDouble()) y else yMax
  }

  operator fun times(other: MinMax<X, Y>): MinMax<X, Y> {
    val my = MinMax<X, Y>()
    if (this.xMinInitialized) my.xMin = this.xMin else if (other.xMinInitialized) my.xMin = other.xMin
    if (this.xMaxInitialized) my.xMax = this.xMax else if (other.xMaxInitialized) my.xMax = other.xMax
    if (this.yMinInitialized) my.yMin = this.yMin else if (other.yMinInitialized) my.yMin = other.yMin
    if (this.yMaxInitialized) my.yMax = this.yMax else if (other.yMaxInitialized) my.yMax = other.yMax
    return my
  }

  operator fun plus(other: MinMax<X, Y>) : MinMax<X, Y> {
    val my = MinMax<X, Y>()
    help(this.xMinInitialized, this::xMin, other.xMinInitialized, other::xMin, -1, my::xMin.setter)
    help(this.xMaxInitialized, this::xMax, other.xMaxInitialized, other::xMax, 1, my::xMax.setter)
    help(this.yMinInitialized, this::yMin, other.yMinInitialized, other::yMin, -1, my::yMin.setter)
    help(this.yMaxInitialized, this::yMax, other.yMaxInitialized, other::yMax, 1, my::yMax.setter)
    return my
  }

  private fun <T: Number> help(thisInitialized: Boolean, thisGetter: () -> T, otherInitialized: Boolean, otherGetter: () -> T, sign: Int, calc: (T) -> Unit) {
    when {
      thisInitialized && otherInitialized -> {
        val thisValue = thisGetter().toDouble()
        val thatValue = otherGetter().toDouble()
        calc(if (thisValue.compareTo(thatValue).sign == sign) thisGetter() else otherGetter())
      }
      thisInitialized && !otherInitialized -> calc((thisGetter()))
      !thisInitialized && otherInitialized -> calc(otherGetter())
    }
  }

  operator fun component1(): X = xMin
  operator fun component2(): X = xMax
  operator fun component3(): Y = yMin
  operator fun component4(): Y = yMax


  val isInitialized: Boolean get() = xMinInitialized && xMaxInitialized && yMinInitialized && yMaxInitialized
}

class Grid<X: Number, Y: Number>: MinMax<X, Y>() {
  lateinit var xOrigin: X
  val xOriginInitialized: Boolean
    get() = this::xOrigin.isInitialized
  var xLines: ValueIterable<X> = ValueIterable.createStub()
  var xPainter: (Consumer<GridLine<X, Y, X>>) = Consumer {  }

  lateinit var yOrigin: Y
  val yOriginInitialized: Boolean
    get() = this::yOrigin.isInitialized
  var yLines: ValueIterable<Y> = ValueIterable.createStub()
  var yPainter: (Consumer<GridLine<X, Y, Y>>) = Consumer {  }
}

class GridLine<X: Number, Y: Number, T: Number>(val value: T, @get:JvmName("getXY") val xy: MinMax<X, Y>, @MagicConstant val orientation: Int = SwingConstants.HORIZONTAL) {
  var paintLine: Boolean = true
  var majorLine: Boolean = false
  var label: String? = null
  @MagicConstant var horizontalAlignment: Int = SwingConstants.CENTER
  @MagicConstant var verticalAlignment: Int = SwingConstants.BOTTOM
}

abstract class ValueIterable<X: Number> : Iterable<X> {

  lateinit var min: X
  lateinit var max: X

  open fun prepare(min: X, max: X): ValueIterable<X> {
    this.min = min
    this.max = max
    return this
  }

  companion object {
    fun <T: Number> createStub(): ValueIterable<T> {
      return object: ValueIterable<T>() {
        val iter = object: Iterator<T> {
          override fun hasNext() = false
          override fun next(): T { error("not implemented") }
        }
        override fun iterator(): Iterator<T> = iter
      }
    }
  }

}