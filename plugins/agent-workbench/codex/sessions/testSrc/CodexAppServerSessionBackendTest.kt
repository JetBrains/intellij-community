// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexAppServerException
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActiveFlag
import com.intellij.agent.workbench.codex.common.CodexThreadSourceKind
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerSessionBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexAppServerSessionBackendTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun foldsSubAgentThreadsUnderParentAndArchivesOrphanOnlyOnce() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-hierarchy")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)
      val archiveCalls = ArrayList<String>()
      val attemptedArchiveIds = LinkedHashSet<String>()

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = {
          listOf(
            parentThread(id = "parent-1", cwd = cwd, updatedAt = 200L),
            subAgentThread(
              id = "child-1",
              cwd = cwd,
              parentThreadId = "parent-1",
              updatedAt = 220L,
              nickname = "Scout",
              role = "reviewer",
              activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT),
            ),
            subAgentThread(id = "orphan-1", cwd = cwd, parentThreadId = "missing-parent", updatedAt = 240L),
          )
        },
        archiveThread = { threadId -> archiveCalls.add(threadId) },
        orphanArchiveAttemptRecorder = attemptedArchiveIds::add,
      )

      val first = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(first).hasSize(1)
      val parent = first.single()
      assertThat(parent.thread.id).isEqualTo("parent-1")
      assertThat(parent.thread.subAgents).hasSize(1)
      assertThat(parent.thread.subAgents.single().id).isEqualTo("child-1")
      assertThat(parent.thread.subAgents.single().name).isEqualTo("Scout (reviewer)")
      assertThat(parent.activity).isEqualTo(CodexSessionActivity.READY)
      assertThat(parent.summaryActivity).isEqualTo(CodexSessionActivity.READY)
      assertThat(parent.requiresResponse).isFalse()
      assertThat(parent.subAgentActivitiesById).containsEntry("child-1", CodexSessionActivity.NEEDS_INPUT)
      assertThat(archiveCalls).containsExactly("orphan-1")

      val second = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(second).hasSize(1)
      assertThat(archiveCalls).containsExactly("orphan-1")
    }
  }

  @Test
  fun defaultRecorderArchivesOrphanOnlyOnceAcrossRefreshes() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-default-recorder")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)
      val archiveCalls = ArrayList<String>()

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = {
          listOf(
            parentThread(id = "parent-1", cwd = cwd, updatedAt = 200L),
            subAgentThread(id = "orphan-1", cwd = cwd, parentThreadId = "missing-parent", updatedAt = 240L),
          )
        },
        archiveThread = { threadId -> archiveCalls.add(threadId) },
      )

      backend.listThreads(path = projectDir.toString(), openProject = null)
      backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(archiveCalls).containsExactly("orphan-1")
    }
  }

  @Test
  fun refreshThreadsForSubAgentChildReturnsFoldedParentThread() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-child-refresh")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)
      val threadsById = mapOf(
        "parent-1" to parentThread(id = "parent-1", cwd = cwd, updatedAt = 200L),
        "child-1" to subAgentThread(
          id = "child-1",
          cwd = cwd,
          parentThreadId = "parent-1",
          updatedAt = 220L,
          activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT),
        ),
      )
      val readCalls = ArrayList<String>()
      val archiveCalls = ArrayList<String>()
      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = { emptyList() },
        readThread = { threadId ->
          readCalls.add(threadId)
          threadsById[threadId]
        },
        archiveThread = { threadId -> archiveCalls.add(threadId) },
      )

      val result = backend.refreshThreads(path = projectDir.toString(), threadIds = setOf("child-1"), openProject = null)

      assertThat(readCalls).containsExactly("child-1", "parent-1")
      assertThat(archiveCalls).isEmpty()
      assertThat(result?.isComplete).isFalse()
      val parent = result?.threads.orEmpty().single()
      assertThat(parent.thread.id).isEqualTo("parent-1")
      assertThat(parent.thread.subAgents.map { it.id }).containsExactly("child-1")
      assertThat(parent.activity).isEqualTo(CodexSessionActivity.READY)
      assertThat(parent.summaryActivity).isEqualTo(CodexSessionActivity.READY)
      assertThat(parent.requiresResponse).isFalse()
      assertThat(parent.subAgentActivitiesById).containsEntry("child-1", CodexSessionActivity.NEEDS_INPUT)
    }
  }

  @Test
  fun refreshThreadsSkipsPendingThreadIds() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-pending-refresh")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)
      val realThreadId = "00000000-0000-0000-0000-000000000001"
      val threadsById = mapOf(realThreadId to parentThread(id = realThreadId, cwd = cwd, updatedAt = 200L))
      val readCalls = ArrayList<String>()
      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = { emptyList() },
        readThread = { threadId ->
          readCalls.add(threadId)
          if (threadId.startsWith("new-")) {
            throw CodexAppServerException("invalid thread id: $threadId")
          }
          threadsById[threadId]
        },
        archiveThread = {},
      )

      val result = backend.refreshThreads(path = projectDir.toString(), threadIds = setOf("new-123", realThreadId), openProject = null)

      assertThat(readCalls).containsExactly(realThreadId)
      assertThat(result).isNotNull
      assertThat(result?.isComplete).isFalse()
      val thread = result?.threads.orEmpty().single()
      assertThat(thread.thread.id).isEqualTo(realThreadId)
    }
  }

  @Test
  fun refreshThreadsSkipsTransientThreadNotLoadedError() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-thread-loading")
      Files.createDirectories(projectDir)
      val readCalls = ArrayList<String>()
      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = { emptyList() },
        readThread = { threadId ->
          readCalls.add(threadId)
          throw CodexAppServerException("thread not loaded: $threadId")
        },
        archiveThread = {},
      )

      val result = backend.refreshThreads(path = projectDir.toString(), threadIds = setOf("thread-new"), openProject = null)

      assertThat(readCalls).containsExactly("thread-new")
      assertThat(result).isNotNull
      assertThat(result?.isComplete).isFalse()
      assertThat(result?.threads).isEmpty()
    }
  }

  @Test
  fun refreshThreadsRethrowsUnexpectedReadError() {
    val projectDir = tempDir.resolve("project-read-failure")
    Files.createDirectories(projectDir)
    val backend = CodexAppServerSessionBackend(
      listThreadsForProject = { emptyList() },
      readThread = { throw CodexAppServerException("boom") },
      archiveThread = {},
    )

    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        backend.refreshThreads(path = projectDir.toString(), threadIds = setOf("thread-failed"), openProject = null)
      }
    }
      .isInstanceOf(CodexAppServerException::class.java)
      .hasMessage("boom")
  }

  @Test
  fun listArchivedThreadsUsesArchivedFetcherAndDoesNotArchiveOrphans() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-archived")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)
      val activeFetchCalls = ArrayList<String>()
      val archivedFetchCalls = ArrayList<String>()
      val archiveCalls = ArrayList<String>()
      val attemptedArchiveIds = LinkedHashSet<String>()

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = { projectPath ->
          activeFetchCalls.add(projectPath.toString())
          emptyList()
        },
        listArchivedThreadsForProject = { projectPath ->
          archivedFetchCalls.add(normalizeRootPath(projectPath.invariantSeparatorsPathString))
          listOf(
            parentThread(id = "parent-archived", cwd = cwd, updatedAt = 300L).copy(archived = true),
            subAgentThread(
              id = "child-archived",
              cwd = cwd,
              parentThreadId = "parent-archived",
              updatedAt = 320L,
            ).copy(archived = true),
            subAgentThread(
              id = "orphan-archived",
              cwd = cwd,
              parentThreadId = "missing-parent",
              updatedAt = 330L,
              activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_APPROVAL),
            ).copy(archived = true),
          )
        },
        archiveThread = { threadId -> archiveCalls.add(threadId) },
        orphanArchiveAttemptRecorder = attemptedArchiveIds::add,
      )

      val threads = backend.listArchivedThreads(path = projectDir.toString(), openProject = null)

      assertThat(activeFetchCalls).isEmpty()
      assertThat(archivedFetchCalls).containsExactly(cwd)
      assertThat(archiveCalls).isEmpty()
      assertThat(attemptedArchiveIds).isEmpty()
      assertThat(threads.map { it.thread.id }).containsExactly("orphan-archived", "parent-archived")
      val orphan = threads.first()
      assertThat(orphan.thread.archived).isTrue()
      assertThat(orphan.thread.subAgents).isEmpty()
      assertThat(orphan.activity).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
      assertThat(orphan.requiresResponse).isTrue()
      val parent = threads[1]
      assertThat(parent.thread.archived).isTrue()
      assertThat(parent.thread.subAgents.map { it.id }).containsExactly("child-archived")
    }
  }

  @Test
  fun prefetchArchivesAtMostOneOrphanPerCall() {
    runBlocking(Dispatchers.Default) {
      val projectA = tempDir.resolve("project-a")
      val projectB = tempDir.resolve("project-b")
      Files.createDirectories(projectA)
      Files.createDirectories(projectB)
      val cwdA = normalizeRootPath(projectA.invariantSeparatorsPathString)
      val cwdB = normalizeRootPath(projectB.invariantSeparatorsPathString)
      val archiveCalls = ArrayList<String>()
      val attemptedArchiveIds = LinkedHashSet<String>()

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = { projectPath ->
          when (normalizeRootPath(projectPath.invariantSeparatorsPathString)) {
            cwdA -> listOf(
              parentThread(id = "parent-a", cwd = cwdA, updatedAt = 300L),
              subAgentThread(id = "orphan-a", cwd = cwdA, parentThreadId = "missing-a", updatedAt = 310L),
            )
            cwdB -> listOf(
              subAgentThread(id = "orphan-b", cwd = cwdB, parentThreadId = "missing-b", updatedAt = 320L),
            )
            else -> emptyList()
          }
        },
        archiveThread = { threadId -> archiveCalls.add(threadId) },
        orphanArchiveAttemptRecorder = attemptedArchiveIds::add,
      )

      val prefetched = backend.prefetchThreads(listOf(projectA.toString(), projectB.toString()))
      assertThat(prefetched[projectA.toString()].orEmpty().map { it.thread.id }).containsExactly("parent-a")
      assertThat(prefetched[projectB.toString()]).isEmpty()
      assertThat(archiveCalls).hasSize(1)
      assertThat(archiveCalls.single()).isEqualTo("orphan-b")
    }
  }

  @Test
  fun failedOrphanArchiveAttemptIsNotRetried() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-archive-failure")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)
      val archiveCalls = ArrayList<String>()
      val attemptedArchiveIds = LinkedHashSet<String>()

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = {
          listOf(
            parentThread(id = "parent-1", cwd = cwd, updatedAt = 200L),
            subAgentThread(id = "orphan-1", cwd = cwd, parentThreadId = "missing-parent", updatedAt = 240L),
          )
        },
        archiveThread = { threadId ->
          archiveCalls.add(threadId)
          error("archive failed")
        },
        orphanArchiveAttemptRecorder = attemptedArchiveIds::add,
      )

      backend.listThreads(path = projectDir.toString(), openProject = null)
      backend.listThreads(path = projectDir.toString(), openProject = null)

      assertThat(archiveCalls).containsExactly("orphan-1")
    }
  }

  @Test
  fun flatSubAgentSourcesWithoutParentAreNotAutoArchived() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-flat-subagent")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)
      val archiveCalls = ArrayList<String>()
      val attemptedArchiveIds = LinkedHashSet<String>()

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = {
          listOf(
            parentThread(id = "parent-1", cwd = cwd, updatedAt = 200L),
            subAgentThread(
              id = "review-1",
              cwd = cwd,
              parentThreadId = null,
              sourceKind = CodexThreadSourceKind.SUB_AGENT_REVIEW,
              updatedAt = 220L,
            ),
            subAgentThread(
              id = "compact-1",
              cwd = cwd,
              parentThreadId = null,
              sourceKind = CodexThreadSourceKind.SUB_AGENT_COMPACT,
              updatedAt = 230L,
            ),
          )
        },
        archiveThread = { threadId -> archiveCalls.add(threadId) },
        orphanArchiveAttemptRecorder = attemptedArchiveIds::add,
      )

      val threads = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(threads.map { it.thread.id }).containsExactly("compact-1", "review-1", "parent-1")
      val compactThread = threads.firstOrNull { it.thread.id == "compact-1" }
      val reviewThread = threads.firstOrNull { it.thread.id == "review-1" }
      assertNotNull(compactThread, "Expected compact-1 flat sub-agent thread to be present")
      assertNotNull(reviewThread, "Expected review-1 flat sub-agent thread to be present")
      assertThat(compactThread.summaryActivity).isNull()
      assertThat(reviewThread.summaryActivity).isNull()
      assertThat(archiveCalls).isEmpty()
    }
  }

  @Test
  fun prefetchDeduplicatesRepeatedProjectPathsByCwd() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-dedupe")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)
      val fetchCalls = ArrayList<String>()

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = { projectPath ->
          fetchCalls.add(normalizeRootPath(projectPath.invariantSeparatorsPathString))
          listOf(parentThread(id = "parent-1", cwd = cwd, updatedAt = 200L))
        },
        archiveThread = {},
        orphanArchiveAttemptRecorder = { true },
      )

      val prefetched = backend.prefetchThreads(listOf(projectDir.toString(), projectDir.toString()))
      assertThat(fetchCalls).containsExactly(cwd)
      assertThat(prefetched[projectDir.toString()].orEmpty().map { it.thread.id }).containsExactly("parent-1")
    }
  }

  @Test
  fun prefetchRecordsRolloutPathsForParentAndSubAgentThreads() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-rollout-path-index")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)
      val parentRolloutPath = "/tmp/codex/rollout-parent.jsonl"
      val childRolloutPath = "/tmp/codex/rollout-child.jsonl"
      val threadPathIndex = InMemoryCodexThreadPathIndex()

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = {
          listOf(
            parentThread(id = "parent-1", cwd = cwd, updatedAt = 200L, path = parentRolloutPath),
            subAgentThread(
              id = "child-1",
              cwd = cwd,
              parentThreadId = "parent-1",
              updatedAt = 220L,
              path = childRolloutPath,
            ),
          )
        },
        archiveThread = {},
        orphanArchiveAttemptRecorder = { true },
        threadPathIndex = threadPathIndex,
      )

      val prefetched = backend.prefetchThreads(listOf(projectDir.toString()))

      assertThat(prefetched[projectDir.toString()].orEmpty().map { it.thread.id }).containsExactly("parent-1")
      assertThat(threadPathIndex.entry("parent-1")?.rolloutPath).isEqualTo(parentRolloutPath)
      assertThat(threadPathIndex.entry("child-1")?.rolloutPath).isEqualTo(childRolloutPath)
      assertThat(threadPathIndex.entry("child-1")?.parentThreadId).isEqualTo("parent-1")
    }
  }

  @Test
  fun prefetchOmitsFailedCwdSoCallerCanFallback() {
    runBlocking(Dispatchers.Default) {
      val projectA = tempDir.resolve("project-prefetch-a")
      val projectB = tempDir.resolve("project-prefetch-b")
      Files.createDirectories(projectA)
      Files.createDirectories(projectB)
      val cwdA = normalizeRootPath(projectA.invariantSeparatorsPathString)
      val cwdB = normalizeRootPath(projectB.invariantSeparatorsPathString)

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = { projectPath ->
          when (normalizeRootPath(projectPath.invariantSeparatorsPathString)) {
            cwdA -> listOf(parentThread(id = "parent-a", cwd = cwdA, updatedAt = 300L))
            cwdB -> error("failed to fetch cwdB")
            else -> emptyList()
          }
        },
        archiveThread = {},
        orphanArchiveAttemptRecorder = { true },
      )

      val prefetched = backend.prefetchThreads(listOf(projectA.toString(), projectB.toString()))
      assertThat(prefetched.keys).containsExactly(projectA.toString())
      assertThat(prefetched[projectA.toString()].orEmpty().map { it.thread.id }).containsExactly("parent-a")
      assertThat(prefetched).doesNotContainKey(projectB.toString())
    }
  }

  @Test
  fun mapsStatusKindsAndFlagsToExpectedSessionActivity() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-status-mapping")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = {
          listOf(
            parentThread(id = "ready-idle", cwd = cwd, updatedAt = 100L, statusKind = CodexThreadStatusKind.IDLE),
            parentThread(id = "ready-system-error", cwd = cwd, updatedAt = 110L, statusKind = CodexThreadStatusKind.SYSTEM_ERROR),
            parentThread(id = "ready-unknown", cwd = cwd, updatedAt = 120L, statusKind = CodexThreadStatusKind.UNKNOWN),
            parentThread(id = "processing-active", cwd = cwd, updatedAt = 130L, statusKind = CodexThreadStatusKind.ACTIVE),
            parentThread(
              id = "needs-input-approval",
              cwd = cwd,
              updatedAt = 140L,
              statusKind = CodexThreadStatusKind.ACTIVE,
              activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_APPROVAL),
            ),
            parentThread(
              id = "needs-input-user-input",
              cwd = cwd,
              updatedAt = 150L,
              statusKind = CodexThreadStatusKind.ACTIVE,
              activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT),
            ),
          )
        },
        archiveThread = {},
      )

      val byId = backend.listThreads(path = projectDir.toString(), openProject = null)
        .associateBy { it.thread.id }

      assertThat(byId.getValue("ready-idle").activity).isEqualTo(CodexSessionActivity.READY)
      assertThat(byId.getValue("ready-system-error").activity).isEqualTo(CodexSessionActivity.READY)
      assertThat(byId.getValue("ready-unknown").activity).isEqualTo(CodexSessionActivity.READY)
      assertThat(byId.getValue("processing-active").activity).isEqualTo(CodexSessionActivity.PROCESSING)
      assertThat(byId.getValue("needs-input-approval").activity).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
      assertThat(byId.getValue("needs-input-user-input").activity).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
    }
  }

  @Test
  fun foldedSubAgentActivityDoesNotContributeToParentActivity() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-sub-agent-tree-only-activity")
      Files.createDirectories(projectDir)
      val cwd = normalizeRootPath(projectDir.invariantSeparatorsPathString)

      val backend = CodexAppServerSessionBackend(
        listThreadsForProject = {
          listOf(
            parentThread(id = "parent-ready", cwd = cwd, updatedAt = 200L),
            subAgentThread(
              id = "child-processing",
              cwd = cwd,
              parentThreadId = "parent-ready",
              updatedAt = 210L,
              activeFlags = emptyList(),
            ),
            subAgentThread(
              id = "child-approval",
              cwd = cwd,
              parentThreadId = "parent-ready",
              updatedAt = 220L,
              activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_APPROVAL),
            ),
          )
        },
        archiveThread = {},
      )

      val byId = backend.listThreads(path = projectDir.toString(), openProject = null)
        .associateBy { it.thread.id }

      assertThat(byId.keys).containsExactly("parent-ready")
      val parent = byId.getValue("parent-ready")
      assertThat(parent.activity).isEqualTo(CodexSessionActivity.READY)
      assertThat(parent.summaryActivity).isEqualTo(CodexSessionActivity.READY)
      assertThat(parent.requiresResponse).isFalse()
      assertThat(parent.thread.subAgents.map { it.id }).containsExactly("child-approval", "child-processing")
      assertThat(parent.subAgentActivitiesById).containsEntry("child-processing", CodexSessionActivity.PROCESSING)
      assertThat(parent.subAgentActivitiesById).containsEntry("child-approval", CodexSessionActivity.NEEDS_INPUT)
    }
  }
}

