// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
  val metadataStore = AgentChatTabMetadataStores.getInstance()
  val fileSystem = AgentChatVirtualFileSystems.getInstance()
  val descriptor = AgentChatFileDescriptor.create(
    projectHash = project.locationHash,
    projectPath = projectPath,
    threadIdentity = threadIdentity,
    threadId = threadId,
    threadTitle = threadTitle,
    subAgentId = subAgentId,
    shellCommand = shellCommand,
  )
  val file = existing ?: fileSystem.getOrCreateFile(descriptor)
  if (existing != null) {
    existing.updateCommandAndThreadId(shellCommand = shellCommand, threadId = threadId)
    val updated = existing.updateThreadTitle(threadTitle)
    metadataStore.upsert(existing.toDescriptor())
    LOG.debug {
      "openChat existing tab update(identity=$threadIdentity, subAgentId=$subAgentId): updated=$updated, currentName=${existing.name}, currentTitle=${existing.threadTitle}"
    }
    if (updated) {
      manager.updateFilePresentation(existing)
    }
  }
  else {
    metadataStore.upsert(descriptor)
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

suspend fun collectOpenAgentChatProjectPaths(): Set<String> = runOnEdt {
  val paths = LinkedHashSet<String>()
  for (project in ProjectManager.getInstance().openProjects) {
    val manager = runCatching { FileEditorManagerEx.getInstanceEx(project) }.getOrNull() ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      paths.add(normalizeAgentChatProjectPath(chatFile.projectPath))
    }
  }
  paths
}

suspend fun updateOpenAgentChatTabTitles(
  titleByPathAndThreadIdentity: Map<Pair<String, String>, String>,
): Int {
  if (titleByPathAndThreadIdentity.isEmpty()) {
    return 0
  }

  var updatedTabs = 0
  val metadataStore = AgentChatTabMetadataStores.getInstance()
  runOnEdt {
    for (project in ProjectManager.getInstance().openProjects) {
      val manager = runCatching { FileEditorManagerEx.getInstanceEx(project) }.getOrNull() ?: continue
      for (openFile in manager.openFiles) {
        val chatFile = openFile as? AgentChatVirtualFile ?: continue
        val targetTitle = titleByPathAndThreadIdentity[
          normalizeAgentChatProjectPath(chatFile.projectPath) to chatFile.threadIdentity
        ] ?: continue
        if (chatFile.updateThreadTitle(targetTitle)) {
          metadataStore.upsert(chatFile.toDescriptor())
          manager.updateFilePresentation(chatFile)
          updatedTabs++
        }
      }
    }
  }
  LOG.debug {
    "updateOpenAgentChatTabTitles updatedTabs=$updatedTabs, requested=${titleByPathAndThreadIdentity.size}"
  }
  return updatedTabs
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

private suspend fun <T> runOnEdt(action: () -> T): T {
  if (ApplicationManager.getApplication()?.isDispatchThread == true) {
    return action()
  }
  return withContext(Dispatchers.EDT) {
    action()
  }
}
