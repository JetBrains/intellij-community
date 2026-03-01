// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentSessionsServiceConcurrencyIntegrationTest {
  @Test
  fun refreshCoalescesConcurrentRequestsAndRunsFollowUpRefresh() = runBlocking(Dispatchers.Default) {
    val openInvocationCount = AtomicInteger(0)
    val firstRefreshStarted = CompletableDeferred<Unit>()
    val firstRefreshRelease = CompletableDeferred<Unit>()
    val secondRefreshObserved = CompletableDeferred<Unit>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                when (openInvocationCount.incrementAndGet()) {
                  1 -> {
                    firstRefreshStarted.complete(Unit)
                    firstRefreshRelease.await()
                  }
                  2 -> {
                    secondRefreshObserved.complete(Unit)
                  }
                }
                listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
              }
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      firstRefreshStarted.await()
      service.refresh()
      firstRefreshRelease.complete(Unit)

      waitForCondition {
        secondRefreshObserved.isCompleted
      }

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true &&
        openInvocationCount.get() == 2
      }

      assertThat(openInvocationCount.get()).isEqualTo(2)
    }
  }
}
