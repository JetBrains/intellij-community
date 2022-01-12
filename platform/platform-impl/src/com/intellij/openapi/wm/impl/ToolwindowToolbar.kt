// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.ActionPlaces.TOOLWINDOW_TOOLBAR_BAR
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel

internal abstract class ToolwindowToolbar : JPanel() {
  lateinit var defaults: List<String>

  init {
    layout = BorderLayout()
    isOpaque = true
    background = JBUI.CurrentTheme.ToolWindow.background()
  }

  abstract fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe

  abstract fun getButtonFor(toolWindowId: String): SquareStripeButton?

  abstract fun getStripeFor(screenPoint: Point): AbstractDroppableStripe?

  fun removeStripeButton(toolWindow: ToolWindow, anchor: ToolWindowAnchor) {
    remove(getStripeFor(anchor), toolWindow)
  }

  fun addStripeButton(project: Project, anchor: ToolWindowAnchor, toolWindow: ToolWindow) {
    rebuildStripe(project, getStripeFor(anchor), toolWindow)
  }

  abstract fun reset()

  fun startDrag() {
    revalidate()
    repaint()
  }

  fun stopDrag() = startDrag()

  fun rebuildStripe(project: Project, panel: AbstractDroppableStripe, toolWindow: ToolWindow) {
    if (toolWindow !is ToolWindowImpl) {
      return
    }

    if (toolWindow.orderOnLargeStripe == -1) {
      toolWindow.orderOnLargeStripe = panel.components.filterIsInstance(SquareStripeButton::class.java).count()
    }

    // temporary add new button
    if (panel.buttons.filterIsInstance(SquareStripeButton::class.java).find { it.button.id == toolWindow.id } == null) {
      panel.add(SquareStripeButton(project, StripeButton(toolWindow).also(StripeButton::updatePresentation)))
    }

    val sortedSquareButtons = panel.components.asSequence()
      .filterIsInstance(SquareStripeButton::class.java)
      .map { it.button.toolWindow }
      .sortedWith(Comparator.comparingInt<ToolWindow> { (it as? ToolWindowImpl)?.windowInfo?.orderOnLargeStripe ?: -1 })
      .toList()
    panel.removeAll()
    panel.buttons.clear()
    sortedSquareButtons.forEach {
      val button = SquareStripeButton(project, StripeButton(it).also(StripeButton::updatePresentation))
      panel.add(button)
      panel.buttons.add(button)
    }
  }

  protected fun tryDroppingOnGap(data: LayoutData, gap: Int, dropRectangle: Rectangle, doLayout: () -> Unit) {
    val sideDistance = data.eachY + gap - dropRectangle.y + dropRectangle.height

    if (sideDistance > 0) {
      data.dragInsertPosition = -1
      data.dragToSide = false
      data.dragTargetChosen = true
      doLayout()
    }
  }

  companion object {
    fun updateButtons(panel: JComponent) {
      UIUtil.findComponentsOfType(panel, SquareStripeButton::class.java).forEach { it.update() }
      panel.revalidate()
      panel.repaint()
    }

    fun remove(panel: AbstractDroppableStripe, toolWindow: ToolWindow) {
      val components = panel.components
      val index = components.filterIsInstance(SquareStripeButton::class.java).indexOfFirst { it.button.id == toolWindow.id }
      // shift all button indexes beneath
      components.drop(index + 1)
        .filterIsInstance(SquareStripeButton::class.java)
        .map { it.button.toolWindow }
        .forEach { it.orderOnLargeStripe-- }

      components[index]?.let {
        panel.remove(it)
        panel.buttons.remove(it)
        panel.revalidate()
        panel.repaint()
      }
    }
  }

  open class ToolwindowActionToolbar(val panel: JComponent) : ActionToolbarImpl(TOOLWINDOW_TOOLBAR_BAR, DefaultActionGroup(), false) {
    override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) = updateButtons(panel)
  }
}