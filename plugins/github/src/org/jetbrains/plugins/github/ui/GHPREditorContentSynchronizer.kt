// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerListener
import com.intellij.vcs.log.impl.VcsLogEditorTabSelector
import org.jetbrains.plugins.github.pullrequest.GHPRAccountsComponent
import org.jetbrains.plugins.github.pullrequest.GHPRComponentFactory

internal class GHPREditorContentSynchronizer {
  companion object {
    fun getInstance(project: Project): GHPREditorContentSynchronizer = project.service()
  }

  private fun addContentManagerListener(window: ToolWindow, listener: ContentManagerListener) {
    window.contentManager.addContentManagerListener(listener)
    Disposer.register(window.contentManager, Disposable {
      if (!window.isDisposed) {
        window.contentManager.removeContentManagerListener(listener)
      }
    })
  }

  internal class MyToolwindowListener(private val project: Project) : ToolWindowManagerListener {
    override fun toolWindowRegistered(id: String) {
      if (id != ChangesViewContentManager.TOOLWINDOW_ID || !Registry.`is`("show.log.as.editor.tab")) {
        return
      }

      val toolwindow = ToolWindowManager.getInstance(project).getToolWindow(id) ?: return
      getInstance(project).addContentManagerListener(toolwindow, MyLogEditorListener(project))
    }
  }

  private class MyLogEditorListener(private val project: Project) : VcsLogEditorTabSelector(project) {
    override fun selectEditorTab(content: Content) {
      val component = content.component as? GHPRAccountsComponent
      if (component != null) {
        project.service<GHPRComponentFactory>().tryOpenGHPREditorTab()
      }
    }
  }
}