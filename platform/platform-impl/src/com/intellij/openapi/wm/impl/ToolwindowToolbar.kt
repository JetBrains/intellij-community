// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.ActionPlaces.TOOLWINDOW_TOOLBAR_BAR
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.JBUI
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

  fun addStripeButton(toolWindow: ToolWindowImpl) {
    rebuildStripe(getStripeFor(toolWindow.windowInfo.largeStripeAnchor), toolWindow)
  }

  abstract fun reset()

  fun startDrag() {
    revalidate()
    repaint()
  }

  fun stopDrag() = startDrag()

  private fun rebuildStripe(panel: AbstractDroppableStripe, toolWindow: ToolWindowImpl) {
    // temporary add new button
    if (panel.buttons.asSequence().filterIsInstance(SquareStripeButton::class.java).find { it.button.id == toolWindow.id } == null) {
      panel.add(SquareStripeButton(toolWindow.project, StripeButton(toolWindow).also(StripeButton::updatePresentation)))
    }

    val sortedSquareButtons = panel.components.asSequence()
      .filterIsInstance(SquareStripeButton::class.java)
      .map { it.button.toolWindow }
      .sortedWith(Comparator.comparingInt<ToolWindow> { (it as? ToolWindowImpl)?.windowInfo?.orderOnLargeStripe ?: -1 })
      .toList()
    panel.removeAll()
    panel.buttons.clear()
    sortedSquareButtons.forEach {
      val button = SquareStripeButton(toolWindow.project, StripeButton(it).also(StripeButton::updatePresentation))
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
      ComponentUtil.findComponentsOfType(panel, SquareStripeButton::class.java).forEach { it.update() }
      panel.revalidate()
      panel.repaint()
    }

    fun remove(panel: AbstractDroppableStripe, toolWindow: ToolWindow) {
      val component = panel.components.firstOrNull { it is SquareStripeButton && it.button.id == toolWindow.id } ?: return
      panel.remove(component)
      panel.buttons.remove(component)
      panel.revalidate()
      panel.repaint()
    }
  }

  open class ToolwindowActionToolbar(val panel: JComponent) : ActionToolbarImpl(TOOLWINDOW_TOOLBAR_BAR, DefaultActionGroup(), false) {
    override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) = updateButtons(panel)
  }
}