// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentSessionsServiceConcurrencyIntegrationTest {
  @Test
  fun refreshIgnoresConcurrentRequestWhileRefreshInProgress() = runBlocking {
    val openInvocationCount = AtomicInteger(0)
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

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
                openInvocationCount.incrementAndGet()
                started.complete(Unit)
                release.await()
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
      started.await()
      service.refresh()
      release.complete(Unit)

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      assertThat(openInvocationCount.get()).isEqualTo(1)
    }
  }
}
