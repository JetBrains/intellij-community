// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.changes.ui.isCommitToolWindowShown
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ChangesViewCommitTabTitleUpdater(private val project: Project, val tabName: String) {
  @RequiresEdt
  fun init(contentDisposable: Disposable) {
    val toolWindow = getToolWindow()
    if (toolWindow != null) {
      val listener = object : ContentManagerListener {
        override fun contentAdded(event: ContentManagerEvent) {
          updateTitle()
        }

        override fun contentRemoved(event: ContentManagerEvent) {
          if (event.content.tabName == tabName) return
          updateTitle()
        }
      }
      toolWindow.contentManager.addContentManagerListener(listener)
      Disposer.register(contentDisposable) { toolWindow.contentManager.removeContentManagerListener(listener) }
    }

    project.messageBus.connect(contentDisposable).subscribe(ChangesViewContentManagerListener.Companion.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() {
        updateTitle()
      }
    })
  }

  private fun updateTitle() {
    val toolWindow = getToolWindow()?.takeIf { it.isAvailable } ?: return
    val contentManager = toolWindow.contentManager
    val tabContent = contentManager.contents.find { it.tabName == tabName } ?: return

    tabContent.displayName = when {
      contentManager.contentCount == 1 -> null
      project.isCommitToolWindowShown -> VcsBundle.message("tab.title.commit")
      else -> VcsBundle.message("local.changes.tab")
    }
  }

  private fun getToolWindow(): ToolWindow? = ChangesViewContentManager.getToolWindowFor(project, tabName)
}