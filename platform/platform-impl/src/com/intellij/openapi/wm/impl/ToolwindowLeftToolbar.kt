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
  private val topPane = JPanel(VerticalFlowLayout(0, 0))
  private val bottomPane = JPanel(VerticalFlowLayout(0, 0))
  lateinit var moreButton: MoreSquareStripeButton

  init {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 1)
    moreButton = MoreSquareStripeButton(this)
    topPane.background = JBUI.CurrentTheme.ToolWindow.background()
    bottomPane.background = JBUI.CurrentTheme.ToolWindow.background()

    val topWrapper = JPanel(BorderLayout())
    topWrapper.add(topPane, BorderLayout.NORTH)
    topWrapper.add(moreButton, BorderLayout.CENTER)
    add(topWrapper, BorderLayout.NORTH)
    add(bottomPane, BorderLayout.SOUTH)
  }

  override fun removeStripeButton(project: Project, toolWindow: ToolWindow, anchor: ToolWindowAnchor) {
    when (anchor) {
      ToolWindowAnchor.LEFT -> remove(topPane, toolWindow)
      ToolWindowAnchor.BOTTOM -> remove(bottomPane, toolWindow)
    }
  }

  override fun addStripeButton(project: Project, anchor: ToolWindowAnchor, toolWindow: ToolWindow) {
    when (anchor) {
      ToolWindowAnchor.LEFT -> rebuildStripe(project, topPane, toolWindow)
      ToolWindowAnchor.BOTTOM -> rebuildStripe(project, bottomPane, toolWindow, addToBeginning = true)
    }
  }

  override fun reset() {
    topPane.removeAll()
    topPane.revalidate()
    bottomPane.removeAll()
    bottomPane.revalidate()
  }

  override fun getButtonFor(toolWindowId: String): SquareStripeButton? {
    topPane.components.filterIsInstance(SquareStripeButton::class.java).find {it.button.id == toolWindowId}?.let { return it }
    return bottomPane.components.filterIsInstance(SquareStripeButton::class.java).find {it.button.id == toolWindowId}
  }
}