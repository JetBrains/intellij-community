// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.vcs.impl.frontend.changes.FrontendChangesViewContentProvider
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewDataKeys
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ToolWindowContentPostProcessor

internal class VcsToolWindowContentReplacer : ToolWindowContentPostProcessor {
  override fun isEnabled(project: Project, content: Content, toolWindow: ToolWindow): Boolean {
    if (toolWindow.id != ToolWindowId.COMMIT || toolWindow.id == ToolWindowId.VCS) return false

    return getMatchingExtension(content)?.isAvailable(project) ?: false
  }

  override fun postprocessContent(project: Project, content: Content, toolWindow: ToolWindow) {
    val extensionMatchingTab = getMatchingExtension(content) ?: return
    val listener = LazyContentListener(content.tabName, toolWindow)
    content.putUserData(ChangesViewDataKeys.CONTENT_SUPPLIER) { content -> extensionMatchingTab.initTabContent(project, content) }
    toolWindow.contentManager.addContentManagerListener(listener)
  }

  private class LazyContentListener(private val tabName: String, private val toolWindow: ToolWindow) : ContentManagerListener, ToolWindowManagerListener {
    override fun stateChanged(toolWindowManager: ToolWindowManager) {
      val selectedContent = toolWindow.contentManager.selectedContent
      if (toolWindow.isVisible && tabName == selectedContent?.tabName) {
        ChangesViewDataKeys.initLazyContent(selectedContent)
      }
    }

    override fun selectionChanged(event: ContentManagerEvent) {
      val content = event.content
      if (toolWindow.isVisible && event.operation == ContentManagerEvent.ContentOperation.add && tabName == content.tabName) {
        ChangesViewDataKeys.initLazyContent(content)
      }
    }
  }

  private fun getMatchingExtension(content: Content): FrontendChangesViewContentProvider? =
    FrontendChangesViewContentProvider.EP_NAME.findFirstSafe { ext -> ext.matchesTabName(content.tabName) }
}
