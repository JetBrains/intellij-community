// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec plugins/ij-air/spec/thread-view/agent-thread-view.spec.md
package com.intellij.agent.workbench.thread.view

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.TestOnly

internal class AgentThreadViewVirtualFileSystem : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {
  override fun getProtocol(): String = AGENT_THREAD_VIEW_PROTOCOL

  override fun findFileByPath(path: String): VirtualFile? {
    val resolution = service<AgentThreadViewTabsService>().resolveFromPath(path) ?: return null
    return getOrCreateFileSync(resolution)
  }

  override fun refresh(asynchronous: Boolean) = Unit

  override fun refreshAndFindFileByPath(path: String): VirtualFile? {
    return findFileByPath(path)
  }

  suspend fun getOrCreateFile(snapshot: AgentThreadViewTabSnapshot): AgentThreadViewVirtualFile {
    return getOrCreateFile(AgentThreadViewTabResolution.Resolved(snapshot))
  }

  suspend fun getOrCreateFile(resolution: AgentThreadViewTabResolution): AgentThreadViewVirtualFile {
    val stableKey = resolution.tabKey.value
    val existing = collectOpenAgentThreadViewTabsSnapshotOnUi().findFileByTabKey(stableKey)
    return reuseOrCreateFile(resolution = resolution, existing = existing)
  }

  private fun getOrCreateFileSync(resolution: AgentThreadViewTabResolution): AgentThreadViewVirtualFile {
    val stableKey = resolution.tabKey.value
    val existing = collectOpenAgentThreadViewTabsSnapshot().findFileByTabKey(stableKey)
    return reuseOrCreateFile(resolution = resolution, existing = existing)
  }

  private fun reuseOrCreateFile(
    resolution: AgentThreadViewTabResolution,
    existing: AgentThreadViewVirtualFile?,
  ): AgentThreadViewVirtualFile {
    // Resolved snapshots can come from persisted restore state. They keep bootstrap title/activity on the
    // file itself, but only explicit open/rebind/provider-refresh paths may republish live shared presentation.
    return if (existing != null) {
      existing.updateFromResolution(resolution)
      existing
    }
    else {
      AgentThreadViewVirtualFile(fileSystem = this, resolution = resolution)
    }
  }
}

internal const val AGENT_THREAD_VIEW_PROTOCOL: String = "agent-thread-view"

internal suspend fun agentThreadViewVirtualFileSystem(): AgentThreadViewVirtualFileSystem {
  checkNotNull(ApplicationManager.getApplication()) {
    "AgentThreadViewVirtualFileSystem requires an initialized application"
  }
  val fileSystem = serviceAsync<VirtualFileManager>().getFileSystem(AGENT_THREAD_VIEW_PROTOCOL)
  return (fileSystem as? AgentThreadViewVirtualFileSystem)
    ?: error("AgentThreadViewVirtualFileSystem is not registered for protocol $AGENT_THREAD_VIEW_PROTOCOL")
}

@TestOnly
internal fun createStandaloneAgentThreadViewVirtualFileSystemForTest(): AgentThreadViewVirtualFileSystem = AgentThreadViewVirtualFileSystem()
