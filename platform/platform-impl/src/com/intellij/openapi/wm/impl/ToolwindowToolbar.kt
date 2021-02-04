// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import java.awt.BorderLayout
import javax.swing.JPanel

abstract class ToolwindowToolbar : JPanel() {
  lateinit var toolwindowPane: ToolWindowsPane
  lateinit var defaults: List<String>

  init {
    layout = BorderLayout()
    isOpaque = true
  }

  abstract fun updateButtons()

  abstract fun getButtonFor(toolWindowId: String): SquareStripeButton?

  abstract fun removeStripeButton(project: Project, toolWindow: ToolWindow, anchor: ToolWindowAnchor)

  abstract fun addStripeButton(project: Project, anchor: ToolWindowAnchor, comparator: Comparator<ToolWindow>, toolWindow: ToolWindow)

  companion object {
    fun rebuildStripe(project: Project, toolwindowPane: ToolWindowsPane, panel: JPanel,
                      toolWindow: ToolWindow, comparator: Comparator<ToolWindow>) {
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
  }
}