// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorOpenRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

suspend fun openChat(
  project: Project,
  projectPath: String,
  threadIdentity: String,
  shellCommand: List<String>,
  threadId: String,
  threadTitle: String,
  subAgentId: String?,
) {
  val manager = FileEditorManagerEx.getInstanceExAsync(project)
  val existing = findExistingChat(manager.openFiles, threadIdentity, subAgentId)
  val file = existing ?: AgentChatVirtualFile(
    projectPath = projectPath,
    threadIdentity = threadIdentity,
    shellCommand = shellCommand,
    threadId = threadId,
    threadTitle = threadTitle,
    subAgentId = subAgentId,
  )
  manager.openFile(
    file = file,
    options = FileEditorOpenRequest(requestFocus = true, reuseOpen = true),
  )
}

private fun findExistingChat(
  openFiles: Array<VirtualFile>,
  threadIdentity: String,
  subAgentId: String?,
): AgentChatVirtualFile? {
  for (openFile in openFiles) {
    val chatFile = openFile as? AgentChatVirtualFile ?: continue
    if (chatFile.matches(threadIdentity, subAgentId)) {
      return chatFile
    }
  }
  return null
}
