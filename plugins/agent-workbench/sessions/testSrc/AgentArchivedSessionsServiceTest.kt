// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.service.AgentArchivedSessionsService
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@TestApplication
class AgentArchivedSessionsServiceTest {
  @Test
  fun refreshIfLoadedQueuesRefreshWhileInitialLoadIsRunning() {
    runBlocking(Dispatchers.Default) {
      val job = SupervisorJob()
      val scope = CoroutineScope(job + Dispatchers.Default)
      val archivedThreads = CopyOnWriteArrayList<AgentSessionThread>()
      val listCalls = AtomicInteger(0)
      val firstLoadStarted = CompletableDeferred<Unit>()
      val releaseFirstLoad = CompletableDeferred<Unit>()
      val provider = AgentSessionProvider.from("test-archived")
      val sessionSource = ScriptedSessionSource(
        provider = provider,
        supportsArchivedThreads = true,
        listArchivedFromOpenProject = { path, _ ->
          if (path != PROJECT_PATH) {
            emptyList()
          }
          else {
            val snapshot = archivedThreads.toList()
            if (listCalls.incrementAndGet() == 1) {
              firstLoadStarted.complete(Unit)
              releaseFirstLoad.await()
            }
            snapshot
          }
        },
      )
      val service = AgentArchivedSessionsService(
        serviceScope = scope,
        sessionSourcesProvider = { listOf(sessionSource) },
        projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
      )

      try {
        service.ensureLoaded()
        withTimeout(6.seconds) {
          firstLoadStarted.await()
        }

        archivedThreads.add(thread(id = "codex-1", updatedAt = 100, provider = provider).copy(archived = true))
        service.refreshIfLoaded()
        releaseFirstLoad.complete(Unit)

        waitForCondition(timeoutMs = 6_000) {
          listCalls.get() >= 2 && archivedThreadIds(service) == listOf("codex-1")
        }

        assertThat(listCalls.get()).isGreaterThanOrEqualTo(2)
        assertThat(archivedThreadIds(service)).containsExactly("codex-1")
      }
      finally {
        job.cancelAndJoin()
      }
    }
  }
}

private fun archivedThreadIds(service: AgentArchivedSessionsService): List<String> {
  return service.snapshot().projects.firstOrNull()?.threads.orEmpty().map { thread -> thread.id }
}
