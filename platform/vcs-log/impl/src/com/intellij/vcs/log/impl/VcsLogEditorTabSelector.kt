// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.TabbedContent
import com.intellij.vcs.log.ui.VcsLogUiImpl
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

open class VcsLogEditorTabSelector(private val project: Project) : ContentManagerAdapter(), PropertyChangeListener {

  override fun selectionChanged(event: ContentManagerEvent) {
    if (ContentManagerEvent.ContentOperation.add == event.operation) {
      val content = event.content
      selectEditorTab(content)
    }
  }

  protected open fun selectEditorTab(content: Content) {
    val ui = VcsLogContentUtil.getLogUi(content.component)
    if (ui is VcsLogUiImpl) {
      val frame = ui.mainFrame
      frame.openLogEditorTab()
    }
  }

  override fun contentAdded(event: ContentManagerEvent) {
    val content = event.content
    if (content is TabbedContent) {
      content.addPropertyChangeListener(this)
    }
  }

  override fun contentRemoved(event: ContentManagerEvent) {
    val content = event.content
    if (content is TabbedContent) {
      content.removePropertyChangeListener(this)
    }
  }

  private fun getToolWindow(): ToolWindow? {
    return ToolWindowManager.getInstance(project).getToolWindow(
      ChangesViewContentManager.TOOLWINDOW_ID)
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    if (evt.propertyName == Content.PROP_COMPONENT) {

      val toolWindow = getToolWindow()
      if (toolWindow != null && toolWindow.isVisible) {
        val content = toolWindow.contentManager.selectedContent
        if (content != null) {
          selectEditorTab(content)
        }
      }
    }
  }
}