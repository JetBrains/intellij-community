// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.platform.problemsView.shared.ToolWindowLazyContent
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ToolWindowContentPostProcessor

internal class ProblemsViewToolWindowContentReplacer : ToolWindowContentPostProcessor {
  override fun isEnabled(project: Project, content: Content, toolWindow: ToolWindow): Boolean {
    if (toolWindow.id != ProblemsView.ID) {
      return false
    }

    val extension = getMatchingExtension(content)
    val available = extension?.isAvailable(project) ?: false

    return available
  }

  override fun postprocessContent(project: Project, content: Content, toolWindow: ToolWindow) {
    val extensionMatchingTab = getMatchingExtension(content) ?: return
    extensionMatchingTab.initTabContent(project, content)
    addSecondaryActions(toolWindow)
    ToolWindowLazyContent.installInitializer(toolWindow)
  }

  private fun addSecondaryActions(toolWindow: ToolWindow) {
    if (toolWindow is ToolWindowEx) {
      val group = ActionManager.getInstance().getAction("ProblemsView.Frontend.SecondaryActions") as? ActionGroup
      toolWindow.setAdditionalGearActions(group)
    }
  }

  private fun getMatchingExtension(content: Content): FrontendProblemsViewContentProvider? {
    return FrontendProblemsViewContentProvider.EP_NAME.extensionList
        .firstOrNull { ext -> ext.matchesTabName(content.tabName)}
  }
}