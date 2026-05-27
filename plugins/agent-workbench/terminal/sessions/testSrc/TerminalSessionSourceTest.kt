// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.terminal.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class TerminalSessionSourceTest {
  @Test
  fun `lists active and archived terminal sessions for matching project path`() {
    runBlocking(Dispatchers.Default) {
      val stateService = TerminalSessionStateService()
      val source = TerminalSessionSource(stateService)
      stateService.recordSession(path = "/tmp/project/", threadId = "first", title = "First", createdAtMs = 1000L)
      stateService.recordSession(path = "/tmp/project", threadId = "second", title = "Second", createdAtMs = 2000L)
      stateService.recordSession(path = "/tmp/other", threadId = "other", title = "Other", createdAtMs = 3000L)
      stateService.archiveSession(path = "/tmp/project", threadId = "first")

      val active = source.listThreadsFromClosedProject("/tmp/project")
      val archived = source.listArchivedThreadsFromClosedProject("/tmp/project/")

      assertThat(active.map { thread -> thread.id }).containsExactly("second")
      assertThat(active.single().provider).isEqualTo(AgentSessionProvider.TERMINAL)
      assertThat(active.single().archived).isFalse()
      assertThat(archived.map { thread -> thread.id }).containsExactly("first")
      assertThat(archived.single().archived).isTrue()
    }
  }

  @Test
  fun `emits scoped thread update when session changes`() {
    runBlocking(Dispatchers.Default) {
      val stateService = TerminalSessionStateService()
      val event = async(start = CoroutineStart.UNDISPATCHED) { stateService.updateEvents.first() }

      stateService.recordSession(path = "/tmp/project/", threadId = "terminal-session-id", title = "Terminal", createdAtMs = 1000L)

      val update = event.await()
      assertThat(update.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
      assertThat(update.scopedPaths).containsExactly("/tmp/project")
      assertThat(update.threadIds).containsExactly("terminal-session-id")
    }
  }

  @Test
  fun `persists working directory restore context`() {
    runBlocking(Dispatchers.Default) {
      val stateService = TerminalSessionStateService()
      stateService.recordSession(path = "/tmp/project/", threadId = "terminal-session-id", title = "Terminal", createdAtMs = 1000L)

      stateService.recordWorkingDirectory(
        path = "/tmp/project",
        threadId = "terminal-session-id",
        workingDirectory = "/tmp/project/src/nested",
      )

      val context = checkNotNull(stateService.readRestoreContext(path = "/tmp/project/", threadId = "terminal-session-id"))
      assertThat(context.workingDirectory).isEqualTo("/tmp/project/src/nested")
    }
  }

  @Test
  fun `preserves Windows drive roots when normalizing project paths and working directories`() {
    runBlocking(Dispatchers.Default) {
      val stateService = TerminalSessionStateService()
      val source = TerminalSessionSource(stateService)

      stateService.recordSession(path = "C:\\", threadId = "drive-root", title = "Drive root", createdAtMs = 1000L)
      stateService.recordWorkingDirectory(path = "C:/", threadId = "drive-root", workingDirectory = "C:\\")

      val threadsFromSlashPath = source.listThreadsFromClosedProject("C:/")
      val threadsFromBackslashPath = source.listThreadsFromClosedProject("C:\\")
      val context = checkNotNull(stateService.readRestoreContext(path = "C:/", threadId = "drive-root"))
      assertThat(threadsFromSlashPath.map { thread -> thread.id }).containsExactly("drive-root")
      assertThat(threadsFromBackslashPath.map { thread -> thread.id }).containsExactly("drive-root")
      assertThat(context.workingDirectory).isEqualTo("C:/")
    }
  }

  @Test
  fun `working directory snapshots do not update session recency`() {
    runBlocking(Dispatchers.Default) {
      val stateService = TerminalSessionStateService()
      stateService.recordSession(path = "/tmp/project", threadId = "first", title = "First", createdAtMs = 1000L)
      stateService.recordSession(path = "/tmp/project", threadId = "second", title = "Second", createdAtMs = 2000L)

      assertThat(
        stateService.recordWorkingDirectory(
          path = "/tmp/project",
          threadId = "first",
          workingDirectory = "/tmp/project/app",
        )
      ).isTrue()
      val stateAfterFirstSnapshot = stateService.state

      assertThat(
        stateService.recordWorkingDirectory(
          path = "/tmp/project/",
          threadId = "first",
          workingDirectory = "/tmp/project/app/",
        )
      ).isTrue()

      val activeThreads = stateService.listSessions(path = "/tmp/project", archived = false)
      val firstThread = activeThreads.single { thread -> thread.id == "first" }
      val context = checkNotNull(stateService.readRestoreContext(path = "/tmp/project", threadId = "first"))
      assertThat(stateService.state).isEqualTo(stateAfterFirstSnapshot)
      assertThat(activeThreads.map { thread -> thread.id }).containsExactly("second", "first")
      assertThat(firstThread.updatedAt).isEqualTo(1000L)
      assertThat(context.workingDirectory).isEqualTo("/tmp/project/app")
    }
  }
}
