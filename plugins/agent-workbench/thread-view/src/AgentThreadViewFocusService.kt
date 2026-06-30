// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class AgentThreadViewFocusService(private val project: Project) {
  fun focusRecentOrFirstThreadViewTab(projectPath: String): Boolean {
    val manager = FileEditorManagerEx.getInstanceEx(project)
    val threadViewFile = recentOrFirstAgentThreadViewFile(
      projectPath = projectPath,
      historyFiles = EditorHistoryManager.getInstance(project).fileList,
      openFiles = manager.openFiles,
    ) ?: return false

    manager.openFile(threadViewFile, true, true)
    return true
  }
}

internal fun recentOrFirstAgentThreadViewFile(
  projectPath: String,
  historyFiles: List<VirtualFile>,
  openFiles: Array<VirtualFile>,
): AgentThreadViewVirtualFile? {
  val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath).takeIf { path -> path.isNotBlank() } ?: return null
  val openMatchingFiles = openFiles.asSequence()
    .filterIsInstance<AgentThreadViewVirtualFile>()
    .filter { file -> file.isAgentThreadViewForProject(normalizedProjectPath) }
    .toList()
  if (openMatchingFiles.isEmpty()) return null

  val openMatchingFilesByTabKey = openMatchingFiles.associateBy { file -> file.tabKey }
  historyFiles.asReversed().forEach { file ->
    val historyThreadViewFile = file as? AgentThreadViewVirtualFile ?: return@forEach
    if (historyThreadViewFile.isAgentThreadViewForProject(normalizedProjectPath)) {
      openMatchingFilesByTabKey[historyThreadViewFile.tabKey]?.let { openFile -> return openFile }
    }
  }
  return openMatchingFiles.first()
}

private fun AgentThreadViewVirtualFile.isAgentThreadViewForProject(normalizedProjectPath: String): Boolean {
  return normalizeAgentWorkbenchPath(projectPath) == normalizedProjectPath
}
