// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.LightVirtualFile

private class AgentChatVirtualFileLog

private val LOG = logger<AgentChatVirtualFileLog>()

internal class AgentChatVirtualFile(
  val projectPath: String,
  val threadIdentity: String,
  val shellCommand: List<String>,
  val threadId: String,
  threadTitle: String,
  val subAgentId: String?,
) : LightVirtualFile(resolveFileName(threadTitle)) {
  var threadTitle: String = resolveFileName(threadTitle)
    private set

  init {
    fileType = AgentChatFileType
    // Keep writable so tab title can be renamed when the thread title changes.
    isWritable = true
  }

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
}

private fun resolveFileName(threadTitle: String): String {
  return threadTitle.takeIf { it.isNotBlank() } ?: AgentChatBundle.message("chat.filetype.name")
}
