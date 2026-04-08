// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.service.resolveAgentSessionChatOpenPayload
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionChatOpenPayloadTest {
  @Test
  fun resolvesBaseThreadPayload() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )

      val payload = resolveAgentSessionChatOpenPayload(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = null,
        launchSpecOverride = null,
        resumeLaunchSpecProvider = ::testResumeLaunchSpec,
      )

      assertThat(payload.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1"))
      assertThat(payload.runtimeThreadId).isEqualTo("thread-1")
      assertThat(payload.launchSpec.command)
        .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "thread-1")
      assertThat(payload.threadTitle).isEqualTo("Parent title")
      assertThat(payload.subAgentId).isNull()
    }
  }

  @Test
  fun resolvesSubAgentPayloadWithSubAgentRuntimeThreadIdAndTitle() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )
      val subAgent = AgentSubAgent(id = "sub-1", name = "Sub-agent label")

      val payload = resolveAgentSessionChatOpenPayload(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = subAgent,
        launchSpecOverride = null,
        resumeLaunchSpecProvider = ::testResumeLaunchSpec,
      )

      assertThat(payload.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1"))
      assertThat(payload.runtimeThreadId).isEqualTo("sub-1")
      assertThat(payload.launchSpec.command)
        .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "sub-1")
      assertThat(payload.threadTitle).isEqualTo("Sub-agent label")
      assertThat(payload.subAgentId).isEqualTo("sub-1")
    }
  }

  @Test
  fun resolvesSubAgentPayloadWithSubAgentIdWhenNameBlank() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )
      val subAgent = AgentSubAgent(id = "sub-1", name = "")

      val payload = resolveAgentSessionChatOpenPayload(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = subAgent,
        launchSpecOverride = null,
        resumeLaunchSpecProvider = ::testResumeLaunchSpec,
      )

      assertThat(payload.threadTitle).isEqualTo("sub-1")
    }
  }

  @Test
  fun keepsLaunchSpecOverrideWhenProvided() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )
      val subAgent = AgentSubAgent(id = "sub-1", name = "Sub-agent label")

      val payload = resolveAgentSessionChatOpenPayload(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = subAgent,
        launchSpecOverride = AgentSessionTerminalLaunchSpec(
          command = listOf("custom", "resume", "sub-1"),
          envVariables = mapOf("CUSTOM_ENV" to "1"),
        ),
      )

      assertThat(payload.launchSpec.command).containsExactly("custom", "resume", "sub-1")
      assertThat(payload.launchSpec.envVariables).containsExactlyEntriesOf(mapOf("CUSTOM_ENV" to "1"))
    }
  }

  @Test
  fun resolvesLaunchSpecFromAugmenterWhenOverrideMissing() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )

      val payload = withTestLaunchSpecAugmenter {
        resolveAgentSessionChatOpenPayload(
          projectPath = PROJECT_PATH,
          thread = thread,
          subAgent = null,
          launchSpecOverride = null,
          resumeLaunchSpecProvider = { _, sessionId ->
            AgentSessionTerminalLaunchSpec(
              command = listOf("codex", "resume", sessionId),
              envVariables = mapOf("DISABLE_AUTOUPDATER" to "1"),
            )
          },
        )
      }

      assertThat(payload.launchSpec.command).containsExactly("codex", "resume", "thread-1")
      assertThat(payload.launchSpec.envVariables)
        .containsEntry("DISABLE_AUTOUPDATER", "1")
        .containsEntry(AGENT_WORKBENCH_TEST_ENV_NAME, AGENT_WORKBENCH_TEST_ENV_VALUE)
      assertThat(payload.launchSpec.envVariables.getValue("PATH").split(java.io.File.pathSeparator))
        .containsExactly(AGENT_WORKBENCH_TEST_PATH_PREPEND)
    }
  }

  @Test
  fun preservesRemoteResumeCommandFromResumeLaunchProvider() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )

      val payload = resolveAgentSessionChatOpenPayload(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = null,
        launchSpecOverride = null,
        resumeLaunchSpecProvider = { provider, sessionId ->
          check(provider == AgentSessionProvider.CODEX)
          AgentSessionTerminalLaunchSpec(
            command = listOf(
              "codex",
              "-c",
              "check_for_update_on_startup=false",
              "--remote",
              "ws://127.0.0.1:31337",
              "resume",
              sessionId,
            ),
          )
        },
      )

      assertThat(payload.launchSpec.command).containsExactly(
        "codex",
        "-c",
        "check_for_update_on_startup=false",
        "--remote",
        "ws://127.0.0.1:31337",
        "resume",
        "thread-1",
      )
    }
  }
}

private fun testResumeLaunchSpec(
  provider: AgentSessionProvider,
  sessionId: String,
): AgentSessionTerminalLaunchSpec {
  check(provider == AgentSessionProvider.CODEX)
  return AgentSessionTerminalLaunchSpec(
    command = listOf("codex", "-c", "check_for_update_on_startup=false", "resume", sessionId),
  )
}
