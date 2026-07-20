// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
@ApiStatus.Internal
interface ToolWindowEditorTabSupport {
  fun getEditorTabDescriptor(toolWindow: ToolWindow, content: Content): ToolWindowEditorTabDescriptor?

  @RequiresEdt
  fun updateEditorTabPresentation(project: Project, toolWindow: ToolWindow, content: Content) {
    val descriptor = getEditorTabDescriptor(toolWindow, content) ?: return
    project.service<ToolWindowEditorTabTransferController>().updateEditorTabPresentation(toolWindow, content, descriptor)
  }

  @RequiresEdt
  fun updateAllEditorTabsPresentation(project: Project, toolWindow: ToolWindow) {
    FileEditorManager.getInstance(project).openFiles
      .filterIsInstance<ToolWindowEditorTabFile>()
      .filter { file -> file.toolWindowId == toolWindow.id }
      .forEach { file ->
        val content = file.content
        updateEditorTabPresentation(project, toolWindow, content)
      }
  }

  fun canCloseFile(project: Project, content: Content): Boolean = true
}

@ApiStatus.Experimental
@ApiStatus.Internal
data class ToolWindowEditorTabDescriptor(
  val title: @NlsContexts.TabTitle String,
  val icon: Icon? = null,
  val persistInEditorHistory: Boolean = false,
)
