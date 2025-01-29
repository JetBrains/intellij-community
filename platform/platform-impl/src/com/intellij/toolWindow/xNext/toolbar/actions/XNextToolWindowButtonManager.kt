// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.xNext.toolbar.XNextBar
import com.intellij.toolWindow.xNext.toolbar.actions.toolbar.XNextActionToolbar
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import javax.swing.JComponent

internal class XNextToolWindowButtonManager(paneId: String, isPrimary: Boolean) :
  ToolWindowPaneNewButtonManager(paneId, isPrimary) {

  constructor(paneId: String) : this(paneId, true)

  private val xNextToolbar = XNextToolWindowToolbar(paneId, isPrimary)

  override fun setupToolWindowPane(pane: JComponent) {
    xNextToolbar.topStripe.bottomAnchorDropAreaComponent = pane
    xNextToolbar.bottomStripe.bottomAnchorDropAreaComponent = pane
    super.setupToolWindowPane(pane)
  }

  override fun wrapWithControls(pane: ToolWindowPane): JComponent {
    return super.wrapWithControls(pane).apply {
      add(XNextBar().apply{
        add(BorderLayoutPanel().apply {
          val toolbar = XNextActionToolbar()
          toolbar.setTargetComponent(this)
          add(toolbar.component.apply {
            toolbar.component.isOpaque = true
          }, BorderLayout.CENTER)
        })

      }, BorderLayout.SOUTH)
    }
  }
}