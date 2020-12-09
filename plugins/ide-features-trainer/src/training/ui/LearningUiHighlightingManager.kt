// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.TimerUtil
import java.awt.*
import java.util.*
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.TreePath
import kotlin.math.absoluteValue

object LearningUiHighlightingManager {
  data class HighlightingOptions(
    val highlightBorder: Boolean = true,
    val highlightInside: Boolean = true,
    val clearPreviousHighlights: Boolean = true,
  )

  private val highlights: MutableList<RepaintByTimer<*>> = ArrayList()

  fun highlightComponent(original: Component, options: HighlightingOptions = HighlightingOptions()) {
    highlightComponent(original, options.clearPreviousHighlights) { glassPane ->
      RepaintByTimer(original, glassPane, options)
    }
  }

  fun highlightJListItem(list: JList<*>,
                         options: HighlightingOptions = HighlightingOptions(),
                         index: () -> Int?) {
    highlightPartOfComponent(list, options) l@{
      val i = index()
      if (i == null || i < 0 && list.visibleRowCount <= i) null
      else list.getCellBounds(i, i)
    }
  }

  fun highlightJTreeItem(tree: JTree,
                         options: HighlightingOptions = HighlightingOptions(),
                         path: () -> TreePath?) {
    highlightPartOfComponent(tree, options) {
      path()?.let { tree.getPathBounds(it) }
    }
  }

  fun <T: Component> highlightPartOfComponent(component: T, options: HighlightingOptions = HighlightingOptions(), rectangle: (T) -> Rectangle?) {
    highlightComponent(component, options.clearPreviousHighlights) { glassPane ->
      GeneralPartRepaint(component, glassPane, options, { rectangle(component) })
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

  private fun highlightComponent(original: Component, clearPreviousHighlights: Boolean, init: (glassPane: JComponent) -> RepaintByTimer<*>) {
    runInEdt {
      if (clearPreviousHighlights) clearHighlights()
      val glassPane = getGlassPane(original) ?: return@runInEdt
      val repaintByTimer = init(glassPane)
      repaintByTimer.reinitHighlightComponent()
      repaintByTimer.initTimer()
      highlights.add(repaintByTimer)
    }
  }

  internal fun removeIt(core: RepaintByTimer<*>) {
    core.removed = true
    core.cleanup()
  }

  fun getRectangle(original: Component): Rectangle? =
    highlights.find { it.original == original }?.rectangle?.invoke()
}

internal open class RepaintByTimer<T : Component>(val original: T,
                                                  val glassPane: JComponent,
                                                  val options: LearningUiHighlightingManager.HighlightingOptions) {
  var removed = false
  protected val startDate = Date()

  protected var highlightComponent: GlassHighlightComponent? = null

  open val rectangle: () -> Rectangle? = { Rectangle(0, 0, original.width, original.height) }

  open fun reinitHighlightComponent() {
    val newHighlightComponent = GlassHighlightComponent(startDate, options)

    val pt = SwingUtilities.convertPoint(original, Point(0, 0), glassPane)
    val bounds = Rectangle(pt.x, pt.y, original.width, original.height)

    newHighlightComponent.bounds = bounds
    glassPane.add(newHighlightComponent)
    highlightComponent = newHighlightComponent
  }

  fun initTimer() {
    val timer = TimerUtil.createNamedTimer("IFT item", 50)
    timer.addActionListener {
      if (!original.isShowing) {
        LearningUiHighlightingManager.removeIt(this)
      }
      if (this.removed) {
        timer.stop()
        return@addActionListener
      }
      if (shouldReinit()) {
        cleanup()
        highlightComponent = null
        reinitHighlightComponent()
      }
      glassPane.repaint()
    }
    timer.start()
  }

  protected open fun shouldReinit(): Boolean {
    val component = highlightComponent
    return component == null || original.locationOnScreen != component.locationOnScreen || original.size != component.size
  }

  fun cleanup() {
    highlightComponent?.let { glassPane.remove(it) }
    if (glassPane.isValid) {
      glassPane.revalidate()
      glassPane.repaint()
    }
  }
}

internal class GeneralPartRepaint<T : Component>(whole: T,
                                                 glassPane: JComponent,
                                                 options: LearningUiHighlightingManager.HighlightingOptions,
                                                 override val rectangle: () -> Rectangle?
) : RepaintByTimer<T>(whole, glassPane, options) {
  private var listLocationOnScreen: Point? = null
  private var cellBoundsInList: Rectangle? = null

  override fun shouldReinit(): Boolean {
    return highlightComponent == null || original.locationOnScreen != listLocationOnScreen || rectangle() != cellBoundsInList
  }

  override fun reinitHighlightComponent() {
    val cellBounds = rectangle() ?: return

    val newHighlightComponent = GlassHighlightComponent(startDate, options)

    val pt = SwingUtilities.convertPoint(original, cellBounds.location, glassPane)
    val bounds = Rectangle(pt.x, pt.y, cellBounds.width, cellBounds.height)

    newHighlightComponent.bounds = bounds
    glassPane.add(newHighlightComponent)
    highlightComponent = newHighlightComponent
    listLocationOnScreen = original.locationOnScreen
    cellBoundsInList = cellBounds
  }
}

internal class GlassHighlightComponent(private val startDate: Date,
                                       private val options: LearningUiHighlightingManager.HighlightingOptions) : JComponent() {

  private var previous: Long = 0

  override fun paintComponent(g: Graphics) {
    val g2d = g as Graphics2D
    val r: Rectangle = bounds
    val oldColor = g2d.color
    val time = Date().time
    val delta = time - startDate.time
    //System.err.println("tik = ${time - previous}")
    previous = time
    fun cyclicNumber(amplitude: Int, change: Long) = (change % (2 * amplitude) - amplitude).absoluteValue.toInt()
    val alphaCycle = cyclicNumber(1000, delta).toDouble() / 1000
    val magenta = ColorUtil.withAlpha(Color.magenta, 0.8)
    val orange = ColorUtil.withAlpha(Color.orange, 0.8)
    val background = ColorUtil.withAlpha(JBColor(Color.black, Color.white), 0.3 * alphaCycle)
    val gradientShift = (delta / 20).toFloat()
    val gp = GradientPaint(gradientShift + 0F, gradientShift + 0F, magenta,
                           gradientShift + r.height.toFloat(), gradientShift + r.height.toFloat(), orange, true)
    RectanglePainter.paint(g2d, 0, 0, r.width, r.height, 2,
                           if (options.highlightInside) background else null,
                           if (options.highlightBorder) gp else null)
    g2d.color = oldColor
  }
}

private fun getGlassPane(component: Component): JComponent? {
  val rootPane = SwingUtilities.getRootPane(component)
  return if (rootPane == null) null else rootPane.glassPane as JComponent
}
