// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent

sealed interface SessionActionTarget {
  data class Project(
    @JvmField val path: String,
    @JvmField val isOpen: Boolean,
  ) : SessionActionTarget

  data class Worktree(
    @JvmField val path: String,
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
    @JvmField val parentThreadId: String,
    @JvmField val subAgentId: String,
    override val title: String,
    @JvmField val thread: AgentSessionThread? = null,
    @JvmField val subAgent: AgentSubAgent? = null,
  ) : Conversation {
    override val threadId: String
      get() = subAgentId
  }

  data class MoreProjects(
    @JvmField val hiddenCount: Int,
  ) : SessionActionTarget

  data class MoreThreads(
    @JvmField val path: String,
    @JvmField val hiddenCount: Int?,
  ) : SessionActionTarget
}
