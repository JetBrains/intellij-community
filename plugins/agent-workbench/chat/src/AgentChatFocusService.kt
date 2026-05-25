// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class AgentChatFocusService(private val project: Project) {
  fun focusRecentOrFirstChatTab(projectPath: String): Boolean {
    val manager = FileEditorManagerEx.getInstanceEx(project)
    val chatFile = recentOrFirstAgentChatFile(
      projectPath = projectPath,
      historyFiles = EditorHistoryManager.getInstance(project).fileList,
      openFiles = manager.openFiles,
    ) ?: return false

    manager.openFile(chatFile, true, true)
    return true
  }
}

internal fun recentOrFirstAgentChatFile(
  projectPath: String,
  historyFiles: List<VirtualFile>,
  openFiles: Array<VirtualFile>,
): AgentChatVirtualFile? {
  val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath).takeIf { path -> path.isNotBlank() } ?: return null
  val openMatchingFiles = openFiles.asSequence()
    .filterIsInstance<AgentChatVirtualFile>()
    .filter { file -> file.isAgentChatForProject(normalizedProjectPath) }
    .toList()
  if (openMatchingFiles.isEmpty()) return null

  val openMatchingFilesByTabKey = openMatchingFiles.associateBy { file -> file.tabKey }
  historyFiles.asReversed().forEach { file ->
    val historyChatFile = file as? AgentChatVirtualFile ?: return@forEach
    if (historyChatFile.isAgentChatForProject(normalizedProjectPath)) {
      openMatchingFilesByTabKey[historyChatFile.tabKey]?.let { openFile -> return openFile }
    }
  }
  return openMatchingFiles.first()
}

private fun AgentChatVirtualFile.isAgentChatForProject(normalizedProjectPath: String): Boolean {
  return normalizeAgentWorkbenchPath(projectPath) == normalizedProjectPath
}
