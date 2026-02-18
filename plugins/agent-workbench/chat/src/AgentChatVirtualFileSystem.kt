// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.application.ApplicationManager
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
    val tabKey = AgentChatFileDescriptor.parsePath(path) ?: return null
    val metadataStore = AgentChatTabMetadataStores.getInstance()
    val descriptor = metadataStore.loadDescriptor(tabKey) ?: AgentChatFileDescriptor.unresolved(tabKey)
    return getOrCreateFile(descriptor)
  }

  override fun refresh(asynchronous: Boolean) = Unit

  override fun refreshAndFindFileByPath(path: String): VirtualFile? {
    return findFileByPath(path)
  }

  fun getOrCreateFile(descriptor: AgentChatFileDescriptor): AgentChatVirtualFile {
    val stableKey = descriptor.stableKey()
    val file = filesByStableKey.computeIfAbsent(stableKey) {
      AgentChatVirtualFile(fileSystem = this, descriptor = descriptor)
    }
    file.updateFromDescriptor(descriptor)
    return file
  }

}

internal const val AGENT_CHAT_PROTOCOL: String = "agent-chat"

internal object AgentChatVirtualFileSystems {
  fun getInstance(): AgentChatVirtualFileSystem {
    checkNotNull(ApplicationManager.getApplication()) {
      "AgentChatVirtualFileSystem requires an initialized application"
    }
    val fileSystem = VirtualFileManager.getInstance().getFileSystem(AGENT_CHAT_PROTOCOL)
    return (fileSystem as? AgentChatVirtualFileSystem)
      ?: error("AgentChatVirtualFileSystem is not registered for protocol $AGENT_CHAT_PROTOCOL")
  }

  @TestOnly
  fun createStandaloneForTest(): AgentChatVirtualFileSystem = AgentChatVirtualFileSystem()
}
