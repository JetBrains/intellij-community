// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import androidx.compose.runtime.Immutable

@Immutable
data class CodexSubAgent(
  @JvmField val id: String,
  @JvmField val name: String,
)

enum class CodexThreadSourceKind {
  CLI,
  VSCODE,
  EXEC,
  APP_SERVER,
  SUB_AGENT,
  SUB_AGENT_REVIEW,
  SUB_AGENT_COMPACT,
  SUB_AGENT_THREAD_SPAWN,
  SUB_AGENT_OTHER,
  UNKNOWN,
}

enum class CodexThreadStatusKind {
  NOT_LOADED,
  IDLE,
  ACTIVE,
  SYSTEM_ERROR,
  UNKNOWN,
}

enum class CodexThreadActiveFlag {
  WAITING_ON_APPROVAL,
  WAITING_ON_USER_INPUT,
}

@Immutable
data class CodexThread(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val archived: Boolean,
  // Populated when backend data includes subagent hierarchy (for example, rollout thread_spawn metadata).
  @JvmField val subAgents: List<CodexSubAgent> = emptyList(),
  @JvmField val gitBranch: String? = null,
  @JvmField val cwd: String? = null,
  @JvmField val sourceKind: CodexThreadSourceKind = CodexThreadSourceKind.UNKNOWN,
  @JvmField val parentThreadId: String? = null,
  @JvmField val agentNickname: String? = null,
  @JvmField val agentRole: String? = null,
  @JvmField val statusKind: CodexThreadStatusKind = CodexThreadStatusKind.UNKNOWN,
  @JvmField val activeFlags: List<CodexThreadActiveFlag> = emptyList(),
)

@Immutable
data class CodexThreadPage(
  @JvmField val threads: List<CodexThread>,
  @JvmField val nextCursor: String?,
)
