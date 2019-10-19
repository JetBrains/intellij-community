// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.diff.editor.VCSContentVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.TOOLWINDOW_ID
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.content.ContentManagerListener

class VcsToolWindowEditorSynchronizer {

  companion object {
    fun getInstance(project: Project): VcsToolWindowEditorSynchronizer = project.service()
  }

  private fun addContentManagerListener(window: ToolWindowImpl, listener: ContentManagerListener) {
    window.contentManager.addContentManagerListener(listener)
    Disposer.register(window, Disposable {
      if (!window.isDisposed) {
        window.contentManager.removeContentManagerListener(listener)
      }
    })
  }

  private class MyFileEditorListener : FileEditorManagerListener {
    override fun selectionChanged(e: FileEditorManagerEvent) {
      if (!Registry.`is`("show.log.as.editor.tab")) {
        return
      }

      val file = e.newFile
      if (file is VCSContentVirtualFile) {
        val tabSelector = file.getUserData(VCSContentVirtualFile.TabSelector)
        if (tabSelector != null) {
          tabSelector()
        }
      }
    }
  }

  class MyToolwindowListener(private val project: Project) : ToolWindowManagerListener {
    override fun toolWindowRegistered(id: String) {

      if (!Registry.`is`("show.log.as.editor.tab")) return
      if (id != TOOLWINDOW_ID) return

      val toolwindow = ToolWindowManager.getInstance(project).getToolWindow(id) as? ToolWindowImpl
      if (toolwindow != null) {
        getInstance(project).addContentManagerListener(toolwindow, VcsLogEditorTabSelector(project))
      }
    }
  }
}
