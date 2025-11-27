// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl.WidgetEffect
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MacUIUtil
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import kotlin.math.max

internal val WIDGET_EFFECT_KEY: Key<WidgetEffect> = Key.create("TextPanel.widgetEffect")

/**
 * Handles hover and press visual effects for status bar widgets.
 */
internal class WidgetEffectRenderer(private val statusBar: IdeStatusBarImpl) {
  private var effectComponent: JComponent? = null

  /**
   * Applies a visual effect (hover/press) to the specified component.
   * Clears any existing effect on the previous component.
   */
  fun applyEffect(component: JComponent?, widgetEffect: WidgetEffect?) {
    if (effectComponent === component &&
        (effectComponent == null || ClientProperty.get(effectComponent, WIDGET_EFFECT_KEY) == widgetEffect)) {
      return
    }

    if (effectComponent != null) {
      ClientProperty.put(effectComponent!!, WIDGET_EFFECT_KEY, null)
      statusBar.repaint(RelativeRectangle(effectComponent).getRectangleOn(statusBar))
    }

    effectComponent = component
    val target = component ?: return
    // widgets shall not be opaque, as it may conflict with a background image
    target.background = null
    ClientProperty.put(target, WIDGET_EFFECT_KEY, widgetEffect)
    if (target.isEnabled && widgetEffect != null) {
      target.background = if (widgetEffect == WidgetEffect.HOVER) {
        JBUI.CurrentTheme.StatusBar.Widget.HOVER_BACKGROUND
      }
      else {
        JBUI.CurrentTheme.StatusBar.Widget.PRESSED_BACKGROUND
      }
    }
    statusBar.repaint(RelativeRectangle(target).getRectangleOn(statusBar))
  }

  /**
   * Paints the effect background for the currently highlighted widget.
   * Called from [IdeStatusBarImpl.paintChildren].
   */
  fun paintBackground(g: Graphics) {
    val component = effectComponent ?: return
    if (!component.isEnabled || !UIUtil.isAncestor(statusBar, component) || MemoryUsagePanel.isInstance(component)) {
      return
    }

    val highlightBounds = component.bounds
    val point = RelativePoint(component.parent, highlightBounds.location).getPoint(statusBar)
    highlightBounds.location = point

    val widgetEffect = ClientProperty.get(component, WIDGET_EFFECT_KEY)
    val bg = if (widgetEffect == WidgetEffect.PRESSED) {
      JBUI.CurrentTheme.StatusBar.Widget.PRESSED_BACKGROUND
    }
    else {
      JBUI.CurrentTheme.StatusBar.Widget.HOVER_BACKGROUND
    }

    paintHover(g, component, highlightBounds, bg, statusBar)
  }

  /**
   * Clears effect on the specified component if it matches.
   * Used when a widget is removed.
   */
  fun clearIfMatches(component: JComponent) {
    effectComponent?.let {
      if (UIUtil.isAncestor(component, it)) {
        ClientProperty.put(it, WIDGET_EFFECT_KEY, null)
        effectComponent = null
      }
    }
  }

  /**
   * Returns the currently hovered/pressed widget ID, if any.
   */
  fun getEffectWidgetId(): String? = ClientProperty.get(effectComponent, WIDGET_ID)

  companion object {
    private val WIDGET_ID = Key.create<String>("STATUS_BAR_WIDGET_ID")

    @JvmStatic
    fun paintHover(
      g: Graphics,
      component: JComponent,
      highlightBounds: Rectangle,
      bg: Color,
      statusBar: StatusBar,
    ) {
      if (!ExperimentalUI.isNewUI() && (statusBar as? JComponent)?.getUI() is StatusBarUI) {
        highlightBounds.y += StatusBarUI.BORDER_WIDTH.get()
        highlightBounds.height -= StatusBarUI.BORDER_WIDTH.get()
      }
      g.color = bg
      if (ExperimentalUI.isNewUI()) {
        JBInsets.removeFrom(highlightBounds, calcHoverInsetsCorrection(component))
        val g2 = g.create() as Graphics2D
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)
          val arc = JBUIScale.scale(4).toFloat()
          val shape: RoundRectangle2D = RoundRectangle2D.Float(
            highlightBounds.x.toFloat(),
            highlightBounds.y.toFloat(),
            highlightBounds.width.toFloat(),
            highlightBounds.height.toFloat(),
            arc,
            arc,
          )
          g2.fill(shape)
        }
        finally {
          g2.dispose()
        }
      }
      else {
        g.fillRect(highlightBounds.x, highlightBounds.y, highlightBounds.width, highlightBounds.height)
      }
    }

    private fun calcHoverInsetsCorrection(effectComponent: JComponent): Insets {
      val comp = effectComponent.insets
      val hover = JBUI.CurrentTheme.StatusBar.hoverInsets()

      // Don't allow hover be outside the component
      @Suppress("UseDPIAwareInsets")
      return Insets(
        max(0, comp.top - hover.top),
        max(0, comp.left - hover.left),
        max(0, comp.bottom - hover.bottom),
        max(0, comp.right - hover.right),
      )
    }
  }
}
