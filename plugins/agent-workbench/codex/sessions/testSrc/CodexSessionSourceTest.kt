// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodexSessionSourceTest {
  @Test
  fun markThreadAsReadSuppressesStaleUnreadRefreshHints() {
    val source = createSource(
      appServerHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(activity = AgentThreadActivity.UNREAD, updatedAt = 100L)
        )
      )
    )

    runBlocking(Dispatchers.Default) {
      source.markThreadAsRead(threadId = "thread-1", updatedAt = 100L)

      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        knownThreadIdsByPath = mapOf(PROJECT_PATH to setOf("thread-1")),
      )

      assertThat(hints).isEmpty()
    }
  }

  @Test
  fun responseRequiredUnreadRefreshHintsSurviveMarkAsRead() {
    val source = createSource(
      appServerHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.UNREAD,
            updatedAt = 100L,
            responseRequired = true,
          )
        )
      )
    )

    runBlocking(Dispatchers.Default) {
      source.markThreadAsRead(threadId = "thread-1", updatedAt = 100L)

      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        knownThreadIdsByPath = mapOf(PROJECT_PATH to setOf("thread-1")),
      )

      assertThat(hints.getValue(PROJECT_PATH).activityByThreadId)
        .containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.UNREAD))
    }
  }

  @Test
  fun activeThreadSuppressesCurrentUnreadOutputHints() {
    val source = createSource(
      appServerHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(activity = AgentThreadActivity.UNREAD, updatedAt = 100L)
        )
      )
    )

    runBlocking(Dispatchers.Default) {
      source.setActiveThreadId("thread-1")

      val whileActive = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        knownThreadIdsByPath = mapOf(PROJECT_PATH to setOf("thread-1")),
      )
      assertThat(whileActive).isEmpty()

      source.setActiveThreadId(null)

      val afterDeactivation = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        knownThreadIdsByPath = mapOf(PROJECT_PATH to setOf("thread-1")),
      )
      assertThat(afterDeactivation).isEmpty()
    }
  }

  @Test
  fun activeThreadAutoAdvancesTrackerFromListedThreads() {
    val source = createSource(
      backendThreads = listOf(
        CodexBackendThread(
          thread = CodexThread(
            id = "thread-1",
            title = "thread-1",
            updatedAt = 200L,
            archived = false,
          )
        )
      ),
      appServerHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(activity = AgentThreadActivity.UNREAD, updatedAt = 200L)
        )
      )
    )

    runBlocking(Dispatchers.Default) {
      source.setActiveThreadId("thread-1")
      source.listThreadsFromClosedProject(PROJECT_PATH)

      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        knownThreadIdsByPath = mapOf(PROJECT_PATH to setOf("thread-1")),
      )

      assertThat(hints).isEmpty()
    }
  }
}

private const val PROJECT_PATH = "/work/project"

private fun createSource(
  backendThreads: List<CodexBackendThread> = emptyList(),
  appServerHints: Map<String, CodexRefreshHints> = emptyMap(),
  rolloutHints: Map<String, CodexRefreshHints> = emptyMap(),
): CodexSessionSource {
  return CodexSessionSource(
    backend = object : CodexSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> = backendThreads
    },
    appServerRefreshHintsProvider = staticHintsProvider(appServerHints),
    rolloutRefreshHintsProvider = staticHintsProvider(rolloutHints),
  )
}

private fun staticHintsProvider(hintsByPath: Map<String, CodexRefreshHints>): CodexRefreshHintsProvider {
  return object : CodexRefreshHintsProvider {
    override val updates = emptyFlow<Unit>()

    override suspend fun prefetchRefreshHints(
      paths: List<String>,
      knownThreadIdsByPath: Map<String, Set<String>>,
    ): Map<String, CodexRefreshHints> {
      return hintsByPath.filterKeys(paths::contains)
    }
  }
}

private fun refreshHints(vararg entries: Pair<String, CodexRefreshActivityHint>): CodexRefreshHints {
  return CodexRefreshHints(activityHintsByThreadId = linkedMapOf(*entries))
}

private fun refreshHint(
  activity: AgentThreadActivity,
  updatedAt: Long,
  responseRequired: Boolean = false,
): CodexRefreshActivityHint {
  return CodexRefreshActivityHint(
    activity = activity,
    updatedAt = updatedAt,
    responseRequired = responseRequired,
  )
}
