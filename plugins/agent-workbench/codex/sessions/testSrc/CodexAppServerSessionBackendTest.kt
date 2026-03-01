// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

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
      assertThat(parent.activity).isEqualTo(CodexSessionActivity.UNREAD)
      assertThat(archiveCalls).containsExactly("orphan-1")

      val second = backend.listThreads(path = projectDir.toString(), openProject = null)
      assertThat(second).hasSize(1)
      assertThat(archiveCalls).containsExactly("orphan-1")
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
}

private fun parentThread(
  id: String,
  cwd: String,
  updatedAt: Long,
): CodexThread {
  return CodexThread(
    id = id,
    title = id,
    updatedAt = updatedAt,
    archived = false,
    cwd = cwd,
    sourceKind = CodexThreadSourceKind.CLI,
    statusKind = CodexThreadStatusKind.IDLE,
  )
}

private fun subAgentThread(
  id: String,
  cwd: String,
  parentThreadId: String?,
  sourceKind: CodexThreadSourceKind = CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN,
  updatedAt: Long,
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
    sourceKind = sourceKind,
    parentThreadId = parentThreadId,
    agentNickname = nickname,
    agentRole = role,
    statusKind = CodexThreadStatusKind.ACTIVE,
    activeFlags = activeFlags,
  )
}
