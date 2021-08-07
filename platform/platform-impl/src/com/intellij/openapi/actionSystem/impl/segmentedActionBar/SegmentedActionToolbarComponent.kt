// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.Border

open class SegmentedActionToolbarComponent(place: String, group: ActionGroup, val paintBorderForSingleItem: Boolean = true) : ActionToolbarImpl(place, group, true) {
  companion object {
    internal const val CONTROL_BAR_PROPERTY = "CONTROL_BAR_PROPERTY"
    internal const val CONTROL_BAR_FIRST = "CONTROL_BAR_PROPERTY_FIRST"
    internal const val CONTROL_BAR_LAST = "CONTROL_BAR_PROPERTY_LAST"
    internal const val CONTROL_BAR_MIDDLE = "CONTROL_BAR_PROPERTY_MIDDLE"
    internal const val CONTROL_BAR_SINGLE = "CONTROL_BAR_PROPERTY_SINGLE"

    const val RUN_TOOLBAR_COMPONENT_ACTION = "RUN_TOOLBAR_COMPONENT_ACTION"

    internal val painter = SegmentedBarPainter()

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

  init {
    layoutPolicy = NOWRAP_LAYOUT_POLICY
  }

  private var isActive = false

  private val segmentedButtonLook = object : ActionButtonLook() {
    override fun paintBorder(g: Graphics, c: JComponent, state: Int) {
    }

    override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
      painter.paintActionButtonBackground(g, component, state)
    }
  }

  override fun getInsets(): Insets {
    return JBUI.emptyInsets()
  }

  override fun setBorder(border: Border?) {

  }

  override fun isInsideNavBar(): Boolean {
    return true
  }

  override fun createCustomComponent(action: CustomComponentAction, presentation: Presentation): JComponent {
    if (!isActive) {
      return super.createCustomComponent(action, presentation)
    }

    var component = super.createCustomComponent(action, presentation)

    if (action is ComboBoxAction && component is JPanel && component.getComponentCount() == 1) {
      val child = component.getComponent(0) as? JComponent
      if (child != null) {
        component.remove(child)
        component = child
      }
    }

    component.border = JBUI.Borders.empty()

    return component
  }

  override fun createToolbarButton(action: AnAction,
                                   look: ActionButtonLook?,
                                   place: String,
                                   presentation: Presentation,
                                   minimumSize: Dimension): ActionButton {
    if (!isActive) {
      return super.createToolbarButton(action, look, place, presentation, minimumSize)

    }

    val createToolbarButton = super.createToolbarButton(action, segmentedButtonLook, place, presentation, minimumSize)
    createToolbarButton.border = JBUI.Borders.empty(0, 3)
    createToolbarButton.setLook(segmentedButtonLook)

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

        component.putClientProperty(RUN_TOOLBAR_COMPONENT_ACTION, action)
      }
      else {
        val component = createToolbarButton(action)
        addMetadata(component, i, actions.size)
        add(ACTION_BUTTON_CONSTRAINT, component)

        component.putClientProperty(RUN_TOOLBAR_COMPONENT_ACTION, action)
      }
    }
  }

  private fun isSuitableAction(it: AnAction): Boolean {
    return it !is Separator || (it is ComboBoxAction) && (it !is CustomComponentAction) || (it is BarCustomComponentAction)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    painter.paintActionBarBackground(this, g)
  }

  override fun paintBorder(g: Graphics) {
    if(isActive || paintBorderForSingleItem) {
      painter.paintActionBarBorder(this, g)
    }
  }

  override fun paint(g: Graphics) {
    super.paint(g)
    if(isActive || paintBorderForSingleItem) {
      painter.paintActionBarBorder(this, g)
    }
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

  override fun calculateBounds(size2Fit: Dimension, bounds: MutableList<Rectangle>) {
    bounds.clear()
    for (i in 0 until componentCount) {
      bounds.add(Rectangle())
    }

    var offset = 0
    for (i in 0 until componentCount) {
      val d = getChildPreferredSize(i)
      val r = bounds[i]
      r.setBounds(insets.left + offset, insets.top, d.width, DEFAULT_MINIMUM_BUTTON_SIZE.height)
      offset += d.width
    }
  }
}