// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.util.io.DigestUtil
import java.math.BigInteger

private const val AGENT_CHAT_SCHEMA_VERSION = "2"
private const val AGENT_CHAT_PATH_SEPARATOR = '/'
private const val AGENT_CHAT_TAB_KEY_LENGTH = 50
private const val AGENT_CHAT_TAB_KEY_RADIX = 36

internal data class AgentChatFileDescriptor(
  val tabKey: String,
  val projectHash: String,
  val projectPath: String,
  val threadIdentity: String,
  val threadId: String,
  val threadTitle: String,
  val subAgentId: String?,
  val shellCommand: List<String>,
) {
  fun stableKey(): String = tabKey

  fun toPath(): String = "$AGENT_CHAT_SCHEMA_VERSION$AGENT_CHAT_PATH_SEPARATOR$tabKey"

  companion object {
    fun create(
      projectHash: String,
      projectPath: String,
      threadIdentity: String,
      threadId: String,
      threadTitle: String,
      subAgentId: String?,
      shellCommand: List<String>,
    ): AgentChatFileDescriptor {
      val tabKey = buildTabKey(
        projectHash = projectHash,
        projectPath = projectPath,
        threadIdentity = threadIdentity,
        subAgentId = subAgentId,
      )
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

    fun unresolved(tabKey: String): AgentChatFileDescriptor {
      return AgentChatFileDescriptor(
        tabKey = tabKey,
        projectHash = "",
        projectPath = "",
        threadIdentity = "",
        threadId = "",
        threadTitle = "",
        subAgentId = null,
        shellCommand = emptyList(),
      )
    }

    fun parsePath(path: String): String? {
      val segments = path.split(AGENT_CHAT_PATH_SEPARATOR, limit = 3)
      if (segments.size != 2 || segments[0] != AGENT_CHAT_SCHEMA_VERSION) {
        return null
      }
      val tabKey = segments[1]
      if (tabKey.length != AGENT_CHAT_TAB_KEY_LENGTH || tabKey.any { !it.isLowercaseBase36Char() }) {
        return null
      }
      return tabKey
    }
  }
}

private fun buildTabKey(projectHash: String, projectPath: String, threadIdentity: String, subAgentId: String?): String {
  val stableIdentity = buildString {
    append(projectHash)
    append('|')
    append(projectPath)
    append('|')
    append(threadIdentity)
    append('|')
    append(subAgentId.orEmpty())
  }
  val digest = DigestUtil.sha256().digest(stableIdentity.toByteArray(Charsets.UTF_8))
  val encoded = BigInteger(1, digest).toString(AGENT_CHAT_TAB_KEY_RADIX)
  check(encoded.length <= AGENT_CHAT_TAB_KEY_LENGTH)
  return encoded.padStart(AGENT_CHAT_TAB_KEY_LENGTH, '0')
}

private fun Char.isLowercaseBase36Char(): Boolean {
  return this in '0'..'9' || this in 'a'..'z'
}
