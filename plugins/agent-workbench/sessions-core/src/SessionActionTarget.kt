// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core

sealed interface SessionActionTarget {
  data class Project(
    val path: String,
    val isOpen: Boolean,
  ) : SessionActionTarget

  data class Worktree(
    val path: String,
  ) : SessionActionTarget

  sealed interface Conversation : SessionActionTarget {
    val path: String
    val provider: AgentSessionProvider
    val threadId: String
    val title: String
  }

  data class Thread(
    override val path: String,
    override val provider: AgentSessionProvider,
    override val threadId: String,
    override val title: String,
    val thread: AgentSessionThread? = null,
  ) : Conversation

  data class SubAgent(
    override val path: String,
    override val provider: AgentSessionProvider,
    val parentThreadId: String,
    val subAgentId: String,
    override val title: String,
    val thread: AgentSessionThread? = null,
    val subAgent: AgentSubAgent? = null,
  ) : Conversation {
    override val threadId: String
      get() = subAgentId
  }

  data class MoreProjects(
    val hiddenCount: Int,
  ) : SessionActionTarget

  data class MoreThreads(
    val path: String,
    val hiddenCount: Int?,
  ) : SessionActionTarget
}
