// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionContentRepository
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmPathSnapshot
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.InMemorySessionWarmState
import com.intellij.openapi.util.text.StringUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionContentRepositoryTest {
  @Test
  fun findArchiveNotificationLabelPrefersRuntimeThreadTitleOverWarmSnapshot() {
    val stateStore = AgentSessionsStateStore()
    val warmState = InMemorySessionWarmState()
    val repository = AgentSessionContentRepository(stateStore = stateStore, warmState = warmState)

    stateStore.replaceProjects(
      projects = listOf(
        AgentProjectSessions(
          path = TEST_PROJECT_PATH,
          name = "Project A",
          isOpen = true,
          threads = listOf(thread(id = "thread-1", title = "Runtime title")),
        )
      ),
      visibleThreadCounts = emptyMap(),
    )
    warmState.setPathSnapshot(
      TEST_PROJECT_PATH,
      AgentSessionWarmPathSnapshot(
        threads = listOf(thread(id = "thread-1", title = "Warm title")),
        hasUnknownThreadCount = false,
        updatedAt = 100L,
      )
    )

    val label = repository.findArchiveNotificationLabel(
      ArchiveThreadTarget.Thread(path = TEST_PROJECT_PATH, provider = AgentSessionProvider.CODEX, threadId = "thread-1")
    )

    assertThat(label).isEqualTo("Runtime title")
  }

  @Test
  fun findArchiveNotificationLabelFallsBackToWarmSnapshotAndCompactsLongSubAgentName() {
    val stateStore = AgentSessionsStateStore()
    val warmState = InMemorySessionWarmState()
    val repository = AgentSessionContentRepository(stateStore = stateStore, warmState = warmState)
    val longSubAgentName = "Sub-agent " + "a".repeat(120) + " tail"

    warmState.setPathSnapshot(
      TEST_PROJECT_PATH,
      AgentSessionWarmPathSnapshot(
        threads = listOf(
          thread(
            id = "thread-1",
            title = "Parent thread",
            subAgents = listOf(AgentSubAgent(id = "sub-agent-1", name = longSubAgentName)),
          )
        ),
        hasUnknownThreadCount = false,
        updatedAt = 100L,
      )
    )

    val label = repository.findArchiveNotificationLabel(
      ArchiveThreadTarget.SubAgent(
        path = TEST_PROJECT_PATH,
        provider = AgentSessionProvider.CODEX,
        parentThreadId = "thread-1",
        subAgentId = "sub-agent-1",
      )
    )

    assertThat(label).isEqualTo(StringUtil.trimMiddle(longSubAgentName, 50))
  }
}

private const val TEST_PROJECT_PATH = "/work/project-a"

private fun thread(
  id: String,
  title: String,
  subAgents: List<AgentSubAgent> = emptyList(),
): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = 100L,
    archived = false,
    provider = AgentSessionProvider.CODEX,
    subAgents = subAgents,
  )
}
