// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AgentChatMetadataLifecycleLog

private val LIFECYCLE_LOG = logger<AgentChatMetadataLifecycleLog>()

suspend fun closeAndForgetAgentChatsForThread(
  projectPath: String,
  threadIdentity: String,
) {
  val normalizedProjectPath = normalizeAgentChatProjectPath(projectPath)
  val closedTabs = closeMatchingOpenTabs(normalizedProjectPath, threadIdentity)
  // Metadata deletion may parse many files; keep this off EDT/archive caller threads.
  val deletedMetadataFiles = withContext(Dispatchers.IO) {
    AgentChatTabMetadataStores.getInstance()
      .deleteByThread(normalizedProjectPath, threadIdentity)
  }

  LIFECYCLE_LOG.debug {
    "Archived thread cleanup(identity=$threadIdentity, path=$normalizedProjectPath): closedTabs=$closedTabs, deletedMetadataFiles=$deletedMetadataFiles"
  }
}

private suspend fun closeMatchingOpenTabs(projectPath: String, threadIdentity: String): Int {
  return withContext(Dispatchers.EDT) {
    var closedTabs = 0
    for (project in ProjectManager.getInstance().openProjects) {
      if (project.isDisposed) {
        continue
      }

      val manager = runCatching { FileEditorManager.getInstance(project) }.getOrNull() ?: continue
      val matchingFiles = manager.openFiles.filterIsInstance<AgentChatVirtualFile>().filter { chatFile ->
        normalizeAgentChatProjectPath(chatFile.projectPath) == projectPath &&
        chatFile.threadIdentity == threadIdentity
      }
      for (chatFile in matchingFiles) {
        manager.closeFile(chatFile)
        closedTabs++
      }
    }
    closedTabs
  }
}

internal fun forgetAgentChatTabMetadata(tabKey: String) {
  val application = ApplicationManager.getApplication() ?: return
  if (application.isDisposed) {
    return
  }
  AgentChatTabMetadataStores.getInstance().delete(tabKey)
}
