// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

internal class AgentChatVirtualFileSystem : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {
  private val filesByStableKey = ConcurrentHashMap<String, AgentChatVirtualFile>()

  override fun getProtocol(): String = AGENT_CHAT_PROTOCOL

  override fun findFileByPath(path: String): VirtualFile? {
    val resolution = service<AgentChatTabsService>().resolveFromPath(path) ?: return null
    return getOrCreateFile(resolution)
  }

  override fun refresh(asynchronous: Boolean) = Unit

  override fun refreshAndFindFileByPath(path: String): VirtualFile? {
    return findFileByPath(path)
  }

  fun getOrCreateFile(snapshot: AgentChatTabSnapshot): AgentChatVirtualFile {
    return getOrCreateFile(AgentChatTabResolution.Resolved(snapshot))
  }

  fun getOrCreateFile(resolution: AgentChatTabResolution): AgentChatVirtualFile {
    val stableKey = resolution.tabKey.value
    val file = filesByStableKey.computeIfAbsent(stableKey) {
      AgentChatVirtualFile(fileSystem = this, resolution = resolution)
    }
    file.updateFromResolution(resolution)
    return file
  }

  fun forgetFile(tabKey: String): Boolean {
    return filesByStableKey.remove(tabKey) != null
  }

  @TestOnly
  fun clearFilesForTests() {
    filesByStableKey.clear()
  }

}

internal const val AGENT_CHAT_PROTOCOL: String = "agent-chat"

internal fun agentChatVirtualFileSystem(): AgentChatVirtualFileSystem {
  checkNotNull(ApplicationManager.getApplication()) {
    "AgentChatVirtualFileSystem requires an initialized application"
  }
  val fileSystem = VirtualFileManager.getInstance().getFileSystem(AGENT_CHAT_PROTOCOL)
  return (fileSystem as? AgentChatVirtualFileSystem)
    ?: error("AgentChatVirtualFileSystem is not registered for protocol $AGENT_CHAT_PROTOCOL")
}

@TestOnly
internal fun createStandaloneAgentChatVirtualFileSystemForTest(): AgentChatVirtualFileSystem = AgentChatVirtualFileSystem()
