// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.service.AgentSessionsToolWindowVisibilityService
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.ui.publishAgentSessionsToolWindowVisibility
import com.intellij.agent.workbench.sessions.toolwindow.ui.sessionTreeModelShouldMarkCostHintEligible
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsToolWindowPanelTest {
  @Test
  fun publishToolWindowVisibilityUpdatesModelStateAndHydrationVisibility() {
    val visibilityService = service<AgentSessionsToolWindowVisibilityService>()
    assertThat(visibilityService.visibleFlow.value).isFalse()

    var modelUpdatesVisible: Boolean? = null
    publishAgentSessionsToolWindowVisibility(
      visible = true,
      token = "test-token",
      setModelUpdatesVisible = { modelUpdatesVisible = it },
      visibilityService = visibilityService,
    )

    assertThat(modelUpdatesVisible).isTrue()
    assertThat(visibilityService.visibleFlow.value).isTrue()

    publishAgentSessionsToolWindowVisibility(
      visible = false,
      token = "test-token",
      setModelUpdatesVisible = { modelUpdatesVisible = it },
      visibilityService = visibilityService,
    )

    assertThat(modelUpdatesVisible).isFalse()
    assertThat(visibilityService.visibleFlow.value).isFalse()
  }

  @Test
  fun costHintEligibilityTreatsThreadRowsWithoutCostAsEligible() {
    val model = buildSessionTreeModel(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          threads = listOf(
            AgentSessionThread(
              id = "thread-1",
              title = "Thread 1",
              updatedAt = 100,
              archived = false,
              provider = AgentSessionProvider.CODEX,
            )
          ),
        )
      ),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = mapOf("/work/project-a" to 10),
      treeUiState = InMemorySessionTreeUiState(),
    )

    assertThat(sessionTreeModelShouldMarkCostHintEligible(model)).isTrue()
    assertThat(sessionTreeModelShouldMarkCostHintEligible(SessionTreeModel.EMPTY)).isFalse()
  }
}
