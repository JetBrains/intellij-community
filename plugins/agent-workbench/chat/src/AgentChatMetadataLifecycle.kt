// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger

private class AgentChatMetadataLifecycleLog

private val LIFECYCLE_LOG = logger<AgentChatMetadataLifecycleLog>()

suspend fun closeAndForgetAgentChatsForThread(
  projectPath: String,
  threadIdentity: String,
  subAgentId: String? = null,
) {
  val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
  val cleanupResult = serviceAsync<AgentChatTabsService>()
    .closeAndForgetByThread(projectPath = normalizedProjectPath, threadIdentity = threadIdentity, subAgentId = subAgentId)

  LIFECYCLE_LOG.debug {
    "Archived thread cleanup(identity=$threadIdentity, subAgentId=$subAgentId, path=$normalizedProjectPath): " +
    "closedTabs=${cleanupResult.closedTabs}, deletedStates=${cleanupResult.deletedStates}"
  }
}

internal fun forgetAgentChatTabMetadata(tabKey: String) {
  val application = ApplicationManager.getApplication() ?: return
  if (application.isDisposed) {
    return
  }
  application.service<AgentChatTabsService>().forget(tabKey)
  runCatching {
    agentChatVirtualFileSystem().forgetFile(tabKey)
  }.onFailure { t ->
    LIFECYCLE_LOG.debug("Failed to evict Agent Chat virtual file for tabKey=$tabKey", t)
  }
}
