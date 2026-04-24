// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.util.io.DigestUtil
import java.math.BigInteger

internal const val AGENT_CHAT_URL_SCHEMA_VERSION = "2"

private const val AGENT_CHAT_PATH_SEPARATOR = '/'
private const val AGENT_CHAT_TAB_KEY_LENGTH = 50
private const val AGENT_CHAT_TAB_KEY_RADIX = 36

@JvmInline
internal value class AgentChatTabKey private constructor(val value: String) {
  fun toPath(): String = "$AGENT_CHAT_URL_SCHEMA_VERSION$AGENT_CHAT_PATH_SEPARATOR$value"

  companion object {
    fun fromIdentity(identity: AgentChatTabIdentity): AgentChatTabKey {
      val stableIdentity = buildString {
        append(identity.projectHash)
        append('|')
        append(identity.projectPath)
        append('|')
        append(identity.threadIdentity)
        append('|')
        append(identity.subAgentId.orEmpty())
      }
      val digest = DigestUtil.sha256().digest(stableIdentity.toByteArray(Charsets.UTF_8))
      val encoded = BigInteger(1, digest).toString(AGENT_CHAT_TAB_KEY_RADIX)
      check(encoded.length <= AGENT_CHAT_TAB_KEY_LENGTH)
      return AgentChatTabKey(encoded.padStart(AGENT_CHAT_TAB_KEY_LENGTH, '0'))
    }

    fun parse(value: String): AgentChatTabKey? {
      if (value.length != AGENT_CHAT_TAB_KEY_LENGTH || value.any { !it.isLowercaseBase36Char() }) {
        return null
      }
      return AgentChatTabKey(value)
    }

    fun parsePath(path: String): AgentChatTabKey? {
      val segments = path.split(AGENT_CHAT_PATH_SEPARATOR, limit = 3)
      if (segments.size != 2 || segments[0] != AGENT_CHAT_URL_SCHEMA_VERSION) {
        return null
      }
      return parse(segments[1])
    }
  }
}

private fun Char.isLowercaseBase36Char(): Boolean {
  return this in '0'..'9' || this in 'a'..'z'
}