private fun parentThread(
  id: String,
  cwd: String,
  updatedAt: Long,
  path: String? = null,
  statusKind: CodexThreadStatusKind = CodexThreadStatusKind.IDLE,
  activeFlags: List<CodexThreadActiveFlag> = emptyList(),
): CodexThread {
  return CodexThread(
    id = id,
    title = id,
    updatedAt = updatedAt,
    archived = false,
    cwd = cwd,
    path = path,
    sourceKind = CodexThreadSourceKind.CLI,
    statusKind = statusKind,
    activeFlags = activeFlags,
  )
}

private fun subAgentThread(
  id: String,
  cwd: String,
  parentThreadId: String?,
  sourceKind: CodexThreadSourceKind = CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN,
  updatedAt: Long,
  path: String? = null,
  nickname: String? = null,
  role: String? = null,
  activeFlags: List<CodexThreadActiveFlag> = emptyList(),
): CodexThread {
  return CodexThread(
    id = id,
    title = id,
    updatedAt = updatedAt,
    archived = false,
    cwd = cwd,
    path = path,
    sourceKind = sourceKind,
    parentThreadId = parentThreadId,
    agentNickname = nickname,
    agentRole = role,
    statusKind = CodexThreadStatusKind.ACTIVE,
    activeFlags = activeFlags,
  )
}
