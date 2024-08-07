// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.TimerUtil
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.util.*
import javax.swing.*
import javax.swing.tree.TreePath
import kotlin.math.absoluteValue

private const val pulsationSize = 20

object LearningUiHighlightingManager {
  data class HighlightingOptions(
    val highlightBorder: Boolean = true,
    val highlightInside: Boolean = true,
    val usePulsation: Boolean = false,
    val clearPreviousHighlights: Boolean = true,
    val limitByVisibleRect: Boolean = true,
    val isRoundedCorners: Boolean = false,
    val thickness: Int = 1,
  )

  private val highlights: MutableList<RepaintHighlighting<*>> = ArrayList()

  val highlightingComponents: List<Component> get() = highlights.map { it.original }

  val highlightingComponentsWithInfo: List<Pair<Component, () -> Any?>> get() = highlights.map { it.original to it.partInfo }

  fun highlightComponent(original: Component, options: HighlightingOptions = HighlightingOptions()) {
    highlightPartOfComponent(original, options) { Rectangle(Point(0, 0), it.size) }
  }

  fun highlightJListItem(list: JList<*>,
                         options: HighlightingOptions = HighlightingOptions(),
                         index: () -> Int?) {
    highlightPartOfComponent(list, options, { index() }) l@{
      index()?.let {
        if (it in 0 until list.model.size) list.getCellBounds(it, it) else null
      }
    }
  }

  fun highlightJTreeItem(tree: JTree,
                         options: HighlightingOptions = HighlightingOptions(),
                         path: () -> TreePath?) {
    highlightPartOfComponent(tree, options) {
      path()?.let { tree.getPathBounds(it) }
    }
  }

  fun <T : Component> highlightPartOfComponent(component: T,
                                               options: HighlightingOptions = HighlightingOptions(),
                                               partInfo: () -> Any? = { null },
                                               rectangle: (T) -> Rectangle?) {
    highlightComponent(component, options.clearPreviousHighlights) {
      RepaintHighlighting(component, options, partInfo) l@{
        val rect = rectangle(component) ?: return@l null
        if (component !is JComponent) return@l rect
        val intersection = component.visibleRect.intersection(rect)
        when {
          intersection.isEmpty -> null
          !options.limitByVisibleRect -> rect
          else -> intersection
        }
      }
    }
  }

  fun clearHighlights() {
    runInEdt {
      for (core in highlights) {
        removeIt(core)
      }
      highlights.clear()
    }
  }

  private fun highlightComponent(original: Component,
                                 clearPreviousHighlights: Boolean,
                                 init: () -> RepaintHighlighting<*>) {
    runInEdt {
      if (clearPreviousHighlights) clearHighlights()
      if (!original.isShowing) return@runInEdt  // this check is required in rare cases when highlighting called after restore
      val repaintByTimer = init()
      repaintByTimer.reinitHighlightComponent()
      repaintByTimer.initTimer()
      highlights.add(repaintByTimer)
    }
  }

  internal fun removeIt(core: RepaintHighlighting<*>) {
    core.removed = true
    core.cleanup()
  }

  fun getRectangle(original: Component): Rectangle? =
    highlights.find { it.original == original }?.rectangle?.invoke()
}

internal class RepaintHighlighting<T : Component>(val original: T,
                                                  val options: LearningUiHighlightingManager.HighlightingOptions,
                                                  val partInfo: () -> Any?,
                                                  val rectangle: () -> Rectangle?
) {
  var removed = false

  private val startDate = Date()
  private var listLocationOnScreen: Point? = null
  private var cellBoundsInList: Rectangle? = null
  private var highlightPainter: LearningHighlightPainter? = null
  private val pulsationOffset = if (options.usePulsation) pulsationSize else 0

  private var disposable: Disposable? = null

  fun initTimer() {
    val timer = TimerUtil.createNamedTimer("IFT item", 50)
    timer.addActionListener {
      if (!original.isShowing || original.bounds.isEmpty) {
        LearningUiHighlightingManager.removeIt(this)
      }
      if (this.removed) {
        timer.stop()
        return@addActionListener
      }
      if (shouldReinit()) {
        cleanup()
        reinitHighlightComponent()
      }
      highlightPainter?.setNeedsRepaint(true)
    }
    timer.start()
  }

  fun cleanup() {
    disposable?.let {
      Disposer.dispose(it)
      disposable = null
    }
    highlightPainter = null
  }

  private fun shouldReinit(): Boolean {
    return highlightPainter == null || original.locationOnScreen != listLocationOnScreen || rectangle() != cellBoundsInList
  }

  fun reinitHighlightComponent() {
    val cellBounds = rectangle() ?: return
    cleanup()

    val pt = SwingUtilities.convertPoint(original, cellBounds.location, SwingUtilities.getRootPane(original).glassPane)
    val bounds = Rectangle(pt.x - pulsationOffset, pt.y - pulsationOffset, cellBounds.width + 2 * pulsationOffset,
                           cellBounds.height + 2 * pulsationOffset)

    val newPainter = LearningHighlightPainter(startDate, options, bounds)
    Disposer.newDisposable("RepaintHighlightingDisposable").let {
      disposable = it
      findIdeGlassPane(original).addPainter(null, newPainter, it)
    }

    listLocationOnScreen = original.locationOnScreen
    cellBoundsInList = cellBounds
    highlightPainter = newPainter
  }
}

