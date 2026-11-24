// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.SwingUtilities

private val LOG = logger<LayerHighlighter>()

private val HIGHLIGHT_COLOR = JBColor(Color(0x2D6FE0), Color(0x5C9DFF))

/**
 * Shows one layer's area over the component its icon was painted into, on the glass pane of that component's window.
 * [area] is in [component] coordinates; it is converted on every paint, so the highlight follows the component.
 */
internal class LayerHighlighter private constructor(private val component: Component, private val area: Rectangle2D) : Disposable {
  private val painter = object : AbstractPainter() {
    override fun needsRepaint(): Boolean = true

    override fun executePaint(glassPane: Component, g: Graphics2D) {
      if (!component.isShowing) return
      val origin = SwingUtilities.convertPoint(component, 0, 0, glassPane)
      val rect = Rectangle2D.Double(origin.x + area.x, origin.y + area.y, area.width, area.height)
      val g2 = g.create() as Graphics2D
      try {
        g2.color = HIGHLIGHT_COLOR
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f)
        g2.fill(rect)
        g2.composite = AlphaComposite.SrcOver
        g2.stroke = BasicStroke(JBUIScale.scale(1f))
        g2.draw(rect)
      }
      finally {
        g2.dispose()
      }
    }
  }

  private fun install(glassPane: IdeGlassPane): LayerHighlighter {
    glassPane.addPainter(component, painter, this)
    repaint()
    return this
  }

  private fun repaint() {
    (component as? JComponent)?.rootPane?.glassPane?.repaint()
  }

  override fun dispose() {
    repaint()
  }

  fun close() {
    Disposer.dispose(this)
  }

  companion object {
    /** Shows the highlight, or returns null when [component] has no IDE glass pane (e.g. it is not showing). */
    fun show(component: Component, area: Rectangle2D): LayerHighlighter? {
      if (component !is JComponent || !component.isShowing) return null
      val glassPane = try {
        IdeGlassPaneUtil.find(component)
      }
      catch (e: IllegalArgumentException) {
        LOG.debug(e)
        return null
      }
      return LayerHighlighter(component, area).install(glassPane)
    }
  }
}
