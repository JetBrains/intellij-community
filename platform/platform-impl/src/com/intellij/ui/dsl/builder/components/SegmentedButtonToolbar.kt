// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.components

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.border.Border
import kotlin.math.max
import kotlin.math.roundToInt

@Deprecated("Use Row.segmentedButton")
@ApiStatus.ScheduledForRemoval
class SegmentedButtonToolbar(actionGroup: ActionGroup, private val spacingConfiguration: SpacingConfiguration) :
  ActionToolbarImpl("ButtonSelector", actionGroup, true, true, true) {

  init {
    isFocusable = true
    border = SegmentedButtonBorder()
    setForceMinimumSize(true)
    // Buttons preferred size is calculated in SegmentedButton.getPreferredSize, so reset default size
    setMinimumButtonSize(Dimension(0, 0))
    layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps(size = DarculaUIUtil.BW.unscaled.roundToInt()))
    putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(true, true))

    addFocusListener(object : FocusListener {
      override fun focusGained(e: FocusEvent?) {
        repaint()
      }

      override fun focusLost(e: FocusEvent?) {
        repaint()
      }
    })

    val actionLeft = DumbAwareAction.create { moveSelection(-1) }
    actionLeft.registerCustomShortcutSet(ActionUtil.getShortcutSet("SegmentedButton-left"), this)
    val actionRight = DumbAwareAction.create { moveSelection(1) }
    actionRight.registerCustomShortcutSet(ActionUtil.getShortcutSet("SegmentedButton-right"), this)
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
      g2.paint = getSegmentedButtonBorderPaint(this, true)
      for (component in components) {
        if ((component as? DeprecatedSegmentedButton)?.isSelected == true) {
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
                                   minimumSize: Supplier<out Dimension>): ActionButton {
    val result = DeprecatedSegmentedButton(action, presentation, place, minimumSize, spacingConfiguration)
    result.isEnabled = isEnabled
    return result
  }

  override fun addNotify() {
    super.addNotify()

    // Create actions immediately, otherwise first SegmentedButtonToolbar preferred size calculation can be done without actions.
    // In such case SegmentedButtonToolbar will keep narrow width for preferred size because of ActionToolbar.WRAP_LAYOUT_POLICY
    updateActionsImmediately(true)
  }

  @ApiStatus.Internal
  internal fun getSelectedOption(): Any? {
    val selectedButton = getSelectedButton()
    return if (selectedButton == null) null else (selectedButton.action as? DeprecatedSegmentedButtonAction<*>)?.option
  }

  private fun moveSelection(step: Int) {
    if (components.isEmpty()) {
      return
    }

    val selectedButton = getSelectedButton()
    val selectedIndex = components.indexOf(selectedButton)
    val newSelectedIndex = if (selectedIndex < 0) 0 else (selectedIndex + step).coerceIn(0, components.size - 1)
    if (selectedIndex != newSelectedIndex) {
      (components.getOrNull(selectedIndex) as? DeprecatedSegmentedButton)?.isSelected = false
      (components[newSelectedIndex] as? DeprecatedSegmentedButton)?.click()
    }
  }

  private fun getSelectedButton(): DeprecatedSegmentedButton? {
    for (component in components) {
      if ((component as? DeprecatedSegmentedButton)?.isSelected == true) {
        return component
      }
    }
    return null
  }
}

@ApiStatus.Experimental
@Deprecated("Use Row.segmentedButton")
@ApiStatus.ScheduledForRemoval
internal class DeprecatedSegmentedButtonAction<T>(val option: T,
                                                  private val property: ObservableMutableProperty<T>,
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

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

@Deprecated("Use Row.segmentedButton")
private class DeprecatedSegmentedButton(
  action: AnAction,
  presentation: Presentation,
  place: String,
  minimumSize: Supplier<out Dimension>,
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
    return Dimension(preferredSize.width + JBUIScale.scale(spacingConfiguration.segmentedButtonHorizontalGap) * 2,
                     preferredSize.height + JBUIScale.scale(spacingConfiguration.segmentedButtonVerticalGap) * 2)
  }

  fun setSelected(selected: Boolean) {
    Toggleable.setSelected(myPresentation, selected)
  }
}

/**
 * @param subButton determines border target: true for button inside segmented button, false for segmented button
 */
@ApiStatus.Internal
internal fun getSegmentedButtonBorderPaint(segmentedButton: Component, subButton: Boolean): Paint {
  if (!segmentedButton.isEnabled) {
    return JBUI.CurrentTheme.Button.disabledOutlineColor()
  }

  if (segmentedButton.hasFocus()) {
    return JBUI.CurrentTheme.Button.focusBorderColor(false)
  }
  else {
    if (subButton) {
      return GradientPaint(0f, 0f, JBUI.CurrentTheme.SegmentedButton.SELECTED_START_BORDER_COLOR,
                           0f, segmentedButton.height.toFloat(), JBUI.CurrentTheme.SegmentedButton.SELECTED_END_BORDER_COLOR)
    } else {
      return GradientPaint(0f, 0f, JBUI.CurrentTheme.Button.buttonOutlineColorStart(false),
                           0f, segmentedButton.height.toFloat(), JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false))
    }
  }
}

@ApiStatus.Internal
internal fun paintBorder(g: Graphics2D, r: Rectangle) {
  val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
  val lw = DarculaUIUtil.LW.float
  var arc = DarculaUIUtil.BUTTON_ARC.float
  border.append(RoundRectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), r.height.toFloat(), arc, arc), false)
  arc = max(arc - lw, 0f)
  border.append(RoundRectangle2D.Float(r.x + lw, r.y + lw, r.width - lw * 2, r.height - lw * 2, arc, arc), false)
  g.fill(border)
}

@ApiStatus.Internal
internal class SegmentedButtonBorder : Border {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      g2.translate(x, y)
      val r = Rectangle(0, 0, width, height)
      val arc = DarculaUIUtil.BUTTON_ARC.float
      var outline = DarculaUIUtil.getOutline(c as JComponent)
      if (outline == null && c.hasFocus()) {
        outline = DarculaUIUtil.Outline.focus
      }
      if (outline == null) {
        g2.paint = getSegmentedButtonBorderPaint(c, false)
        JBInsets.removeFrom(r, JBUI.insets(DarculaUIUtil.BW.unscaled.toInt()))
        paintBorder(g2, r)
      }
      else {
        DarculaUIUtil.paintOutlineBorder(g2, r.width, r.height, arc, true, c.hasFocus(), outline)
      }
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

@ApiStatus.Internal
internal object SegmentedButtonLook : IdeaActionButtonLook() {

  override fun paintBorder(g: Graphics, component: JComponent, state: Int) {
    // Border is painted in parent
  }

  override fun getStateBackground(component: JComponent, state: Int): Color? {
    if (!component.isEnabled) {
      return if (component.isBackgroundSet) component.background else null
    }

    val focused = component.parent?.hasFocus() == true

    return when (state) {
      ActionButtonComponent.POPPED -> JBUI.CurrentTheme.ActionButton.hoverBackground()
      ActionButtonComponent.PUSHED ->
        if (focused) JBUI.CurrentTheme.SegmentedButton.FOCUSED_SELECTED_BUTTON_COLOR
        else JBUI.CurrentTheme.SegmentedButton.SELECTED_BUTTON_COLOR
      else -> if (component.isBackgroundSet) component.background else null
    }
  }
}
