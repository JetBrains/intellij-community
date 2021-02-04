// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

class ToolwindowLeftToolbar : ToolwindowToolbar() {
  private val topPane: JPanel
  private val bottomPane: JPanel
  val moreButton: MoreSquareStripeButton

  init {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 1)

    topPane = JPanel(VerticalFlowLayout(0, 0))
    bottomPane = JPanel(VerticalFlowLayout(0, 0))
    moreButton = MoreSquareStripeButton(this)

    val topWrapper = JPanel(BorderLayout())
    topWrapper.add(topPane, BorderLayout.NORTH)
    topWrapper.add(moreButton, BorderLayout.CENTER)
    add(topWrapper, BorderLayout.NORTH)
    add(bottomPane, BorderLayout.SOUTH)
  }

  override fun removeStripeButton(project: Project, toolWindow: ToolWindow, anchor: ToolWindowAnchor) {
    when (anchor) {
      ToolWindowAnchor.LEFT -> {
        topPane.components.find { (it as SquareStripeButton).button.id == toolWindow.id }?.let { topPane.remove(it) }
      }
      ToolWindowAnchor.BOTTOM -> {
        bottomPane.components.find { (it as SquareStripeButton).button.id == toolWindow.id }?.let { bottomPane.remove(it) }
      }
      else -> {
      }
    }
  }

  override fun addStripeButton(project: Project, anchor: ToolWindowAnchor, comparator: Comparator<ToolWindow>, toolWindow: ToolWindow) =
    when (anchor) {
      ToolWindowAnchor.LEFT -> {
        rebuildStripe(project, toolwindowPane, topPane, toolWindow, comparator)
      }
      ToolWindowAnchor.BOTTOM -> {
        rebuildStripe(project, toolwindowPane, bottomPane, toolWindow, comparator)
      }
      else -> {
      }
    }

  override fun updateButtons() {
    topPane.components.filterIsInstance(SquareStripeButton::class.java).forEach { it.update() }
    bottomPane.components.filterIsInstance(SquareStripeButton::class.java).forEach { it.update() }
    moreButton.update()
  }

  override fun getButtonFor(toolWindowId: String): SquareStripeButton? {
    topPane.components.filterIsInstance(SquareStripeButton::class.java).find {it.button.id === toolWindowId}?.let { return it }
    return bottomPane.components.filterIsInstance(SquareStripeButton::class.java).find {it.button.id === toolWindowId}
  }
}