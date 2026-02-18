// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile

private class AgentChatVirtualFileLog

private val LOG = logger<AgentChatVirtualFileLog>()

internal class AgentChatVirtualFile internal constructor(
  private val fileSystem: AgentChatVirtualFileSystem,
  descriptor: AgentChatFileDescriptor,
) : LightVirtualFile(resolveFileName(descriptor.tabKey)) {
  val tabKey: String = descriptor.tabKey
  var projectHash: String = descriptor.projectHash
    private set

  var projectPath: String = descriptor.projectPath
    private set

  var threadIdentity: String = descriptor.threadIdentity
    private set

  var subAgentId: String? = descriptor.subAgentId
    private set

  var shellCommand: List<String> = descriptor.shellCommand
    private set

  var threadId: String = descriptor.threadId
    private set

  var threadTitle: String = resolveThreadTitle(descriptor.threadTitle)
    private set

  internal constructor(
    projectPath: String,
    threadIdentity: String,
    shellCommand: List<String>,
    threadId: String,
    threadTitle: String,
    subAgentId: String?,
    projectHash: String = "",
  ) : this(
    fileSystem = AgentChatVirtualFileSystems.getInstanceOrFallback(),
    descriptor = AgentChatFileDescriptor.create(
      projectHash = projectHash,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
      shellCommand = shellCommand,
    ),
  )

  init {
    fileType = AgentChatFileType
    isWritable = false
  }

  override fun getFileSystem(): VirtualFileSystem = fileSystem

  override fun getPath(): String = toDescriptor().toPath()

  fun matches(threadIdentity: String, subAgentId: String?): Boolean {
    return this.threadIdentity == threadIdentity && this.subAgentId == subAgentId
  }

  fun updateThreadTitle(threadTitle: String): Boolean {
    val resolvedTitle = resolveThreadTitle(threadTitle)
    if (this.threadTitle == resolvedTitle) {
      LOG.debug {
        "Skipped tab title update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged title=$resolvedTitle"
      }
      return false
    }

    val oldTitle = this.threadTitle
    this.threadTitle = resolvedTitle
    LOG.debug {
      "Updated tab title(identity=$threadIdentity, subAgentId=$subAgentId): oldTitle=$oldTitle newTitle=$resolvedTitle"
    }
    return true
  }

  fun updateCommandAndThreadId(shellCommand: List<String>, threadId: String) {
    this.shellCommand = shellCommand
    this.threadId = threadId
  }

  internal fun updateFromDescriptor(descriptor: AgentChatFileDescriptor) {
    if (descriptor.threadIdentity.isNotBlank() || descriptor.projectPath.isNotBlank()) {
      projectHash = descriptor.projectHash
      projectPath = descriptor.projectPath
      threadIdentity = descriptor.threadIdentity
      subAgentId = descriptor.subAgentId
    }
    if (descriptor.threadId.isNotBlank() || descriptor.shellCommand.isNotEmpty()) {
      updateCommandAndThreadId(shellCommand = descriptor.shellCommand, threadId = descriptor.threadId)
    }
    if (descriptor.threadTitle.isNotBlank()) {
      updateThreadTitle(descriptor.threadTitle)
    }
  }

  internal fun toDescriptor(): AgentChatFileDescriptor {
    return AgentChatFileDescriptor(
      tabKey = tabKey,
      projectHash = projectHash,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
      shellCommand = shellCommand,
    )
  }
}

private fun resolveFileName(tabKey: String): String {
  return "chat-$tabKey"
}

private fun resolveThreadTitle(threadTitle: String): String {
  return threadTitle.takeIf { it.isNotBlank() } ?: AgentChatBundle.message("chat.filetype.name")
}
