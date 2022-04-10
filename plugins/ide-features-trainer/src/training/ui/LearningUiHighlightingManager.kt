// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.TimerUtil
import java.awt.*
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
  )

  private val highlights: MutableList<RepaintHighlighting<*>> = ArrayList()

  val highlightingComponents: List<Component> get() = highlights.map { it.original }

  fun highlightComponent(original: Component, options: HighlightingOptions = HighlightingOptions()) {
    highlightPartOfComponent(original, options) { Rectangle(Point(0, 0), it.size) }
  }

  fun highlightJListItem(list: JList<*>,
                         options: HighlightingOptions = HighlightingOptions(),
                         index: () -> Int?) {
    highlightPartOfComponent(list, options) l@{
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
                                               rectangle: (T) -> Rectangle?) {
    highlightComponent(component, options.clearPreviousHighlights) {
      RepaintHighlighting(component, options) l@{
        val rect = rectangle(component) ?: return@l null
        if (component !is JComponent) return@l rect
        component.visibleRect.intersection(rect).takeIf { !it.isEmpty }
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
  val bounds: Rectangle
) : AbstractPainter() {
  private val pulsationOffset = if (options.usePulsation) pulsationSize else 0
  private var previous: Long = 0
  override fun executePaint(component: Component?, g: Graphics2D?) {
    val g2d = g as Graphics2D
    val r: Rectangle = bounds
    val oldColor = g2d.color
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

    val x = r.x + pulsationOffset - shift
    val y = r.y + pulsationOffset - shift
    val width = r.width - (pulsationOffset - shift) * 2
    val height = r.height - (pulsationOffset - shift) * 2
    RectanglePainter.paint(g2d, x, y, width, height, 2,
      if (options.highlightInside) background else null,
      if (options.highlightBorder) gp else null)
    g2d.color = oldColor
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

