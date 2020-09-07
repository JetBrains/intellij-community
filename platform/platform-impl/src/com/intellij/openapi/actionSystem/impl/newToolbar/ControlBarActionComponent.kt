// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.newToolbar

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MacUIUtil
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.util.*
import javax.swing.AbstractButton
import javax.swing.JComponent

class ControlBarActionComponent(actionGroup: ActionGroup) :
  AnAction(), CustomComponentAction {
  companion object {
    private const val CONTROL_BAR_PROPERTY = "CONTROL_BAR_PROPERTY"
    private const val CONTROL_BAR_FIRST = "CONTROL_BAR_PROPERTY_FIRST"
    private const val CONTROL_BAR_LAST = "CONTROL_BAR_PROPERTY_LAST"
    private const val CONTROL_BAR_MIDDLE = "CONTROL_BAR_PROPERTY_MIDDLE"

    fun isCustomBar(component: Component): Boolean {
      if (component !is JComponent) return false
      return component.getClientProperty(CONTROL_BAR_PROPERTY)?.let {
        true
      } ?: false
    }

    fun paintButtonDecorations(g: Graphics2D, c: JComponent, paint: Paint): Boolean {
      if (!(c as AbstractButton).isContentAreaFilled) {
        return true
      }
      val r = Rectangle(c.getSize())
      JBInsets.removeFrom(r, if (DarculaButtonUI.isSmallVariant(c)) c.getInsets() else JBUI.insets(1))

      val g2 = g.create() as Graphics2D
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)
        g2.translate(r.x, r.y)
        val arc = DarculaUIUtil.BUTTON_ARC.float
        val bw: Float = if (DarculaButtonUI.isSmallVariant(c)) 0f else DarculaUIUtil.BW.float

        if (c.isEnabled()) {
          g2.paint = Color.ORANGE //paint
          g2.fill(RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc))
        }
      }
      finally {
        g2.dispose()
      }
      return true
    }
  }


  private val buttonLook = object : ActionButtonLook() {
    override fun paintBorder(g: Graphics, c: JComponent, state: Int) {
    }

    override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
      if (state == ActionButtonComponent.NORMAL && !component.isBackgroundSet) return
      val rect = Rectangle(component.size)
      JBInsets.removeFrom(rect, component.insets)
      val color = when (state) {
        ActionButtonComponent.NORMAL -> component.background
        ActionButtonComponent.PUSHED -> JBUI.CurrentTheme.ActionButton.pressedBackground()
        else -> JBUI.CurrentTheme.ActionButton.hoverBackground()
      }
      paintLookBackground(g, rect, color)
    }
  }

  private var bar = object : ActionToolbarImpl(ActionPlaces.NAVIGATION_BAR_TOOLBAR, actionGroup, true) {


          override fun createToolbarButton(action: AnAction,
                                           look: ActionButtonLook?,
                                           place: String,
                                           presentation: Presentation,
                                           minimumSize: Dimension): ActionButton {


            val createToolbarButton = super.createToolbarButton(action, buttonLook, place, presentation, minimumSize)
            createToolbarButton.border = JBUI.Borders.empty()
            return createToolbarButton
          }

          override fun createCustomComponent(action: CustomComponentAction, presentation: Presentation): JComponent {
            var customComponent = super.createCustomComponent(action, presentation)
            if (action is ComboBoxAction) {
              customComponent = UIUtil.findComponentOfType(customComponent, ComboBoxAction.ComboBoxButton::class.java)
            }
            customComponent.border = JBUI.Borders.empty()
            return customComponent
          }

          override fun fillToolBar(actions: MutableList<out AnAction>, layoutSecondaries: Boolean) {
            val rightAligned: MutableList<AnAction> = ArrayList()
            for (i in actions.indices) {
              val action = actions[i]
              if (action is RightAlignedToolbarAction) {
                rightAligned.add(action)
                continue
              }

              if (action is CustomComponentAction) {
                val component = getCustomComponent(action)
                addMetadata(component, i, actions.size)
                add(CUSTOM_COMPONENT_CONSTRAINT, component)
              }
              else {
                val component = createToolbarButton(action)
                addMetadata(component, i, actions.size)
                add(ACTION_BUTTON_CONSTRAINT, component)
              }
            }
          }

          private fun addMetadata(component: JComponent, index: Int, count: Int) {
            val property = when (index) {
              0 -> CONTROL_BAR_FIRST
              count - 1 -> CONTROL_BAR_LAST
              else -> CONTROL_BAR_MIDDLE
            }
            component.putClientProperty(CONTROL_BAR_PROPERTY, property)
          }

          override fun actionsUpdated(forced: Boolean, newVisibleActions: MutableList<out AnAction>) {
            super.actionsUpdated(forced, newVisibleActions.filter { isSuitableAction(it) })
          }
        }



  private fun isSuitableAction(it: AnAction): Boolean {
    return it !is Separator || (it is ComboBoxAction) && (it !is CustomComponentAction) || (it is BarCustomComponentAction)
  }

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val component = bar.component
    component.background = Color.RED
    return component
  }
}

interface BarCustomComponentAction

private class BarActionButton(action: AnAction,
                              presentation: Presentation,
                              place: String,
                              minimumSize: Dimension) : ActionButton(action,
                                                                     presentation,
                                                                     place,
                                                                     minimumSize) {

}