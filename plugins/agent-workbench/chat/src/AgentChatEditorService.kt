// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private class AgentChatEditorServiceLog

private val LOG = logger<AgentChatEditorServiceLog>()

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
  LOG.debug {
    "openChat(project=${project.name}, path=$projectPath, identity=$threadIdentity, subAgentId=$subAgentId, existing=${existing != null}, title=$threadTitle)"
  }
  val fileSystem = AgentChatVirtualFileSystems.getInstanceOrFallback()
  val file = existing ?: fileSystem.getOrCreateFile(
    descriptor = AgentChatFileDescriptor(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
      shellCommand = shellCommand,
    ),
  )
  if (existing != null) {
    existing.updateCommandAndThreadId(shellCommand = shellCommand, threadId = threadId)
    val updated = existing.updateThreadTitle(threadTitle)
    LOG.debug {
      "openChat existing tab update(identity=$threadIdentity, subAgentId=$subAgentId): updated=$updated, currentName=${existing.name}, currentTitle=${existing.threadTitle}"
    }
    if (updated) {
      manager.updateFilePresentation(existing)
    }
  }
  else {
    LOG.debug {
      "openChat created new tab(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name})"
    }
  }
  manager.openFile(
    file = file,
    options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true),
  )
  LOG.debug {
    "openChat openFile completed(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name})"
  }
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
