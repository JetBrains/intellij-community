// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.window

import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.projectView.window.ProjectViewToolWindowService
import com.intellij.ui.content.ContentFactory
import javax.swing.JLabel

internal class ProjectViewToolWindowServiceImpl : ProjectViewToolWindowService {
  override fun setupToolWindow(toolWindow: ToolWindow) {
    val label = JLabel("Not implemented")
    val content = ContentFactory.getInstance().createContent(
      /* component = */ label,
      /* displayName = */ "TODO",
      /* isLockable = */ false
    )
    toolWindow.contentManager.addContent(content)
  }
}
