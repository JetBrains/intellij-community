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
  // TODO: Populate subAgents once Codex exposes multi-agent hierarchy data.
  @JvmField val subAgents: List<CodexSubAgent> = emptyList(),
  @JvmField val gitBranch: String? = null,
  @JvmField val cwd: String? = null,
)

@Immutable
data class CodexProjectSessions(
  @JvmField val path: String,
  @JvmField val name: String,
  @JvmField val isOpen: Boolean,
  @JvmField val threads: List<CodexThread> = emptyList(),
  @JvmField val isLoading: Boolean = false,
  @JvmField val isPagingThreads: Boolean = false,
  @JvmField val hasLoaded: Boolean = false,
  @JvmField val nextThreadsCursor: String? = null,
  @JvmField val errorMessage: String? = null,
  @JvmField val loadMoreErrorMessage: String? = null,
)

@Immutable
data class CodexThreadPage(
  @JvmField val threads: List<CodexThread>,
  @JvmField val nextCursor: String?,
)

data class CodexSessionsState(
  @JvmField val projects: List<CodexProjectSessions> = emptyList(),
  @JvmField val lastUpdatedAt: Long? = null,
)
