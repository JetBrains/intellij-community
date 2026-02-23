// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import androidx.compose.runtime.Immutable

@Immutable
data class CodexSubAgent(
  @JvmField val id: String,
  @JvmField val name: String,
)

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
)

@Immutable
data class CodexThreadPage(
  @JvmField val threads: List<CodexThread>,
  @JvmField val nextCursor: String?,
)
