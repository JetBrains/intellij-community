// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.assertExistingThreadLaunchUsesPostStartDispatch
import com.intellij.agent.workbench.sessions.existingThreadPromptLaunchRequest
import com.intellij.agent.workbench.sessions.thread
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class ClaudeExistingThreadPromptLaunchIntegrationTest {
  @Test
  fun existingThreadStandardPromptUsesPostStartDispatch() {
    assertExistingThreadLaunchUsesPostStartDispatch(
      descriptor = descriptor(),
      request = existingThreadPromptLaunchRequest(
        provider = AgentSessionProvider.CLAUDE,
        projectPath = PROJECT_PATH,
        threadId = EXISTING_THREAD_ID,
      ),
      projectPath = PROJECT_PATH,
      threadId = EXISTING_THREAD_ID,
    )
  }

  @Test
  fun existingThreadPlanModePromptUsesPostStartDispatch() {
    assertExistingThreadLaunchUsesPostStartDispatch(
      descriptor = descriptor(),
      request = existingThreadPromptLaunchRequest(
        provider = AgentSessionProvider.CLAUDE,
        projectPath = PROJECT_PATH,
        threadId = EXISTING_THREAD_ID,
        planMode = true,
      ),
      projectPath = PROJECT_PATH,
      threadId = EXISTING_THREAD_ID,
    )
  }
}

private fun descriptor(): ClaudeAgentSessionProviderDescriptor {
  return ClaudeAgentSessionProviderDescriptor(
    sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listOf(thread(id = EXISTING_THREAD_ID, updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
        }
        else {
          emptyList()
        }
      },
    ),
    executableResolver = { ClaudeCliSupport.CLAUDE_COMMAND },
  )
}

private const val PROJECT_PATH: String = "/work/project-a"
private const val EXISTING_THREAD_ID: String = "thread-existing"
