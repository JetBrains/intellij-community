// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.junie.common.JunieCliInfo
import com.intellij.agent.workbench.junie.common.JunieCliSupport
import com.intellij.agent.workbench.junie.common.JunieCliVersion
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
    assertThat(descriptor.promptOptions.map { it.id }).containsExactly(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
    assertThat(descriptor.promptOptions.single().defaultSelected).isFalse()
    assertThat(descriptor.supportsArchiveThread).isTrue()
    assertThat(descriptor.supportsUnarchiveThread).isTrue()
    assertThat(descriptor.archiveRefreshDelayMs).isEqualTo(1_000L)
    assertThat(descriptor.suppressArchivedThreadsDuringRefresh).isTrue()
    assertThat(descriptor.icon).isNotNull()
  }

  @Test
  fun `descriptor exposes Junie generation model aliases and efforts`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })

    val models = descriptor.listAvailableGenerationModels()

    assertThat(models.map { it.id }).containsExactly(
      "gpt",
      "gpt-codex",
      "opus",
      "sonnet",
      "gemini-pro",
      "gemini-flash",
      "grok",
    )
    assertThat(models.map { it.displayName }).containsExactly(
      "GPT",
      "GPT Codex",
      "Claude Opus",
      "Claude Sonnet",
      "Gemini Pro",
      "Gemini Flash",
      "Grok",
    )
    assertThat(descriptor.supportedReasoningEfforts).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
      AgentPromptReasoningEffort.MAX,
    )
  }

  @Test
  fun `apply generation settings leaves auto launch spec unchanged`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec = descriptor.applyGenerationSettings(baseLaunchSpec, AgentPromptGenerationSettings.AUTO)

    assertThat(updatedLaunchSpec.command).containsExactly("junie-test", "--skip-update-check")
  }

  @Test
  fun `apply generation settings adds model flag`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(modelId = "gpt-codex"),
    )

    assertThat(updatedLaunchSpec.command).containsExactly("junie-test", "--skip-update-check", "--model", "gpt-codex")
  }

  @Test
  fun `apply generation settings adds effort flag`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.XHIGH),
    )

    assertThat(updatedLaunchSpec.command).containsExactly("junie-test", "--skip-update-check", "--effort", "xhigh")
  }

  @Test
  fun `apply generation settings adds model and effort flags`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = "sonnet",
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
    )

    assertThat(updatedLaunchSpec.command).containsExactly(
      "junie-test",
      "--skip-update-check",
      "--model",
      "sonnet",
      "--effort",
      "high",
    )
  }

  @Test
  fun `apply generation settings replaces existing generation flags`() {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(
      command = listOf("junie-test", "--skip-update-check", "--model", "old", "--effort", "low"),
    )

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = "gpt-codex",
        reasoningEffort = AgentPromptReasoningEffort.MAX,
      ),
    )

    assertThat(updatedLaunchSpec.command).containsExactly(
      "junie-test",
      "--skip-update-check",
      "--model",
      "gpt-codex",
      "--effort",
      "max",
    )
  }

  @Test
  fun `apply generation settings inserts flags before prompt`() {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(
      command = listOf("junie-test", "--skip-update-check", "--prompt", "keep --model as prompt text"),
    )

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = "grok",
        reasoningEffort = AgentPromptReasoningEffort.LOW,
      ),
    )

    assertThat(updatedLaunchSpec.command).containsExactly(
      "junie-test",
      "--skip-update-check",
      "--model",
      "grok",
      "--effort",
      "low",
      "--prompt",
      "keep --model as prompt text",
    )
  }

  @Test
  fun `descriptor delegates archive unarchive and backend rename to Junie index backend`(): Unit = runBlocking(Dispatchers.Default) {
    val backend = RecordingJunieThreadMutationBackend()
    val descriptor = JunieAgentSessionProviderDescriptor(
      threadMutationBackend = backend,
      executableResolver = { "junie-test" },
    )

    assertThat(descriptor.archiveThread("/project", "thread-1")).isTrue()
    assertThat(descriptor.unarchiveThread("/project", "thread-1")).isTrue()
    val renameAction = checkNotNull(descriptor.threadRenameAction)
    assertThat(renameAction("/project", "thread-1", "Renamed thread")).isTrue()

    assertThat(backend.calls).containsExactly(
      "archive:/project:thread-1",
      "unarchive:/project:thread-1",
      "rename:/project:thread-1:Renamed thread",
    )
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
  fun `yolo resume launch enables Junie brave mode`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "/bin/junie" })

    val launchSpec = descriptor.buildResumeLaunchSpec("session-251209-172932-1ze8", AgentSessionLaunchMode.YOLO)

    assertThat(launchSpec.command).containsExactly(
      "/bin/junie",
      "--skip-update-check",
      "--brave",
      "--session-id",
      "session-251209-172932-1ze8",
    )
  }

  @Test
  fun `supported initial message launch passes prompt to Junie command`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = descriptorWithCliVersion(JunieCliVersion(1963, 1))
    assertThat(descriptor.isCliAvailable()).isTrue()
    val initialMessagePlan = AgentInitialMessagePlan(message = "Implement the feature")

    val launchSpec = descriptor.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = AgentSessionTerminalLaunchSpec(listOf("junie-test", "--skip-update-check")),
      initialMessagePlan = initialMessagePlan,
    )
    val dispatchSteps = descriptor.buildPostStartDispatchSteps(initialMessagePlan)

    assertThat(launchSpec?.command).containsExactly(
      "junie-test",
      "--skip-update-check",
      "--prompt",
      "Implement the feature",
    )
    assertThat(dispatchSteps.map { it.action }).containsExactly(AgentInitialMessageDispatchAction.SEND_TEXT)
    assertThat(dispatchSteps.map { it.text }).containsExactly("Implement the feature")
  }

  @Test
  fun `old Junie initial message launch uses interactive post start dispatch`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = descriptorWithCliVersion(JunieCliVersion(1962, 1))
    assertThat(descriptor.isCliAvailable()).isTrue()
    val initialMessagePlan = AgentInitialMessagePlan(message = "Implement the feature")

    val launchSpec = descriptor.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = AgentSessionTerminalLaunchSpec(listOf("junie-test", "--skip-update-check")),
      initialMessagePlan = initialMessagePlan,
    )
    val dispatchSteps = descriptor.buildPostStartDispatchSteps(initialMessagePlan)

    assertThat(launchSpec).isNull()
    assertThat(dispatchSteps.map { it.action }).containsExactly(AgentInitialMessageDispatchAction.SEND_TEXT)
    assertThat(dispatchSteps.map { it.text }).containsExactly("Implement the feature")
  }

  @Test
  fun `supported plan mode initial message uses Junie plan prompt command`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = descriptorWithCliVersion(JunieCliVersion(1963, 1))
    assertThat(descriptor.isCliAvailable()).isTrue()

    val plan = descriptor.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Implement the feature",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      )
    )
    val launchSpec = descriptor.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = AgentSessionTerminalLaunchSpec(listOf("junie-test", "--skip-update-check")),
      initialMessagePlan = plan,
    )
    val dispatchSteps = descriptor.buildPostStartDispatchSteps(plan)

    assertThat(plan.message).isEqualTo("Implement the feature")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
    assertThat(launchSpec?.command).containsExactly(
      "junie-test",
      "--skip-update-check",
      "--plan",
      "--prompt",
      "Implement the feature",
    )
    assertThat(dispatchSteps.map { it.action }).containsExactly(
      AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
      AgentInitialMessageDispatchAction.SEND_TEXT,
    )
    assertThat(dispatchSteps.map { it.text }).containsExactly("", "Implement the feature")
  }

  @Test
  fun `old Junie plan mode initial message uses terminal plan mode dispatch`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = descriptorWithCliVersion(JunieCliVersion(1962, 1))
    assertThat(descriptor.isCliAvailable()).isTrue()

    val plan = descriptor.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "Implement the feature",
        providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
      )
    )
    val launchSpec = descriptor.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = AgentSessionTerminalLaunchSpec(listOf("junie-test", "--skip-update-check")),
      initialMessagePlan = plan,
    )
    val dispatchSteps = descriptor.buildPostStartDispatchSteps(plan)

    assertThat(plan.message).isEqualTo("Implement the feature")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.POST_START_ONLY)
    assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
    assertThat(launchSpec).isNull()
    assertThat(dispatchSteps.map { it.action }).containsExactly(
      AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
      AgentInitialMessageDispatchAction.SEND_TEXT,
    )
    assertThat(dispatchSteps.map { it.text }).containsExactly("", "Implement the feature")
  }

  @Test
  fun `manual plan command remains plain prompt text unless option is selected`() {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })

    val plan = descriptor.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(prompt = " /plan Implement the feature ")
    )
    val dispatchSteps = descriptor.buildPostStartDispatchSteps(plan)

    assertThat(plan.message).isEqualTo("/plan Implement the feature")
    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(dispatchSteps.map { it.action }).containsExactly(AgentInitialMessageDispatchAction.SEND_TEXT)
    assertThat(dispatchSteps.map { it.text }).containsExactly("/plan Implement the feature")
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
  fun `Junie CLI version parser reads build versions`() {
    assertThat(JunieCliSupport.parseCliVersion("Junie version: build 1963.1 nightly"))
      .isEqualTo(JunieCliVersion(1963, 1))
    assertThat(JunieCliSupport.parseCliVersion("26.6.8 (1892.12)"))
      .isEqualTo(JunieCliVersion(1892, 12))
    assertThat(JunieCliSupport.parseCliVersion("Junie version unknown"))
      .isNull()
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

private fun descriptorWithCliVersion(version: JunieCliVersion?): JunieAgentSessionProviderDescriptor {
  return JunieAgentSessionProviderDescriptor(
    executableResolver = { "junie-test" },
    cliInfoResolver = { JunieCliInfo("junie-test", version) },
  )
}

private class RecordingJunieThreadMutationBackend : JunieSessionThreadMutationBackend {
  val calls = mutableListOf<String>()

  override fun renameThread(path: String, threadId: String, normalizedName: String): Boolean {
    calls += "rename:$path:$threadId:$normalizedName"
    return true
  }

  override fun archiveThread(path: String, threadId: String): Boolean {
    calls += "archive:$path:$threadId"
    return true
  }

  override fun unarchiveThread(path: String, threadId: String): Boolean {
    calls += "unarchive:$path:$threadId"
    return true
  }
}
