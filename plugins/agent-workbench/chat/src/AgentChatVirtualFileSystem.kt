// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.util.concurrent.ConcurrentHashMap

internal class AgentChatVirtualFileSystem : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {
  private val filesByStableKey = ConcurrentHashMap<String, AgentChatVirtualFile>()

  override fun getProtocol(): String = AGENT_CHAT_PROTOCOL

  override fun findFileByPath(path: String): VirtualFile? {
    val descriptor = AgentChatFileDescriptor.parsePath(path) ?: return null
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
  private val fallbackInstance = AgentChatVirtualFileSystem()

  fun getInstanceOrFallback(): AgentChatVirtualFileSystem {
    if (ApplicationManager.getApplication() == null) {
      return fallbackInstance
    }

    val fileSystem = VirtualFileManager.getInstance().getFileSystem(AGENT_CHAT_PROTOCOL)
    return (fileSystem as? AgentChatVirtualFileSystem) ?: fallbackInstance
  }
}
