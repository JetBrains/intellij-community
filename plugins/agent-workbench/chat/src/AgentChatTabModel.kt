// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity

internal data class AgentChatTabIdentity(
  @JvmField val projectHash: String,
  @JvmField val projectPath: String,
  @JvmField val threadIdentity: String,
  @JvmField val subAgentId: String?,
)

internal data class AgentChatTabRuntime(
  @JvmField val threadId: String,
  @JvmField val threadTitle: String,
  @JvmField val shellCommand: List<String>,
  @JvmField val threadActivity: AgentThreadActivity,
  @JvmField val pendingCreatedAtMs: Long? = null,
  @JvmField val pendingFirstInputAtMs: Long? = null,
  @JvmField val pendingLaunchMode: String? = null,
)

internal data class AgentChatTabSnapshot(
  val tabKey: AgentChatTabKey,
  @JvmField val identity: AgentChatTabIdentity,
  @JvmField val runtime: AgentChatTabRuntime,
) {
  companion object {
    fun create(
      projectHash: String,
      projectPath: String,
      threadIdentity: String,
      threadId: String,
      threadTitle: String,
      subAgentId: String?,
      shellCommand: List<String>,
      threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
      pendingCreatedAtMs: Long? = null,
      pendingFirstInputAtMs: Long? = null,
      pendingLaunchMode: String? = null,
    ): AgentChatTabSnapshot {
      val identity = AgentChatTabIdentity(
        projectHash = projectHash,
        projectPath = projectPath,
        threadIdentity = threadIdentity,
        subAgentId = subAgentId,
      )
      return AgentChatTabSnapshot(
        tabKey = AgentChatTabKey.fromIdentity(identity),
        identity = identity,
        runtime = AgentChatTabRuntime(
          threadId = threadId,
          threadTitle = threadTitle,
          shellCommand = shellCommand,
          threadActivity = threadActivity,
          pendingCreatedAtMs = pendingCreatedAtMs,
          pendingFirstInputAtMs = pendingFirstInputAtMs,
          pendingLaunchMode = pendingLaunchMode,
        ),
      )
    }
  }
}

internal sealed interface AgentChatTabResolution {
  val tabKey: AgentChatTabKey

  data class Resolved(
    @JvmField val snapshot: AgentChatTabSnapshot,
  ) : AgentChatTabResolution {
    override val tabKey: AgentChatTabKey = snapshot.tabKey
  }

  data class Unresolved(
    override val tabKey: AgentChatTabKey,
  ) : AgentChatTabResolution
}
