// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AgentChatThreadCleanupResult(
  @JvmField val closedTabs: Int,
  @JvmField val deletedStates: Int,
)

@Service(Service.Level.APP)
internal class AgentChatTabsService {
  private val stateService: AgentChatTabsStateService
    get() = service<AgentChatTabsStateService>()

  fun resolveFromPath(path: String): AgentChatTabResolution? {
    val tabKey = AgentChatTabKey.parsePath(path) ?: return null
    val snapshot = stateService.load(tabKey)
    return if (snapshot != null) {
      AgentChatTabResolution.Resolved(snapshot)
    }
    else {
      AgentChatTabResolution.Unresolved(tabKey)
    }
  }

  fun upsert(snapshot: AgentChatTabSnapshot) {
    stateService.upsert(snapshot)
  }

  fun forget(tabKey: AgentChatTabKey): Boolean {
    return stateService.delete(tabKey)
  }

  fun forget(tabKey: String): Boolean {
    return stateService.delete(tabKey)
  }

  fun load(tabKey: String): AgentChatTabSnapshot? {
    return stateService.load(tabKey)
  }

  suspend fun closeAndForgetByThread(
    projectPath: String,
    threadIdentity: String,
    subAgentId: String? = null,
  ): AgentChatThreadCleanupResult {
    val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
    val closedTabs = withContext(Dispatchers.EDT) {
      closeMatchingOpenTabs(normalizedProjectPath, threadIdentity, subAgentId)
    }
    val deleteResult = withContext(Dispatchers.IO) {
      stateService.deleteByThreadWithKeys(normalizedProjectPath, threadIdentity, subAgentId)
    }
    if (deleteResult.deletedKeys.isNotEmpty()) {
      val fileSystem = agentChatVirtualFileSystem()
      for (tabKey in deleteResult.deletedKeys) {
        fileSystem.forgetFile(tabKey)
      }
    }
    return AgentChatThreadCleanupResult(
      closedTabs = closedTabs,
      deletedStates = deleteResult.deletedKeys.size,
    )
  }
}

private fun closeMatchingOpenTabs(projectPath: String, threadIdentity: String, subAgentId: String?): Int {
  var closedTabs = 0
  for (project in ProjectManager.getInstance().openProjects) {
    if (project.isDisposed) {
      continue
    }

    val manager = runCatching { FileEditorManager.getInstance(project) }.getOrNull() ?: continue
    val matchingFiles = manager.openFiles.filterIsInstance<AgentChatVirtualFile>().filter { chatFile ->
      normalizeAgentWorkbenchPath(chatFile.projectPath) == projectPath &&
      chatFile.threadIdentity == threadIdentity &&
      (subAgentId == null || chatFile.subAgentId == subAgentId)
    }
    for (chatFile in matchingFiles) {
      manager.closeFile(chatFile)
      closedTabs++
    }
  }
  return closedTabs
}
