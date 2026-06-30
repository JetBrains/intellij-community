// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.assertNewThreadPromptLaunchOpensNewThreadView
import com.intellij.agent.workbench.sessions.newThreadPromptLaunchRequest
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class ClaudeNewThreadPromptLaunchIntegrationTest {
  @Test
  fun newThreadPlanModePromptUsesStartupPlanFlag() {
    val observation = assertNewThreadPromptLaunchOpensNewThreadView(
      descriptor = ClaudeAgentSessionProviderDescriptor(
        sessionSource = ScriptedSessionSource(provider = AgentSessionProvider.from("claude")),
        executableResolver = { ClaudeCliSupport.CLAUDE_COMMAND },
        cliAvailableProbe = { true },
        hookSettingsProvider = ::testHookSettingsArgument,
      ),
      request = newThreadPromptLaunchRequest(
        provider = AgentSessionProvider.from("claude"),
        projectPath = PROJECT_PATH,
        planMode = true,
      ),
    )
    val sessionId = checkNotNull(observation.launchSpec.preallocatedSessionId)

    assertThat(observation.startupLaunchSpecOverride?.command).containsExactly(
      "claude",
      "--session-id",
      sessionId,
      "--settings",
      testHookSettingsArgument(sessionId),
      "--permission-mode",
      "plan",
      "--",
      observation.initialPromptMessage,
    )
  }
}

private fun testHookSettingsArgument(sessionId: String): String = "/tmp/agent-workbench-claude-hooks-$sessionId.json"

private const val PROJECT_PATH: String = "/work/project-a"
