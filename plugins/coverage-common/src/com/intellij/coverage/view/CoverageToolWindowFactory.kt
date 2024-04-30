// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageBundle
import com.intellij.coverage.actions.ExternalReportImportManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

class CoverageToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val manager = toolWindow.contentManager
    manager.addContentManagerListener(object : ContentManagerListener {
      override fun contentRemoved(event: ContentManagerEvent) {
        if (manager.isEmpty) {
          val toolWindowManager = ToolWindowManager.getInstance(project) as? ToolWindowManagerImpl ?: return
          toolWindowManager.hideToolWindow(toolWindow.getId(), removeFromStripe = false)
        }
      }
    })
    val toolWindowEx = toolWindow as? ToolWindowEx ?: return
    toolWindowEx.emptyText
      ?.appendLine(CoverageBundle.message("coverage.import.report.toolwindow.empty.text"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        ExternalReportImportManager.getInstance(toolWindow.project).chooseAndOpenSuites(ExternalReportImportManager.Source.EMPTY_TOOLWINDOW)
      }
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.apply {
      stripeTitle = CoverageBundle.message("coverage.view.title")
      helpId = CoverageView.HELP_ID
    }
  }
}
