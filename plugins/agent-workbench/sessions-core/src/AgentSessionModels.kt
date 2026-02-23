// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity

private val AGENT_SESSION_PROVIDER_ID_REGEX = Regex("[a-z][a-z0-9._-]*")

@JvmInline
value class AgentSessionProvider private constructor(val value: String) {
  companion object {
    val CODEX: AgentSessionProvider = from("codex")

    val CLAUDE: AgentSessionProvider = from("claude")

    fun from(value: String): AgentSessionProvider {
      require(AGENT_SESSION_PROVIDER_ID_REGEX.matches(value)) {
        "Invalid provider id '$value'. Expected: ${AGENT_SESSION_PROVIDER_ID_REGEX.pattern}"
      }
      return AgentSessionProvider(value)
    }

    fun fromOrNull(value: String): AgentSessionProvider? {
      return if (AGENT_SESSION_PROVIDER_ID_REGEX.matches(value)) AgentSessionProvider(value) else null
    }
  }

  override fun toString(): String = value
}

enum class AgentSessionLaunchMode {
  STANDARD,
  YOLO,
}

data class AgentSubAgent(
  @JvmField val id: String,
  @JvmField val name: String,
)

data class AgentSessionThread(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val archived: Boolean,
  @JvmField val activity: AgentThreadActivity = AgentThreadActivity.READY,
  val provider: AgentSessionProvider = AgentSessionProvider.CODEX,
  @JvmField val subAgents: List<AgentSubAgent> = emptyList(),
  @JvmField val originBranch: String? = null,
)
