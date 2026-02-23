// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentSessionsServiceArchiveIntegrationTest {
  @Test
  fun archiveThreadsArchivesAllSupportedTargetsAndSkipsUnsupportedOnes() = runBlocking {
    val codexThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 300, provider = AgentSessionProvider.CODEX),
    )
    val claudeThreads = mutableListOf(
      thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE),
    )
    val codexSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) codexThreads.toList() else emptyList()
      },
    )
    val claudeSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) claudeThreads.toList() else emptyList()
      },
    )
    val codexBridge = testCodexBridge(
      sessionSource = codexSource,
      onArchive = { _, threadId ->
        codexThreads.removeIf { it.id == threadId }
        true
      },
    )
    val claudeBridge = object : AgentSessionProviderBridge {
      override val provider: AgentSessionProvider
        get() = AgentSessionProvider.CLAUDE
      override val displayNameKey: String
        get() = "toolwindow.provider.claude"
      override val newSessionLabelKey: String
        get() = "toolwindow.action.new.session.claude"
      override val iconId: String
        get() = AgentSessionProviderIconIds.CLAUDE
      override val sessionSource: AgentSessionSource = claudeSource
      override val cliMissingMessageKey: String
        get() = "toolwindow.error.claude.cli"
      override fun isCliAvailable(): Boolean = true
      override fun buildResumeCommand(sessionId: String): List<String> = listOf("claude", "--resume", sessionId)
      override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> = listOf("claude")
      override fun buildNewEntryCommand(): List<String> = listOf("claude")
      override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
        return AgentSessionLaunchSpec(sessionId = null, command = listOf("claude"))
      }
    }

    AgentSessionProviderBridges.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(codexBridge, claudeBridge))) {
      runBlocking {
        withService(
          sessionSourcesProvider = { listOf(codexSource, claudeSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service ->
          service.refresh()
          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            threads.any { it.id == "codex-1" } && threads.any { it.id == "claude-1" }
          }

          val threads = service.state.value.projects.first().threads
          val codexTarget = threads.first { it.id == "codex-1" }
          val claudeTarget = threads.first { it.id == "claude-1" }
          service.archiveThreads(
            listOf(
              ArchiveThreadTarget(path = PROJECT_PATH, thread = codexTarget),
              ArchiveThreadTarget(path = PROJECT_PATH, thread = claudeTarget),
            )
          )

          waitForCondition {
            val currentThreads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            currentThreads.none { it.id == "codex-1" } && currentThreads.any { it.id == "claude-1" }
          }
        }
      }
    }
  }

  @Test
  fun archiveThreadRemovesThreadAndRefreshesState() = runBlocking {
    val cleanupCalls = mutableListOf<Pair<String, String>>()
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        sourceThreads.removeIf { it.id == threadId }
        true
      },
    )

    AgentSessionProviderBridges.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking {
        withService(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { projectPath, threadIdentity ->
            cleanupCalls.add(projectPath to threadIdentity)
          },
        ) { service ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }

          val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
          service.archiveThread(PROJECT_PATH, threadToArchive)

          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            threads.none { it.id == "codex-1" } && threads.any { it.id == "codex-2" }
          }

          assertThat(cleanupCalls)
            .containsExactly(PROJECT_PATH to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-1"))
        }
      }
    }
  }

  @Test
  fun archiveThreadKeepsThreadHiddenWhenRefreshReturnsStaleData() = runBlocking {
    val cleanupCalls = mutableListOf<Pair<String, String>>()
    val listCalls = AtomicInteger(0)
    val staleSourceThreads = listOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listCalls.incrementAndGet()
          staleSourceThreads
        }
        else {
          emptyList()
        }
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, _ -> true },
    )

    AgentSessionProviderBridges.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking {
        withService(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { projectPath, threadIdentity ->
            cleanupCalls.add(projectPath to threadIdentity)
          },
        ) { service ->
          service.refresh()
          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            listCalls.get() > 0 && threads.any { it.id == "codex-1" }
          }

          val callsBeforeArchive = listCalls.get()
          val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
          service.archiveThread(PROJECT_PATH, threadToArchive)

          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            threads.none { it.id == "codex-1" } && threads.any { it.id == "codex-2" }
          }

          waitForCondition(timeoutMs = 6_000) {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            listCalls.get() > callsBeforeArchive && threads.none { it.id == "codex-1" } && threads.any { it.id == "codex-2" }
          }

          assertThat(cleanupCalls)
            .containsExactly(PROJECT_PATH to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-1"))
        }
      }
    }
  }

  @Test
  fun archiveThreadDoesNotCleanupChatMetadataWhenArchiveFails() = runBlocking {
    val cleanupCalls = mutableListOf<Pair<String, String>>()
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, _ -> false },
    )

    AgentSessionProviderBridges.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking {
        withService(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { projectPath, threadIdentity ->
            cleanupCalls.add(projectPath to threadIdentity)
          },
        ) { service ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }

          val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
          service.archiveThread(PROJECT_PATH, threadToArchive)

          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            threads.any { it.id == "codex-1" } && threads.any { it.id == "codex-2" }
          }

          assertThat(cleanupCalls).isEmpty()
        }
      }
    }
  }

  @Test
  fun archiveThreadRefreshesAndRemovesThreadWhenCleanupFails() = runBlocking {
    val listCalls = AtomicInteger(0)
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listCalls.incrementAndGet()
          sourceThreads.toList()
        }
        else {
          emptyList()
        }
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        sourceThreads.removeIf { it.id == threadId }
        true
      },
    )

    AgentSessionProviderBridges.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking {
        withService(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { _, _ -> error("cleanup failed") },
        ) { service ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }

          val callsBeforeArchive = listCalls.get()
          val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
          service.archiveThread(PROJECT_PATH, threadToArchive)

          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            threads.none { it.id == "codex-1" } && threads.any { it.id == "codex-2" }
          }

          waitForCondition(timeoutMs = 6_000) {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            listCalls.get() > callsBeforeArchive && threads.none { it.id == "codex-1" }
          }
        }
      }
    }
  }

  @Test
  fun unarchiveThreadsRestoresArchivedCodexThread() = runBlocking {
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        sourceThreads.removeIf { it.id == threadId }
        true
      },
      onUnarchive = { _, threadId ->
        if (sourceThreads.none { it.id == threadId }) {
          sourceThreads.add(thread(id = threadId, updatedAt = 250, provider = AgentSessionProvider.CODEX))
        }
        true
      },
    )

    AgentSessionProviderBridges.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking {
        withService(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }

          val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
          val target = ArchiveThreadTarget(path = PROJECT_PATH, thread = threadToArchive)
          service.archiveThreads(listOf(target))
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.none { it.id == "codex-1" } == true
          }

          service.unarchiveThreads(listOf(target))
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }
        }
      }
    }
  }

}

