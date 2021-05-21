// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.ActionPlaces.TOOLWINDOW_TOOLBAR_BAR
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

abstract class ToolwindowToolbar : JPanel() {
  lateinit var toolwindowPane: ToolWindowsPane
  lateinit var defaults: List<String>

  init {
    layout = BorderLayout()
    isOpaque = true
  }

  abstract fun getButtonFor(toolWindowId: String): SquareStripeButton?

  abstract fun removeStripeButton(project: Project, toolWindow: ToolWindow, anchor: ToolWindowAnchor)

  abstract fun addStripeButton(project: Project, anchor: ToolWindowAnchor, comparator: Comparator<ToolWindow>, toolWindow: ToolWindow)

  fun rebuildStripe(project: Project, panel: JPanel, toolWindow: ToolWindow, comparator: Comparator<ToolWindow>) {
    if (toolWindow !is ToolWindowImpl) return
    //temporary add new button
    panel.add(SquareStripeButton(project, StripeButton(toolwindowPane, toolWindow).also { it.updatePresentation() }))
    val sortedSquareButtons = panel.components.filterIsInstance(SquareStripeButton::class.java)
      .map { it.button.toolWindow }.sortedWith(comparator)
    panel.removeAll()
    sortedSquareButtons.forEach {
      panel.add(SquareStripeButton(project, StripeButton(toolwindowPane, it).also { button -> button.updatePresentation() }))
    }
  }

  companion object {
    fun updateButtons(panel: JComponent) {
      panel.components.filterIsInstance(SquareStripeButton::class.java).forEach { it.update() }
      panel.revalidate()
      panel.repaint()
    }
  }

  open class ToolwindowActionToolbar(val panel: JComponent) : ActionToolbarImpl(TOOLWINDOW_TOOLBAR_BAR, DefaultActionGroup(), false) {
    override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) = updateButtons(panel)
  }
}