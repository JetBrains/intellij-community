// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger

private class AgentThreadViewMetadataLifecycleLog

private val LIFECYCLE_LOG = logger<AgentThreadViewMetadataLifecycleLog>()

suspend fun closeAndForgetAgentThreadViewsForThread(
  projectPath: String,
  threadIdentity: String,
  subAgentId: String? = null,
) {
  val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
  val cleanupResult = serviceAsync<AgentThreadViewTabsService>()
    .closeAndForgetByThread(projectPath = normalizedProjectPath, threadIdentity = threadIdentity, subAgentId = subAgentId)

  LIFECYCLE_LOG.debug {
    "Archived thread cleanup(identity=$threadIdentity, subAgentId=$subAgentId, path=$normalizedProjectPath): " +
    "closedTabs=${cleanupResult.closedTabs}, deletedStates=${cleanupResult.deletedStates}"
  }
}

internal fun forgetAgentThreadViewTabMetadata(tabKey: String) {
  val application = ApplicationManager.getApplication() ?: return
  if (application.isDisposed) {
    return
  }
  application.service<AgentThreadViewTabsService>().forget(tabKey)
}