private fun testCodexBridge(
  sessionSource: AgentSessionSource,
  onArchive: suspend (path: String, threadId: String) -> Boolean,
  onUnarchive: (suspend (path: String, threadId: String) -> Boolean)? = null,
): AgentSessionProviderBridge {
  return object : AgentSessionProviderBridge {
    override val provider: AgentSessionProvider
      get() = AgentSessionProvider.CODEX

    override val displayNameKey: String
      get() = "toolwindow.provider.codex"

    override val newSessionLabelKey: String
      get() = "toolwindow.action.new.session.codex"

    override val iconId: String
      get() = AgentSessionProviderIconIds.CODEX

    override val sessionSource: AgentSessionSource = sessionSource

    override val cliMissingMessageKey: String
      get() = "toolwindow.error.cli"

    override val supportsArchiveThread: Boolean
      get() = true

    override val supportsUnarchiveThread: Boolean
      get() = onUnarchive != null

    override fun isCliAvailable(): Boolean = true

    override fun buildResumeCommand(sessionId: String): List<String> = listOf("codex", "resume", sessionId)

    override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> = listOf("codex")

    override fun buildNewEntryCommand(): List<String> = listOf("codex")

    override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
      return AgentSessionLaunchSpec(sessionId = null, command = listOf("codex"))
    }

    override suspend fun archiveThread(path: String, threadId: String): Boolean {
      return onArchive(path, threadId)
    }

    override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
      return onUnarchive?.invoke(path, threadId) ?: false
    }
  }
}
