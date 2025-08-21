// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.jetbrains.idea.devkit.threadingModelHelper.ui.LockReqsToolWindow
import org.jetbrains.jewel.bridge.JewelComposePanel

class LockReqsToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.getInstance()
    val panel = JewelComposePanel { LockReqsToolWindow(project) }
    val content = contentFactory.createContent(panel, null, false)
    toolWindow.contentManager.addContent(content)
  }
}

