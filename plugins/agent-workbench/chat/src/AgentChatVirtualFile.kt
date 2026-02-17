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
) : LightVirtualFile(resolveFileName(descriptor.threadTitle)) {
  val projectHash: String = descriptor.projectHash
  val projectPath: String = descriptor.projectPath
  val threadIdentity: String = descriptor.threadIdentity
  val subAgentId: String? = descriptor.subAgentId

  var shellCommand: List<String> = descriptor.shellCommand
    private set

  var threadId: String = descriptor.threadId
    private set

  var threadTitle: String = resolveFileName(descriptor.threadTitle)
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
    descriptor = AgentChatFileDescriptor(
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
    // Keep writable so tab title can be renamed when the thread title changes.
    isWritable = true
  }

  override fun getFileSystem(): VirtualFileSystem = fileSystem

  override fun getPath(): String = toDescriptor().toPath()

  fun matches(threadIdentity: String, subAgentId: String?): Boolean {
    return this.threadIdentity == threadIdentity && this.subAgentId == subAgentId
  }

  fun updateThreadTitle(threadTitle: String): Boolean {
    val resolvedTitle = resolveFileName(threadTitle)
    if (this.threadTitle == resolvedTitle && name == resolvedTitle) {
      LOG.debug {
        "Skipped tab title update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged title=$resolvedTitle"
      }
      return false
    }

    val oldTitle = this.threadTitle
    val oldName = name
    this.threadTitle = resolvedTitle
    rename(null, resolvedTitle)
    LOG.debug {
      "Updated tab title(identity=$threadIdentity, subAgentId=$subAgentId): oldTitle=$oldTitle oldName=$oldName newTitle=$resolvedTitle newName=$name"
    }
    return true
  }

  fun updateCommandAndThreadId(shellCommand: List<String>, threadId: String) {
    this.shellCommand = shellCommand
    this.threadId = threadId
  }

  internal fun updateFromDescriptor(descriptor: AgentChatFileDescriptor) {
    updateCommandAndThreadId(shellCommand = descriptor.shellCommand, threadId = descriptor.threadId)
    if (descriptor.threadTitle.isNotBlank()) {
      updateThreadTitle(descriptor.threadTitle)
    }
  }

  private fun toDescriptor(): AgentChatFileDescriptor {
    return AgentChatFileDescriptor(
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

private fun resolveFileName(threadTitle: String): String {
  return threadTitle.takeIf { it.isNotBlank() } ?: AgentChatBundle.message("chat.filetype.name")
}
