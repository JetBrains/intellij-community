// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
@Suppress("unused")
class AgentChatEditorService(private val project: Project) {
  fun openChat(
    projectPath: String,
    threadIdentity: String,
    shellCommand: List<String>,
    threadId: String,
    threadTitle: String,
    subAgentId: String?,
  ) {
    val manager = FileEditorManager.getInstance(project)
    val existing = findExistingChat(manager.openFiles, threadIdentity, subAgentId)
    val file = existing ?: AgentChatVirtualFile(
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      shellCommand = shellCommand,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
    )
    manager.openFile(file, true)
  }

  private fun findExistingChat(
    openFiles: Array<VirtualFile>,
    threadIdentity: String,
    subAgentId: String?,
  ): AgentChatVirtualFile? {
    return openFiles
      .filterIsInstance<AgentChatVirtualFile>()
      .firstOrNull { it.matches(threadIdentity, subAgentId) }
  }
}
