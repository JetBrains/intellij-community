package ui

import java.awt.*
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.plaf.LayerUI

class SpotlightLayerUI(var highlightRadius: Float = 32F,
                       var highlightColor: Color = Color.YELLOW,
                       val ringRadius: Int = 24,
                       val ringColor: Color = Color.CYAN) : LayerUI<JPanel>() {
  var active = false

  private var mouseActive: Boolean = false
  private var mX: Int = 0
  private var mY: Int = 0

  private val ringAlfaColor = ringColor.setAlfa(230)
  private val ring = Ring(0, 0, 10, 4, ringAlfaColor)
  private var ringIsVisible = false
  private val maxRingRadius = ringRadius
  private val minRingRadius = 5

  private val startFrame = 1
  private val finishFrame = 50

  private var spotIsVisible = false
  private val highlightDist = floatArrayOf(0.0f, 1.0f)
  private val highlightColors = arrayOf(highlightColor, Color.BLACK.setAlfa(0))

  private var lastPaintArea: Rectangle? = null
  private var ringAnimatedThread: Thread? = null

  private fun startAnimation(jLayer: JLayer<*>) {
    if (ringAnimatedThread != null && ringAnimatedThread!!.isAlive) ringAnimatedThread!!.interrupt()
    ringAnimatedThread = Thread {
      try {
        ringIsVisible = true
        var lastRepArea: Rectangle? = null
        for (i in startFrame..finishFrame) {
          ring.radius = minRingRadius + (i * (maxRingRadius - minRingRadius)) / finishFrame
          val alpha = 255 - 255 * i / finishFrame
          ring.color = ring.color.setAlfa(alpha)
          SwingUtilities.invokeLater {
            val currRepArea = repaintedArea()
            val rectToRepaint = if (lastRepArea != null) lastRepArea!!.union(currRepArea) else currRepArea
            lastRepArea = currRepArea
            jLayer.repaint(rectToRepaint)
          }
          Thread.sleep(5)
        }
      }
      catch (e: InterruptedException) {
        Thread.interrupted()
      }
      finally {
        ringIsVisible = false
      }
    }
    ringAnimatedThread!!.start()
  }


  override fun installUI(c: JComponent?) {
    super.installUI(c)
    val jLayer = c as JLayer<*>
    jLayer.layerEventMask = AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK
  }

  override fun uninstallUI(c: JComponent?) {
    val jlayer = c as JLayer<*>?
    jlayer!!.layerEventMask = 0
    super.uninstallUI(c)
  }

  override fun paint(g: Graphics, c: JComponent) {
    withTransform(g) { g2 ->

      // Paint the view.
      super.paint(g2, c)

      if (mouseActive && active) {
        // Create a radial gradient, transparent in the middle.
        if (spotIsVisible) {
          val center = Point2D.Float(mX.toFloat(), mY.toFloat())
          val p = RadialGradientPaint(center, highlightRadius, highlightDist, highlightColors)
          val clip = g2.clipBounds

          g2.paint = p
          g2.composite = AlphaComposite.getInstance(
            AlphaComposite.SRC_OVER, .6f)
          g2.fillRect(clip.x, clip.y, clip.width, clip.height)
        }

        if (ringIsVisible) {
          ring.centerX = mX
          ring.centerY = mY
          ring.draw(g2)
        }
      }
    }
  }

  private fun repaintedArea(): Rectangle {
    val radius = Math.max(maxRingRadius, highlightRadius.toInt())
    val diameter = radius * 2
    val x0 = mX - radius
    val y0 = mY - radius
    val newRectangle = Rectangle(x0, y0, diameter, diameter)
    val areaToRepaint = if (lastPaintArea != null) newRectangle.union(lastPaintArea) else newRectangle
    lastPaintArea = newRectangle
    return areaToRepaint
  }

  override fun processMouseEvent(e: MouseEvent, jLayer: JLayer<out JPanel>) {
    if (e.id == MouseEvent.MOUSE_ENTERED) mouseActive = true
    if (e.id == MouseEvent.MOUSE_EXITED) mouseActive = false
    if (e.id == MouseEvent.MOUSE_CLICKED) startAnimation(jLayer)
  }

  override fun processMouseMotionEvent(e: MouseEvent, jLayer: JLayer<out JPanel>) {
    val p = SwingUtilities.convertPoint(e.component, e.point, jLayer)
    mX = p.x
    mY = p.y
    jLayer.repaint(repaintedArea())
  }

  private fun withTransform(g: Graphics, transform: (Graphics2D) -> Unit) {
    val g2 = g as Graphics2D
    val oldTransform = g2.transform
    transform(g2)
    g2.transform = oldTransform
  }
}

class Ring(var centerX: Int, var centerY: Int, var radius: Int, var thickness: Int, var color: Color) {


  fun draw(g2: Graphics2D) {
    val oldStroke = g2.stroke
    val oldColor = g2.color

    val x = centerX - radius
    val y = centerY - radius

    g2.clip = Rectangle(x, y, radius * 2, radius * 2);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.color = color
    g2.stroke = BasicStroke((thickness - 2).toFloat())
    g2.drawOval(x + thickness / 2, y + thickness / 2, radius * 2 - thickness, radius * 2 - thickness)
    g2.stroke = oldStroke
    g2.color = oldColor
  }

}

private fun Color.setAlfa(alfa: Int): Color {
  return Color(this.red, this.green, this.blue, alfa)
}

private fun Color.setAlfa(alfa: Float): Color {
  return Color(this.red, this.green, this.blue, (255 * alfa).toInt())
}