internal class LearningHighlightPainter(
  private val startDate: Date,
  private val options: LearningUiHighlightingManager.HighlightingOptions,
  private val bounds: Rectangle
) : AbstractPainter() {
  private val pulsationOffset = if (options.usePulsation) pulsationSize else 0
  private var previous: Long = 0
  override fun executePaint(component: Component?, g: Graphics2D) {
    val r: Rectangle = bounds
    val time = Date().time
    val delta = time - startDate.time
    previous = time
    val shift = if (pulsationOffset != 0 && (delta / 1000) % 4 == 2.toLong()) {
      (((delta / 25 + 20) % 40 - 20).absoluteValue).toInt()
    }
    else 0

    fun cyclicNumber(amplitude: Int, change: Long) = (change % (2 * amplitude) - amplitude).absoluteValue.toInt()
    val alphaCycle = cyclicNumber(1000, delta).toDouble() / 1000
    val magenta = ColorUtil.withAlpha(Color.magenta, 0.8)
    val orange = ColorUtil.withAlpha(Color.orange, 0.8)
    val background = ColorUtil.withAlpha(JBColor(Color(0, 0, shift * 10), Color(255 - shift * 10, 255 - shift * 10, 255)),
      (0.3 + 0.7 * shift / 20.0) * alphaCycle)
    val gradientShift = (delta / 20).toFloat()
    val gp = GradientPaint(gradientShift + 0F, gradientShift + 0F, magenta,
      gradientShift + r.height.toFloat(), gradientShift + r.height.toFloat(), orange, true)

    val x = (r.x + pulsationOffset - shift).toDouble()
    val y = (r.y + pulsationOffset - shift).toDouble()
    val width = (r.width - (pulsationOffset - shift) * 2).toDouble()
    val height = (r.height - (pulsationOffset - shift) * 2).toDouble()
    val arc = JBUI.scale(if (options.isRoundedCorners) 16 else 2).toDouble()
    val thickness = JBUI.scale(options.thickness).toDouble()

    val outerRect = RoundRectangle2D.Double(x, y, width, height, arc, arc)
    val innerRect = RoundRectangle2D.Double(x + thickness, y + thickness, width - thickness * 2, height - thickness * 2, arc - thickness * 2, arc - thickness * 2)
    val border = Path2D.Double(Path2D.WIND_EVEN_ODD).apply {
      append(outerRect, false)
      append(innerRect, false)
      closePath()
    }

    val g2d = g.create() as Graphics2D
    try {
      GraphicsUtil.setupAAPainting(g2d)
      if (options.highlightInside) {
        val rect = if (options.highlightBorder) innerRect else outerRect
        g2d.color = background
        g2d.fill(rect)
      }
      if (options.highlightBorder) {
        g2d.paint = gp
        g2d.fill(border)
      }
    }
    finally {
      g2d.dispose()
    }
  }

  override fun needsRepaint(): Boolean = true
}

private fun findIdeGlassPane(component: Component): IdeGlassPane {
  val root = when (component) {
    is JComponent -> component.rootPane
    is RootPaneContainer -> component.rootPane
    else -> null
  } ?: throw IllegalArgumentException("Component must be visible in order to find glass pane for it")
  val gp = root.glassPane
  require(gp is IdeGlassPane) { "Glass pane should be " + IdeGlassPane::class.java.name }
  return gp
}

