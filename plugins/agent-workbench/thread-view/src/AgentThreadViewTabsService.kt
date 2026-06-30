// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AgentThreadViewThreadCleanupResult(
  @JvmField val closedTabs: Int,
  @JvmField val deletedStates: Int,
)

@Service(Service.Level.APP)
internal class AgentThreadViewTabsService {
  private val stateService: AgentThreadViewTabsStateService
    get() = service<AgentThreadViewTabsStateService>()

  fun resolveFromPath(path: String): AgentThreadViewTabResolution? {
    val tabKey = AgentThreadViewTabKey.parsePath(path) ?: return null
    val snapshot = if (stateService.hasVersionMismatch()) null else stateService.load(tabKey)
    if (snapshot != null) {
      return AgentThreadViewTabResolution.Resolved(snapshot)
    }
    else {
      return AgentThreadViewTabResolution.Unresolved(tabKey)
    }
  }

  fun upsert(snapshot: AgentThreadViewTabSnapshot) {
    stateService.upsert(snapshot)
  }

  fun forget(tabKey: AgentThreadViewTabKey): Boolean {
    val deletedSnapshot = stateService.deleteAndGetSnapshot(tabKey)
    return deletedSnapshot != null
  }

  fun forget(tabKey: String): Boolean {
    return AgentThreadViewTabKey.parse(tabKey)?.let(::forget) ?: false
  }

  fun load(tabKey: String): AgentThreadViewTabSnapshot? {
    return stateService.load(tabKey)
  }

  suspend fun closeAndForgetByThread(
    projectPath: String,
    threadIdentity: String,
    subAgentId: String? = null,
  ): AgentThreadViewThreadCleanupResult {
    val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
    val closedTabs = withContext(Dispatchers.UiWithModelAccess) {
      // FileEditorManager.closeFile() can initiate write-intent, which is disallowed on strict Dispatchers.UI.
      collectOpenAgentThreadViewTabsSnapshot().closeMatchingOpenTabs(normalizedProjectPath, threadIdentity, subAgentId)
    }
    val deleteResult = withContext(Dispatchers.IO) {
      stateService.deleteByThreadWithKeys(normalizedProjectPath, threadIdentity, subAgentId)
    }
    return AgentThreadViewThreadCleanupResult(
      closedTabs = closedTabs,
      deletedStates = deleteResult.deletedKeys.size,
    )
  }
}
