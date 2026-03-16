// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.testFramework.LightVirtualFile

internal class AgentChatVirtualFile(
  val projectPath: String,
  val threadIdentity: String,
  val shellCommand: List<String>,
  val threadId: String,
  val threadTitle: String,
  val subAgentId: String?,
) : LightVirtualFile(resolveFileName(threadTitle)) {
  init {
    fileType = AgentChatFileType
    isWritable = false
  }

  fun matches(threadIdentity: String, subAgentId: String?): Boolean {
    return this.threadIdentity == threadIdentity && this.subAgentId == subAgentId
  }
}

private fun resolveFileName(threadTitle: String): String {
  return threadTitle.takeIf { it.isNotBlank() } ?: AgentChatBundle.message("chat.filetype.name")
}
