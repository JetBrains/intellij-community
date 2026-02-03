// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.openapi.project.Project
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.xNext.toolbar.actions.statusBar.XNextBar
import java.awt.BorderLayout
import javax.swing.JComponent

internal class XNextToolWindowButtonManager(paneId: String, isPrimary: Boolean) :
  ToolWindowPaneNewButtonManager(paneId, isPrimary) {

  constructor(paneId: String) : this(paneId, true)

  private val xNextBar = XNextBar()

  override fun initMoreButton(project: Project) {
    super.initMoreButton(project)
    xNextBar.init(project)
  }

  override fun wrapWithControls(pane: ToolWindowPane): JComponent {
    return super.wrapWithControls(pane).apply {
      add(xNextBar, BorderLayout.SOUTH)
    }
  }
}