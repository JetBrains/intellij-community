// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
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
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import kotlin.math.max

internal val WIDGET_EFFECT_KEY: Key<WidgetEffect> = Key.create("TextPanel.widgetEffect")

internal interface WidgetEffectBoundsProvider {
  fun getWidgetEffectBounds(): Rectangle
}

/**
 * Handles hover, press, and focus visual effects for status bar widgets.
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

    val highlightBounds = getBoundsOnStatusBar(component)
    val widgetEffect = ClientProperty.get(component, WIDGET_EFFECT_KEY)
    val bg = if (widgetEffect == WidgetEffect.PRESSED) {
      JBUI.CurrentTheme.StatusBar.Widget.PRESSED_BACKGROUND
    }
    else {
      JBUI.CurrentTheme.StatusBar.Widget.HOVER_BACKGROUND
    }

    paintHover(g, component, highlightBounds, bg, statusBar)
  }

  fun paintFocusBorder(g: Graphics) {
    val component = getFocusedWidgetComponent()
    if (component == null || !component.isEnabled || !UIUtil.isAncestor(statusBar, component)) {
      return
    }

    val focusBounds = getFocusBorderBounds(component)
    val arc = JBUIScale.scale(10).toFloat()
    DarculaNewUIUtil.drawRoundedRectangle(g, focusBounds, JBUI.CurrentTheme.Focus.focusColor(), arc, DarculaUIUtil.BW.float)
  }

  fun repaintFocusBorder(component: JComponent) {
    val focusBounds = getFocusBorderBounds(component)
    focusBounds.grow(DarculaUIUtil.BW.get(), DarculaUIUtil.BW.get())
    statusBar.repaint(focusBounds)
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

  private fun getFocusBorderBounds(component: JComponent): Rectangle {
    val focusBounds = getHoverBounds(component, getBoundsOnStatusBar(component), statusBar)
    focusBounds.grow(JBUIScale.scale(1), JBUIScale.scale(1))
    return focusBounds.intersection(Rectangle(focusBounds.x, 0, focusBounds.width, statusBar.height))
  }

  private fun getBoundsOnStatusBar(component: JComponent): Rectangle {
    if (component is WidgetEffectBoundsProvider) {
      return RelativeRectangle(component, Rectangle(component.getWidgetEffectBounds())).getRectangleOn(statusBar)
    }

    val bounds = component.bounds
    val point = RelativePoint(component.parent, bounds.location).getPoint(statusBar)
    bounds.location = point
    return bounds
  }

  private fun getFocusedWidgetComponent(): JComponent? {
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return null
    return statusBar.getFocusableWidgetComponents()
      .asReversed()
      .firstOrNull { focusOwner === it || UIUtil.isAncestor(it, focusOwner) }
  }

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
      val hoverBounds = getHoverBounds(component, highlightBounds, statusBar)
      g.color = bg
      if (ExperimentalUI.isNewUI()) {
        val g2 = g.create() as Graphics2D
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)
          val arc = JBUIScale.scale(4).toFloat()
          val shape: RoundRectangle2D = RoundRectangle2D.Float(
            hoverBounds.x.toFloat(),
            hoverBounds.y.toFloat(),
            hoverBounds.width.toFloat(),
            hoverBounds.height.toFloat(),
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
        g.fillRect(hoverBounds.x, hoverBounds.y, hoverBounds.width, hoverBounds.height)
      }
    }

    private fun getHoverBounds(component: JComponent, highlightBounds: Rectangle, statusBar: StatusBar): Rectangle {
      val hoverBounds = Rectangle(highlightBounds)
      if (!ExperimentalUI.isNewUI() && (statusBar as? JComponent)?.getUI() is StatusBarUI) {
        hoverBounds.y += StatusBarUI.BORDER_WIDTH.get()
        hoverBounds.height -= StatusBarUI.BORDER_WIDTH.get()
      }
      if (ExperimentalUI.isNewUI()) {
        JBInsets.removeFrom(hoverBounds, calcHoverInsetsCorrection(component))
      }
      return hoverBounds
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
