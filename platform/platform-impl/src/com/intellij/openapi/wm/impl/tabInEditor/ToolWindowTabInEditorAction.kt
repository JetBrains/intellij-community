// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager

private val COLLECTOR = KeyedExtensionCollector<ToolWindowTabInEditorHelper, String>("com.intellij.toolWindowTabInEditorHelper")
private val DEFAULT_HELPER = ToolWindowTabInEditorDefaultHelper()

internal class ToolWindowTabInEditorAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    if (!Registry.`is`("toolwindow.open.tab.in.editor")) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    delegateToHelper(e) { toolWindowTabInEditorHelper, toolWindow, tabEditorFile ->
      toolWindowTabInEditorHelper.updatePresentation(e, toolWindow, tabEditorFile)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    delegateToHelper(e) { toolWindowTabInEditorHelper, toolWindow, tabEditorFile ->
      toolWindowTabInEditorHelper.performAction(e, toolWindow, tabEditorFile)
    }
  }
}

private fun delegateToHelper(
  e: AnActionEvent,
  helperDelegate: (ToolWindowTabInEditorHelper, ToolWindow, ToolWindowTabFile?) -> Unit
) {
  val project = e.project
  if (project == null) return

  var toolWindow: ToolWindow? = null
  var tabEditorFile: ToolWindowTabFile? = null
  var id: String? = null

  val vFile = e.getData(PlatformDataKeys.FILE_EDITOR)?.file
  if (vFile is ToolWindowTabFile) {
    tabEditorFile = vFile
    id = vFile.toolWindowId
    toolWindow = ToolWindowManager.getInstance(project).getToolWindow(id)
  }
  else {
    toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    id = toolWindow?.id
  }
  if (id != null && toolWindow != null) {
    val editorHelper = COLLECTOR.forKey(id).firstOrNull() ?: DEFAULT_HELPER
    helperDelegate(editorHelper, toolWindow, tabEditorFile)
  }
}