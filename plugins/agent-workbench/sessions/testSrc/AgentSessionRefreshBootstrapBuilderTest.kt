// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.service.AgentSessionContentRepository
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshBootstrapBuilder
import com.intellij.agent.workbench.sessions.service.RefreshLoadScope
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmPathSnapshot
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.InMemorySessionWarmState
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionRefreshBootstrapBuilderTest {
  @Test
  fun buildMergesWarmAndRuntimeProviderMetadata() {
    runBlocking(Dispatchers.Default) {
      val stateStore = AgentSessionsStateStore()
      val warmState = InMemorySessionWarmState()
      warmState.setPathSnapshot(
        PROJECT_PATH,
        AgentSessionWarmPathSnapshot(
          threads = listOf(thread(id = "claude-warm", updatedAt = 100, provider = AgentSessionProvider.CLAUDE)),
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CLAUDE),
          providersWithUnknownThreadCount = setOf(AgentSessionProvider.CLAUDE),
          updatedAt = 100,
        ),
      )
      val builder = AgentSessionRefreshBootstrapBuilder(
        projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        stateStore = stateStore,
        contentRepository = AgentSessionContentRepository(stateStore = stateStore, warmState = warmState),
      )
      val currentState = AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            threads = listOf(thread(id = "codex-runtime", updatedAt = 200, provider = AgentSessionProvider.CODEX)),
            providerLoadStates = mapOf(AgentSessionProvider.CODEX to AgentSessionProviderLoadState.LOADING),
          )
        ),
      )

      val bootstrap = builder.build(currentState, RefreshLoadScope.NEWLY_OPENED_ONLY)

      val project = bootstrap.initialProjects.single()
      assertThat(project.threads.map { it.id }).containsExactly("codex-runtime")
      assertThat(project.providerLoadStates).containsEntry(AgentSessionProvider.CODEX, AgentSessionProviderLoadState.LOADING)
      assertThat(project.providerLoadStates).containsEntry(AgentSessionProvider.CLAUDE, AgentSessionProviderLoadState.LOADED)
      assertThat(project.providersWithUnknownThreadCount).containsExactly(AgentSessionProvider.CLAUDE)
    }
  }
}
