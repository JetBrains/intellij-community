// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ComponentUtil


internal class ReopenClosedTabAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun update(e: AnActionEvent) {
    val canReopenTab = isReopenAvailable(e)
    e.presentation.isEnabledAndVisible = canReopenTab
  }

  override fun actionPerformed(e: AnActionEvent) {
    val contextWindow = getContextEditorWindow(e)
    if (contextWindow != null) {
      contextWindow.restoreClosedTab()
      return
    }
    val project = e.project ?: return
    val currentWindow = getCurrentWindow(project)
    if (currentWindow != null && currentWindow.hasClosedTabs()) {
      currentWindow.restoreClosedTab()
      return
    }
    val lastHistoryFile = getLastHistoryFile(project) ?: return
    FileEditorManager.getInstance(project).requestOpenFile(lastHistoryFile)
  }

  private fun isReopenAvailable(e: AnActionEvent): Boolean {
    val contextWindow = getContextEditorWindow(e)
    if (contextWindow != null) {
      return contextWindow.hasClosedTabs()
    }
    val project = e.project
    if (project == null) {
      return false
    }
    val currentWindow = getCurrentWindow(project)
    if (currentWindow != null && currentWindow.hasClosedTabs()) {
      return true
    }
    val lastHistoryFile = getLastHistoryFile(project)
    return lastHistoryFile != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

private fun getContextEditorWindow(e: AnActionEvent): EditorWindow? {
  val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: return null
  return ComponentUtil.getParentOfType(EditorsSplitters::class.java, component)?.currentWindow
}

private fun getCurrentWindow(project: Project): EditorWindow? {
  return FileEditorManagerEx.getInstanceExIfCreated(project)?.currentWindow
}

private fun getLastHistoryFile(project: Project): VirtualFile? {
  return EditorHistoryManager.getInstance(project).fileList.lastOrNull()
}
