// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.vcs.impl.frontend.changes.FrontendChangesViewContentProvider
import com.intellij.platform.vcs.impl.shared.ui.ToolWindowLazyContent
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ToolWindowContentPostProcessor

internal class VcsToolWindowContentReplacer : ToolWindowContentPostProcessor {
  override fun isEnabled(project: Project, content: Content, toolWindow: ToolWindow): Boolean {
    if (toolWindow.id != ToolWindowId.COMMIT || toolWindow.id == ToolWindowId.VCS) return false

    return getMatchingExtension(content)?.isAvailable(project) ?: false
  }

  override fun postprocessContent(project: Project, content: Content, toolWindow: ToolWindow) {
    val extensionMatchingTab = getMatchingExtension(content) ?: return
    ToolWindowLazyContent.setContentSupplier(content) { content -> extensionMatchingTab.initTabContent(project, content) }
    ToolWindowLazyContent.installInitializer(toolWindow)
  }

  private fun getMatchingExtension(content: Content): FrontendChangesViewContentProvider? =
    FrontendChangesViewContentProvider.EP_NAME.findFirstSafe { ext -> ext.matchesTabName(content.tabName) }
}
