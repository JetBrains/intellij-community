// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.newToolbar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel

open class ControlBarActionComponent : AnAction(), CustomComponentAction {
  enum class ControlBarProperty {
    FIRST,
    LAST,
    MIDDLE,
    SINGLE
  }

  companion object {
    internal const val CONTROL_BAR_PROPERTY = "CONTROL_BAR_PROPERTY"
    internal const val CONTROL_BAR_FIRST = "CONTROL_BAR_PROPERTY_FIRST"
    internal const val CONTROL_BAR_LAST = "CONTROL_BAR_PROPERTY_LAST"
    internal const val CONTROL_BAR_MIDDLE = "CONTROL_BAR_PROPERTY_MIDDLE"
    internal const val CONTROL_BAR_SINGLE = "CONTROL_BAR_PROPERTY_SINGLE"

    private val painter = ControlBarPainter()

    fun isCustomBar(component: Component): Boolean {
      if (component !is JComponent) return false
      return component.getClientProperty(CONTROL_BAR_PROPERTY)?.let {
        it != CONTROL_BAR_SINGLE
      } ?: false
    }

    fun paintButtonDecorations(g: Graphics2D, c: JComponent, paint: Paint): Boolean {
      return painter.paintButtonDecorations(g, c, paint)
    }

  }

  protected var actionGroup: ActionGroup? = null
    set(value) {
      field?.let { return }
      value ?: return

      field = value
      initialize(value)
    }

  private val buttonLook = object : ActionButtonLook() {
    override fun paintBorder(g: Graphics, c: JComponent, state: Int) {
    }

    override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
      painter.paintActionButtonBackground(g, component, state)
    }
  }

  private var bar: ActionToolbarImpl? = null

  private fun initialize(group: ActionGroup) {
    val tb = object : ActionToolbarImpl(ActionPlaces.NAVIGATION_BAR_TOOLBAR, group, true) {
      private var isActive = false

      override fun createCustomComponent(action: CustomComponentAction, presentation: Presentation): JComponent {
        if (!isActive) {
          return super.createCustomComponent(action, presentation)
        }

        var customComponent = super.createCustomComponent(action, presentation)
        if (action is ComboBoxAction) {
          customComponent = UIUtil.findComponentOfType(customComponent, ComboBoxAction.ComboBoxButton::class.java)
        }
        customComponent.border = JBUI.Borders.empty()
        return customComponent
      }

      override fun createToolbarButton(action: AnAction,
                                       look: ActionButtonLook?,
                                       place: String,
                                       presentation: Presentation,
                                       minimumSize: Dimension): ActionButton {
        if (!isActive) {
          return super.createToolbarButton(action, look, place, presentation, minimumSize)

        }

        val createToolbarButton = super.createToolbarButton(action, buttonLook, place, presentation, minimumSize)
        createToolbarButton.border = JBUI.Borders.empty(0, 3)
        return createToolbarButton
      }

      override fun fillToolBar(actions: MutableList<out AnAction>, layoutSecondaries: Boolean) {
        if (!isActive) {
          super.fillToolBar(actions, layoutSecondaries)
          return
        }

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

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        painter.paintActionBarBackground(this, g)
      }

      override fun paintBorder(g: Graphics) {
        painter.paintActionBarBorder(this, g)
      }

      override fun paint(g: Graphics) {
        super.paint(g)
        painter.paintActionBarBorder(this, g)
      }

      private fun addMetadata(component: JComponent, index: Int, count: Int) {
        if (count == 1) {
          component.putClientProperty(CONTROL_BAR_PROPERTY, CONTROL_BAR_SINGLE)
          return
        }

        val property = when (index) {
          0 -> CONTROL_BAR_FIRST
          count - 1 -> CONTROL_BAR_LAST
          else -> CONTROL_BAR_MIDDLE
        }
        component.putClientProperty(CONTROL_BAR_PROPERTY, property)
      }

      override fun actionsUpdated(forced: Boolean, newVisibleActions: MutableList<out AnAction>) {
        val filtered = newVisibleActions.filter { isSuitableAction(it) }
        isActive = filtered.size > 1
        super.actionsUpdated(forced, if (isActive) filtered else newVisibleActions)
      }
    }.apply {
      component.isOpaque = false
      setHideDisabled(true)
    }
    bar = tb
  }


  private fun isSuitableAction(it: AnAction): Boolean {
    return it !is Separator || (it is ComboBoxAction) && (it !is CustomComponentAction) || (it is BarCustomComponentAction)
  }

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = actionGroup != null
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val component = bar?.component ?: JPanel()
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