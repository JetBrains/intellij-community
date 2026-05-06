// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class JunieAgentSessionProviderDescriptorTest {
  @Test
  fun `descriptor exposes Junie provider metadata`() {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })

    assertThat(descriptor.provider).isEqualTo(AgentSessionProvider.JUNIE)
    assertThat(descriptor.sessionSource.provider).isEqualTo(AgentSessionProvider.JUNIE)
    assertThat(descriptor.displayNameKey).isEqualTo("toolwindow.provider.junie")
    assertThat(descriptor.newSessionLabelKey).isEqualTo("toolwindow.action.new.session.junie")
    assertThat(descriptor.yoloSessionLabelKey).isEqualTo("toolwindow.action.new.session.junie.yolo")
    assertThat(descriptor.supportedLaunchModes).containsExactly(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)
    assertThat(descriptor.icon).isNotNull()
  }

  @Test
  fun `new session launch uses Junie terminal command`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "/bin/junie" })

    val launchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(launchSpec.command).containsExactly("/bin/junie", "--skip-update-check")
  }

  @Test
  fun `yolo session launch enables Junie brave mode`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "/bin/junie" })

    val launchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO)

    assertThat(launchSpec.command).containsExactly("/bin/junie", "--skip-update-check", "--brave")
  }

  @Test
  fun `resume launch uses documented session id flag`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "/bin/junie" })

    val launchSpec = descriptor.buildResumeLaunchSpec("session-251209-172932-1ze8")

    assertThat(launchSpec.command).containsExactly(
      "/bin/junie",
      "--skip-update-check",
      "--session-id",
      "session-251209-172932-1ze8",
    )
  }

  @Test
  fun `initial message plan composes prompt context`() {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })

    val plan = descriptor.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Implement the feature",
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.PATHS,
            title = "Project Selection",
            body = "path: /tmp/project",
            source = "projectView",
          )
        ),
      )
    )

    assertThat(plan.message).contains("Implement the feature")
  }

  @Test
  fun `pending metadata is resolved only for Junie pending identities`() {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val launchSpec = AgentSessionTerminalLaunchSpec(
      command = listOf("junie-test", "--skip-update-check"),
    )

    assertThat(descriptor.resolvePendingSessionMetadata("junie:new-123", launchSpec)?.launchMode).isEqualTo("standard")
    assertThat(
      descriptor.resolvePendingSessionMetadata(
        "junie:new-456",
        AgentSessionTerminalLaunchSpec(command = listOf("junie-test", "--skip-update-check", "--brave")),
      )?.launchMode
    ).isEqualTo("yolo")
    assertThat(descriptor.resolvePendingSessionMetadata("junie:session-123", launchSpec)).isNull()
    assertThat(descriptor.resolvePendingSessionMetadata("codex:new-123", launchSpec)).isNull()
    assertThat(descriptor.resolvePendingSessionMetadata("Junie:new-123", launchSpec)).isNull()
  }
}
