// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.components

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.border.Border
import kotlin.math.max

@ApiStatus.Experimental
class SegmentedButtonToolbar(actionGroup: ActionGroup, private val spacingConfiguration: SpacingConfiguration) :
  ActionToolbarImpl("ButtonSelector", actionGroup, true, true) {

  init {
    border = SegmentedButtonBorder()
    setForceMinimumSize(true)
    // Buttons preferred size is calculated in SegmentedButton.getPreferredSize, so reset default size
    setMinimumButtonSize(Dimension(0, 0))
    layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY

    addFocusListener(object : FocusListener {
      override fun focusGained(e: FocusEvent?) {
        repaint()
      }

      override fun focusLost(e: FocusEvent?) {
        repaint()
      }
    })
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)

    for (component in components) {
      component.isEnabled = enabled
    }
  }

  override fun paint(g: Graphics) {
    super.paint(g)

    // Paint selected button frame over all children
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      val color = getBorderColor(isEnabled, hasFocus())
      g2.paint = color
      for (component in components) {
        if ((component as? SegmentedButton)?.isSelected == true) {
          val r = component.bounds
          JBInsets.addTo(r, JBUI.insets(DarculaUIUtil.LW.unscaled.toInt()))
          paintBorder(g2, r)
        }
      }
    }
    finally {
      g2.dispose()
    }
  }

  override fun createToolbarButton(action: AnAction,
                                   look: ActionButtonLook?,
                                   place: String,
                                   presentation: Presentation,
                                   minimumSize: Dimension): ActionButton {
    val result = SegmentedButton(action, presentation, place, minimumSize, spacingConfiguration)
    result.isEnabled = isEnabled
    return result
  }

  override fun addNotify() {
    super.addNotify()

    // Create actions immediately, otherwise first SegmentedButtonToolbar preferred size calculation can be done without actions.
    // In such case SegmentedButtonToolbar will keep narrow width for preferred size because of ActionToolbar.WRAP_LAYOUT_POLICY
    updateActionsImmediately(true)
  }
}

@ApiStatus.Experimental
internal class SegmentedButtonAction<T>(private val option: T,
                                        private val property: GraphProperty<T>,
                                        @NlsActions.ActionText optionText: String,
                                        @NlsActions.ActionDescription optionDescription: String? = null)
  : ToggleAction(optionText, optionDescription, null), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return property.get() == option
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      property.set(option)
    }
  }
}

private class SegmentedButton(
  action: AnAction,
  presentation: Presentation,
  place: String?,
  minimumSize: Dimension,
  private val spacingConfiguration: SpacingConfiguration
) : ActionButtonWithText(action, presentation, place, minimumSize) {

  init {
    setLook(SegmentedButtonLook)
    myPresentation.addPropertyChangeListener {
      if (it.propertyName == Toggleable.SELECTED_PROPERTY) {
        parent?.repaint()
      }
    }
  }

  override fun getPreferredSize(): Dimension {
    val preferredSize = super.getPreferredSize()
    return Dimension(preferredSize.width + spacingConfiguration.segmentedButtonHorizontalGap * 2,
                     preferredSize.height + spacingConfiguration.segmentedButtonVerticalGap * 2)
  }
}

private fun getBorderColor(enabled: Boolean, hasFocus: Boolean): Color {
  return if (enabled) {
    if (hasFocus) JBUI.CurrentTheme.Button.focusBorderColor(false)
    else
      JBUI.CurrentTheme.Button.buttonOutlineColorStart(false)
  }
  else {
    JBUI.CurrentTheme.Button.disabledOutlineColor()
  }
}

private fun paintBorder(g: Graphics2D, r: Rectangle) {
  val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
  val lw = DarculaUIUtil.LW.float
  var arc = DarculaUIUtil.BUTTON_ARC.float
  border.append(RoundRectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), r.height.toFloat(), arc, arc), false)
  arc = max(arc - lw, 0f)
  border.append(RoundRectangle2D.Float(r.x + lw, r.y + lw, r.width - lw * 2, r.height - lw * 2, arc, arc), false)
  g.fill(border)
}

private class SegmentedButtonBorder : Border {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      val r = Rectangle(x, y, width, height)
      val arc = DarculaUIUtil.BUTTON_ARC.float
      if (c.hasFocus()) {
        DarculaUIUtil.paintOutlineBorder(g2, r.width, r.height, arc, true, true, DarculaUIUtil.Outline.focus)
      }
      g2.paint = getBorderColor(c.isEnabled, c.hasFocus())
      JBInsets.removeFrom(r, JBUI.insets(DarculaUIUtil.BW.unscaled.toInt()))
      paintBorder(g2, r)
    }
    finally {
      g2.dispose()
    }
  }

  override fun getBorderInsets(c: Component?): Insets {
    val unscaledSize = DarculaUIUtil.BW.unscaled + DarculaUIUtil.LW.unscaled
    return JBUI.insets(unscaledSize.toInt()).asUIResource()
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }
}

private object SegmentedButtonLook : IdeaActionButtonLook() {

  override fun paintBorder(g: Graphics, component: JComponent, state: Int) {
    // Border is painted in parent
  }

  override fun getStateBackground(component: JComponent, state: Int): Color {
    if (!component.isEnabled) {
      return component.background
    }

    val focused = component.parent?.hasFocus() == true

    return when (state) {
      ActionButtonComponent.POPPED -> JBUI.CurrentTheme.ActionButton.hoverBackground()
      ActionButtonComponent.PUSHED -> if (focused) JBUI.CurrentTheme.TabbedPane.FOCUS_COLOR else JBUI.CurrentTheme.Button.buttonColorStart()
      else -> component.background
    }
  }
}
