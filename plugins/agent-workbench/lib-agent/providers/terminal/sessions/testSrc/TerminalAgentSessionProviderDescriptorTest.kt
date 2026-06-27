// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.terminal.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class TerminalAgentSessionProviderDescriptorTest {
  @Test
  fun `creates default shell launch spec with preallocated id`() {
    runBlocking(Dispatchers.Default) {
      val descriptor = TerminalAgentSessionProviderDescriptor(
        stateService = TerminalSessionStateService(),
        sessionIdGenerator = { "terminal-session-id" },
      )

      val launchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

      assertThat(TERMINAL_AGENT_SESSION_PROVIDER.value).isEqualTo(AgentSessionProvider.from("terminal").value)
      assertThat(descriptor.supportedLaunchModes).containsExactly(AgentSessionLaunchMode.STANDARD)
      assertThat(descriptor.icon).isNotNull
      assertThat(descriptor.isCliAvailable()).isTrue()
      assertThat(descriptor.supportsArchiveThread).isTrue()
      assertThat(descriptor.archiveOnLastEditorClose).isTrue()
      assertThat(launchSpec.command).isEmpty()
      assertThat(launchSpec.useTerminalDefaultShell).isTrue()
      assertThat(launchSpec.preallocatedSessionId).isEqualTo("terminal-session-id")
    }
  }

  @Test
  fun `records renames archives and unarchives terminal sessions`() {
    runBlocking(Dispatchers.Default) {
      val stateService = TerminalSessionStateService()
      val descriptor = TerminalAgentSessionProviderDescriptor(stateService = stateService)
      val renameAction = checkNotNull(descriptor.threadRenameAction)

      descriptor.recordNewSession(
        path = "/tmp/project/",
        threadId = "terminal-session-id",
        title = "Terminal",
        createdAtMs = 1000L,
      )
      val renamed = renameAction("/tmp/project", "terminal-session-id", "Build shell")
      val archived = descriptor.archiveThread("/tmp/project", "terminal-session-id")
      val unarchived = descriptor.unarchiveThread("/tmp/project", "terminal-session-id")

      val thread = stateService.listSessions(path = "/tmp/project", archived = false).single()
      assertThat(renamed).isTrue()
      assertThat(archived).isTrue()
      assertThat(unarchived).isTrue()
      assertThat(thread.title).isEqualTo("Build shell")
      assertThat(thread.archived).isFalse()
      assertThat(thread.provider.value).isEqualTo(AgentSessionProvider.from("terminal").value)
    }
  }

  @Test
  fun `records terminal working directory restore context through provider descriptor`() {
    runBlocking(Dispatchers.Default) {
      val stateService = TerminalSessionStateService()
      val descriptor = TerminalAgentSessionProviderDescriptor(stateService = stateService)

      descriptor.recordNewSession(
        path = "/tmp/project/",
        threadId = "terminal-session-id",
        title = "Terminal",
        createdAtMs = 1000L,
      )
      descriptor.recordTerminalWorkingDirectory(
        path = "/tmp/project",
        threadId = "terminal-session-id",
        workingDirectory = "/tmp/project/app",
      )

      val context = checkNotNull(descriptor.readTerminalRestoreContext(path = "/tmp/project", threadId = "terminal-session-id"))
      assertThat(descriptor.supportsTerminalRestoreContext).isTrue()
      assertThat(context.workingDirectory).isEqualTo("/tmp/project/app")
    }
  }

  @Test
  fun `launch contributor restores terminal working directory on resume`() {
    runBlocking(Dispatchers.Default) {
      val stateService = TerminalSessionStateService()
      val contributor = TerminalSessionLaunchContributor(stateService)
      stateService.recordSession(path = "/tmp/project", threadId = "terminal-session-id", title = "Terminal", createdAtMs = 1000L)
      stateService.recordWorkingDirectory(path = "/tmp/project", threadId = "terminal-session-id", workingDirectory = "/tmp/project/app")

      val launchSpec = contributor.contribute(
        projectPath = "/tmp/project",
        provider = AgentSessionProvider.from("terminal"),
        sessionId = "terminal-session-id",
        launchSpec = TerminalAgentSessionProviderDescriptor(stateService = stateService).buildResumeLaunchSpec("terminal-session-id"),
      )

      assertThat(launchSpec.useTerminalDefaultShell).isTrue()
      assertThat(launchSpec.workingDirectory).isEqualTo("/tmp/project/app")
    }
  }
}
