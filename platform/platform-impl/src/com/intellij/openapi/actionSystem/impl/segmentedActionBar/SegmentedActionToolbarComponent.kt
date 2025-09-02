// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionToolbar.ACTION_BUTTON_CONSTRAINT
import com.intellij.openapi.actionSystem.ActionToolbar.CUSTOM_COMPONENT_CONSTRAINT
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction.ComboBoxButton
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.JComponent
import javax.swing.border.Border

@ApiStatus.Internal
open class SegmentedActionToolbarComponent(
  place: String,
  group: ActionGroup,
  private val paintBorderForSingleItem: Boolean = true
) : ActionToolbarImpl(place, group, true) {
  companion object {
    internal const val CONTROL_BAR_PROPERTY: String = "CONTROL_BAR_PROPERTY"
    internal const val CONTROL_BAR_FIRST: String = "CONTROL_BAR_PROPERTY_FIRST"
    internal const val CONTROL_BAR_LAST: String = "CONTROL_BAR_PROPERTY_LAST"
    internal const val CONTROL_BAR_MIDDLE: String = "CONTROL_BAR_PROPERTY_MIDDLE"
    internal const val CONTROL_BAR_SINGLE: String = "CONTROL_BAR_PROPERTY_SINGLE"

    const val RUN_TOOLBAR_COMPONENT_ACTION: String = "RUN_TOOLBAR_COMPONENT_ACTION"

    private val LOG = Logger.getInstance(SegmentedActionToolbarComponent::class.java)

    val segmentedButtonLook: ActionButtonLook = object : ActionButtonLook() {
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
    layoutStrategy = MyLayoutStrategy()
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

      val component = getOrCreateActionComponent(action)
      val constraints =
        if (component is ActionButton) ACTION_BUTTON_CONSTRAINT
        else CUSTOM_COMPONENT_CONSTRAINT
      addMetadata(component, i, actions.size)
      add(constraints, component)
      component.putClientProperty(RUN_TOOLBAR_COMPONENT_ACTION, action)
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

  protected open fun logNeeded(): Boolean = false

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

    val actionManager = ActionManager.getInstance()
    val ides = newVisibleActions.map { actionManager.getId(it)!! }
    val filteredIds = filtered.map { actionManager.getId(it)!! }

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
}

private class MyLayoutStrategy: ToolbarLayoutStrategy {
  override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
    val res = mutableListOf<Rectangle>()

    val insets = toolbar.component.insets
    var offset = 0
    for (child in toolbar.component.components) {
      val d = if (child.isVisible) child.preferredSize else Dimension()
      res.add(Rectangle(insets.left + offset, insets.top, d.width, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height))
      offset += d.width
    }

    return res;
  }

  override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
    val width = toolbar.component.components.filter { it.isVisible }.sumOf { it.preferredSize.width }
    return JBUI.size(width, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height)
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension = JBUI.emptySize()
}