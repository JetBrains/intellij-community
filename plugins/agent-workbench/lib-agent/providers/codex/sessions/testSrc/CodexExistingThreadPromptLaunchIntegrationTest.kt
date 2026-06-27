// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.common.CodexCliUtils
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.assertExistingThreadLaunchUsesPostStartDispatch
import com.intellij.agent.workbench.sessions.existingThreadPromptLaunchRequest
import com.intellij.agent.workbench.sessions.thread
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.withProvider
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexExistingThreadPromptLaunchIntegrationTest {
  @Test
  fun existingThreadStandardPromptUsesPostStartDispatch() {
    assertExistingThreadLaunchUsesPostStartDispatch(
      descriptor = descriptor(),
      request = existingThreadPromptLaunchRequest(
        provider = AgentSessionProvider.from("codex"),
        projectPath = PROJECT_PATH,
        threadId = EXISTING_THREAD_ID,
      ),
      projectPath = PROJECT_PATH,
      threadId = EXISTING_THREAD_ID,
    )
  }

  @Test
  fun existingThreadPlanModePromptUsesPostStartDispatch() {
    val startupBackend = RecordingThreadStartupBackend()
    val request = existingThreadPromptLaunchRequest(
      provider = AgentSessionProvider.from("codex"),
      projectPath = PROJECT_PATH,
      threadId = EXISTING_THREAD_ID,
      planMode = true,
    )

    assertExistingThreadLaunchUsesPostStartDispatch(
      descriptor = descriptor(startupBackend),
      request = request,
      projectPath = PROJECT_PATH,
      threadId = EXISTING_THREAD_ID,
    )
    assertThat(startupBackend.requests).isEmpty()
    assertThat(startupBackend.turnRequests).isEmpty()
  }
}

private fun descriptor(
  threadStartupBackend: CodexThreadStartupBackend = RecordingThreadStartupBackend(),
): AgentSessionProviderDescriptor {
  return CodexAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.from("codex"),
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listOf(thread(id = EXISTING_THREAD_ID, updatedAt = 200, provider = AgentSessionProvider.from("codex")))
        }
        else {
          emptyList()
        }
      },
    ),
    threadStartupBackend = threadStartupBackend,
    executableResolver = { CodexCliUtils.CODEX_COMMAND },
    cliAvailableProbe = { true },
    themeLaunchConfigResolver = { null },
  ).withProvider(CODEX_AGENT_SESSION_PROVIDER)
}

private const val PROJECT_PATH: String = "/work/project-a"
private const val EXISTING_THREAD_ID: String = "thread-existing"
