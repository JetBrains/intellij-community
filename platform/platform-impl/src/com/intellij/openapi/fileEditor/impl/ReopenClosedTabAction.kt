// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ComponentUtil

internal class ReopenClosedTabAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val window = getEditorWindow(e)
    if (window == null) {
      val project = e.project ?: return
      val lastFile = EditorHistoryManager.getInstance(project).fileList.lastOrNull() ?: return
      FileEditorManager.getInstance(project).requestOpenFile(lastFile)
    }
    else {
      window.restoreClosedTab()
    }
  }

  override fun update(e: AnActionEvent) {
    getEditorWindow(e)?.let {
      e.presentation.isEnabledAndVisible = it.hasClosedTabs()
      return
    }

    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && !EditorHistoryManager.getInstance(project).fileList.isEmpty()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private fun getEditorWindow(e: AnActionEvent): EditorWindow? {
  val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: return null
  return ComponentUtil.getParentOfType(EditorsSplitters::class.java, component)?.currentWindow
}
