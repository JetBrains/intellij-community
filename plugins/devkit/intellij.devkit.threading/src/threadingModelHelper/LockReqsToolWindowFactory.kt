// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper

import com.intellij.devkit.threading.threadingModelHelper.ui.LockReqsToolWindowPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

internal class LockReqsToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(LockReqsToolWindowPanel(project, toolWindow.disposable), null, false)
    toolWindow.contentManager.addContent(content)
  }
}
