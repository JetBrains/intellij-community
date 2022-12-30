// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction.ComboBoxButton
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.JComponent
import javax.swing.border.Border

open class SegmentedActionToolbarComponent(place: String,
                                           group: ActionGroup,
                                           private val paintBorderForSingleItem: Boolean = true) : ActionToolbarImpl(place, group, true) {
  companion object {
    internal const val CONTROL_BAR_PROPERTY = "CONTROL_BAR_PROPERTY"
    internal const val CONTROL_BAR_FIRST = "CONTROL_BAR_PROPERTY_FIRST"
    internal const val CONTROL_BAR_LAST = "CONTROL_BAR_PROPERTY_LAST"
    internal const val CONTROL_BAR_MIDDLE = "CONTROL_BAR_PROPERTY_MIDDLE"
    internal const val CONTROL_BAR_SINGLE = "CONTROL_BAR_PROPERTY_SINGLE"

    const val RUN_TOOLBAR_COMPONENT_ACTION = "RUN_TOOLBAR_COMPONENT_ACTION"

    private val LOG = Logger.getInstance(SegmentedActionToolbarComponent::class.java)

    val segmentedButtonLook = object : ActionButtonLook() {
      override fun paintBorder(g: Graphics, c: JComponent, state: Int) {
      }

      override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
        SegmentedBarPainter.paintActionButtonBackground(g, component, state)
      }
    }

    fun isCustomBar(component: Component): Boolean {
      if (component !is JComponent) return false
      return component.getClientProperty(CONTROL_BAR_PROPERTY)?.let {
        it != CONTROL_BAR_SINGLE
      } ?: false
    }

    fun paintButtonDecorations(g: Graphics2D, c: JComponent, paint: Paint): Boolean {
      return SegmentedBarPainter.paintButtonDecorations(g, c, paint)
    }
  }

  init {
    layoutPolicy = NOWRAP_LAYOUT_POLICY
    setActionButtonBorder(JBUI.Borders.empty(0, 3))
    setCustomButtonLook(segmentedButtonLook)
  }

  private var isActive = false
  private var visibleActions: List<AnAction>? = null

  override fun getInsets(): Insets {
    return JBInsets.emptyInsets()
  }

  override fun setBorder(border: Border?) {

  }

  override fun createCustomComponent(action: CustomComponentAction, presentation: Presentation): JComponent {
    var component = super.createCustomComponent(action, presentation)
    if (!isActive) {
      return component
    }

    if (action is ComboBoxAction) {
      UIUtil.uiTraverser(component).filter(ComboBoxButton::class.java).firstOrNull()?.let {
        component.remove(it)
        component = it
      }
    }

    if (component !is ActionButton) {
      component.border = JBUI.Borders.empty()
    }

    return component
  }

  override fun fillToolBar(actions: List<AnAction>, layoutSecondaries: Boolean) {
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

  protected open fun isSuitableAction(action: AnAction): Boolean {
    return true
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    paintActiveBorder(g)
  }

  private fun paintActiveBorder(g: Graphics) {
    if ((isActive || paintBorderForSingleItem) && visibleActions != null) {
      SegmentedBarPainter.paintActionBarBorder(this, g)
    }
  }

  override fun paintBorder(g: Graphics) {
    super.paintBorder(g)
    paintActiveBorder(g)
  }

  override fun paint(g: Graphics) {
    super.paint(g)
    paintActiveBorder(g)
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

  protected open fun logNeeded() = false

  protected fun forceUpdate() {
    if (logNeeded()) LOG.info("RunToolbar MAIN SLOT forceUpdate")
    visibleActions?.let {
      update(true, it)

      revalidate()
      repaint()
    }
  }

  override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) {
    visibleActions = newVisibleActions
    update(forced, newVisibleActions)
  }

  private var lastIds: List<String> = emptyList()
  private var lastActions: List<AnAction> = emptyList()

  private fun update(forced: Boolean, newVisibleActions: List<AnAction>) {
    val filtered = newVisibleActions.filter { isSuitableAction(it) }

    val ides = newVisibleActions.map { ActionManager.getInstance().getId(it) }.toList()
    val filteredIds = filtered.map { ActionManager.getInstance().getId(it) }.toList()

    traceState(lastIds, filteredIds, ides)

    isActive = newVisibleActions.size > 1

    super.actionsUpdated(forced, if (filtered.size > 1) filtered else if(lastActions.isEmpty()) newVisibleActions else lastActions)

    lastIds = filteredIds
    lastActions = filtered
    ApplicationManager.getApplication().messageBus.syncPublisher(ToolbarActionsUpdatedListener.TOPIC).actionsUpdated()
  }

  protected open fun traceState(lastIds: List<String>, filteredIds: List<String>, ides: List<String>) {
    // if(logNeeded() && filteredIds != lastIds) LOG.info("MAIN SLOT new filtered: ${filteredIds}} visible: $ides RunToolbar")
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