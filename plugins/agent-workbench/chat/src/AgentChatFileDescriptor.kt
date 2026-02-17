// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import java.nio.charset.StandardCharsets
import java.util.Base64

private const val AGENT_CHAT_SCHEMA_VERSION = "1"
private const val AGENT_CHAT_PATH_SEPARATOR = '/'
private const val AGENT_CHAT_COMMAND_SEPARATOR = '\u0000'
private val AGENT_CHAT_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
private val AGENT_CHAT_DECODER: Base64.Decoder = Base64.getUrlDecoder()

internal data class AgentChatFileDescriptor(
  val projectHash: String,
  val projectPath: String,
  val threadIdentity: String,
  val threadId: String,
  val threadTitle: String,
  val subAgentId: String?,
  val shellCommand: List<String>,
) {
  fun stableKey(): String {
    return buildString {
      append(projectHash)
      append('|')
      append(projectPath)
      append('|')
      append(threadIdentity)
      append('|')
      append(subAgentId.orEmpty())
    }
  }

  fun toPath(): String {
    return buildString {
      append(AGENT_CHAT_SCHEMA_VERSION)
      append(AGENT_CHAT_PATH_SEPARATOR)
      append(encodeSegment(projectHash))
      append(AGENT_CHAT_PATH_SEPARATOR)
      append(encodeSegment(projectPath))
      append(AGENT_CHAT_PATH_SEPARATOR)
      append(encodeSegment(threadIdentity))
      append(AGENT_CHAT_PATH_SEPARATOR)
      append(encodeSegment(threadId))
      append(AGENT_CHAT_PATH_SEPARATOR)
      append(encodeSegment(threadTitle))
      append(AGENT_CHAT_PATH_SEPARATOR)
      append(encodeSegment(subAgentId.orEmpty()))
      append(AGENT_CHAT_PATH_SEPARATOR)
      append(encodeSegment(shellCommand.joinToString(separator = AGENT_CHAT_COMMAND_SEPARATOR.toString())))
    }
  }

  companion object {
    fun parsePath(path: String): AgentChatFileDescriptor? {
      val segments = path.split(AGENT_CHAT_PATH_SEPARATOR, limit = 8)
      if (segments.size != 8 || segments[0] != AGENT_CHAT_SCHEMA_VERSION) {
        return null
      }

      val projectHash = decodeSegment(segments[1]) ?: return null
      val projectPath = decodeSegment(segments[2]) ?: return null
      val threadIdentity = decodeSegment(segments[3]) ?: return null
      val threadId = decodeSegment(segments[4]) ?: return null
      val threadTitle = decodeSegment(segments[5]) ?: return null
      val subAgentId = decodeSegment(segments[6]) ?: return null
      val shellCommandRaw = decodeSegment(segments[7]) ?: return null

      return AgentChatFileDescriptor(
        projectHash = projectHash,
        projectPath = projectPath,
        threadIdentity = threadIdentity,
        threadId = threadId,
        threadTitle = threadTitle,
        subAgentId = subAgentId.ifBlank { null },
        shellCommand = if (shellCommandRaw.isEmpty()) emptyList() else shellCommandRaw.split(AGENT_CHAT_COMMAND_SEPARATOR),
      )
    }

    private fun encodeSegment(value: String): String {
      return AGENT_CHAT_ENCODER.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeSegment(value: String): String? {
      return try {
        String(AGENT_CHAT_DECODER.decode(value), StandardCharsets.UTF_8)
      }
      catch (_: IllegalArgumentException) {
        null
      }
    }
  }
}
