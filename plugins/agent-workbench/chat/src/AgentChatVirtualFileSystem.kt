// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

internal class AgentChatVirtualFileSystem : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {
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

  suspend fun getOrCreateFile(snapshot: AgentChatTabSnapshot): AgentChatVirtualFile {
    return getOrCreateFile(AgentChatTabResolution.Resolved(snapshot))
  }

  suspend fun getOrCreateFile(resolution: AgentChatTabResolution): AgentChatVirtualFile {
    val stableKey = resolution.tabKey.value
    val existing = findOpenFileByStableKey(tabKey = stableKey, projects = serviceAsync<ProjectManager>().openProjects)
    return reuseOrCreateFile(resolution = resolution, existing = existing)
  }

  private fun getOrCreateFileSync(resolution: AgentChatTabResolution): AgentChatVirtualFile {
    val stableKey = resolution.tabKey.value
    val existing = findOpenFileByStableKey(tabKey = stableKey, projects = ProjectManager.getInstance().openProjects)
    return reuseOrCreateFile(resolution = resolution, existing = existing)
  }

  private fun reuseOrCreateFile(
    resolution: AgentChatTabResolution,
    existing: AgentChatVirtualFile?,
  ): AgentChatVirtualFile {
    if (existing != null) {
      existing.updateFromResolution(resolution)
      return existing
    }
    return AgentChatVirtualFile(fileSystem = this, resolution = resolution)
  }
}

private fun findOpenFileByStableKey(tabKey: String, projects: Array<Project>): AgentChatVirtualFile? {
  for (project in projects) {
    if (project.isDisposed) {
      continue
    }
    val manager = project.serviceIfCreated<FileEditorManager>() ?: continue
    for (openFile in manager.openFiles) {
      val chatFile = openFile as? AgentChatVirtualFile ?: continue
      if (chatFile.tabKey == tabKey) {
        return chatFile
      }
    }
  }
  return null
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

internal suspend fun agentChatVirtualFileSystemAsync(): AgentChatVirtualFileSystem {
  checkNotNull(ApplicationManager.getApplication()) {
    "AgentChatVirtualFileSystem requires an initialized application"
  }
  val fileSystem = serviceAsync<VirtualFileManager>().getFileSystem(AGENT_CHAT_PROTOCOL)
  return (fileSystem as? AgentChatVirtualFileSystem)
    ?: error("AgentChatVirtualFileSystem is not registered for protocol $AGENT_CHAT_PROTOCOL")
}

@TestOnly
internal fun createStandaloneAgentChatVirtualFileSystemForTest(): AgentChatVirtualFileSystem = AgentChatVirtualFileSystem()
