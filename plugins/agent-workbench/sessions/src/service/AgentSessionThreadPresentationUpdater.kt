// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.updateOpenAgentChatTabPresentation
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity

internal interface AgentSessionThreadPresentationUpdater {
  suspend fun updateThread(
    provider: AgentSessionProvider,
    path: String,
    threadId: String,
    title: String,
    activity: AgentThreadActivity?,
  ): Int

  suspend fun updateProviderSnapshot(
    provider: AgentSessionProvider,
    authoritativePaths: Set<String>,
    threadsByPath: Map<String, List<AgentSessionThread>>,
  ): Int

  suspend fun updateActivityHints(
    provider: AgentSessionProvider,
    updates: Collection<AgentSessionThreadActivityPresentationUpdate>,
  ): Int
}

internal data class AgentSessionThreadActivityPresentationUpdate(
  @JvmField val path: String,
  @JvmField val threadId: String,
  @JvmField val activity: AgentThreadActivity,
)

internal class DefaultAgentSessionThreadPresentationUpdater(
  private val openAgentChatTabPresentationUpdater: suspend (
    AgentSessionProvider,
    Set<String>,
    Map<Pair<String, String>, String>,
    Map<Pair<String, String>, AgentThreadActivity>,
  ) -> Int = ::updateOpenAgentChatTabPresentation,
) : AgentSessionThreadPresentationUpdater {
  override suspend fun updateThread(
    provider: AgentSessionProvider,
    path: String,
    threadId: String,
    title: String,
    activity: AgentThreadActivity?,
  ): Int {
    val identityKey = path to buildAgentSessionIdentity(provider = provider, sessionId = threadId)
    return openAgentChatTabPresentationUpdater(
      provider,
      emptySet(),
      mapOf(identityKey to title),
      if (activity == null) emptyMap() else mapOf(identityKey to activity),
    )
  }

  override suspend fun updateProviderSnapshot(
    provider: AgentSessionProvider,
    authoritativePaths: Set<String>,
    threadsByPath: Map<String, List<AgentSessionThread>>,
  ): Int {
    val titleByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, String>()
    val activityByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, AgentThreadActivity>()
    for ((path, threads) in threadsByPath) {
      for ((threadId, title, _, _, activity, threadProvider) in threads) {
        if (threadProvider != provider) continue
        val identityKey = path to buildAgentSessionIdentity(provider = threadProvider, sessionId = threadId)
        titleByPathAndThreadIdentity[identityKey] = title
        activityByPathAndThreadIdentity[identityKey] = activity
      }
    }

    return openAgentChatTabPresentationUpdater(
      provider,
      authoritativePaths,
      titleByPathAndThreadIdentity,
      activityByPathAndThreadIdentity,
    )
  }

  override suspend fun updateActivityHints(
    provider: AgentSessionProvider,
    updates: Collection<AgentSessionThreadActivityPresentationUpdate>,
  ): Int {
    if (updates.isEmpty()) {
      return 0
    }

    val activityByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, AgentThreadActivity>(updates.size)
    for ((path, threadId, activity) in updates) {
      val identityKey = path to buildAgentSessionIdentity(provider = provider, sessionId = threadId)
      activityByPathAndThreadIdentity[identityKey] = activity
    }

    return openAgentChatTabPresentationUpdater(
      provider,
      emptySet(),
      emptyMap(),
      activityByPathAndThreadIdentity,
    )
  }
}
