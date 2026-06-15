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
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
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
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    assertThat(descriptor.supportsGenerationModelSelection).isTrue()
    assertThat(descriptor.supportsArchiveThread).isTrue()
    assertThat(descriptor.supportsUnarchiveThread).isTrue()
    assertThat(descriptor.archiveRefreshDelayMs).isEqualTo(1_000L)
    assertThat(descriptor.suppressArchivedThreadsDuringRefresh).isTrue()
    assertThat(descriptor.icon).isNotNull()
  }

  @Test
  fun `descriptor exposes dynamic Junie generation models and efforts`(): Unit = runBlocking(Dispatchers.Default) {
    var requestedExecutable: String? = null
    val descriptor = JunieAgentSessionProviderDescriptor(
      executableResolver = { "junie-test" },
      generationModelCatalogResolver = { executable, _ ->
        requestedExecutable = executable
        listOf(
          AgentPromptGenerationModel(
            id = "chatgpt-5.5",
            displayName = "ChatGPT 5.5",
            supportedReasoningEfforts = setOf(
              AgentPromptReasoningEffort.LOW,
              AgentPromptReasoningEffort.HIGH,
              AgentPromptReasoningEffort.XHIGH,
            ),
            isDefault = true,
          )
        )
      },
    )

    val models = descriptor.listAvailableGenerationModels(null)

    assertThat(requestedExecutable).isEqualTo("junie-test")
    assertThat(models.map { it.id }).containsExactly("chatgpt-5.5")
    assertThat(models.single().displayName).isEqualTo("ChatGPT 5.5")
    assertThat(models.single().isDefault).isTrue()
    assertThat(models.single().supportedReasoningEfforts).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
    )
    assertThat(descriptor.supportedReasoningEfforts).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
    )
  }

  @Test
  fun `ACP session new parser exposes Junie models and thought levels`() {
    val models = parseJunieAcpGenerationModels(
      """
      {
        "jsonrpc": "2.0",
        "id": 2,
        "result": {
          "sessionId": "session-1",
          "models": {
            "currentModelId": "chatgpt-5.5-xhigh",
            "availableModels": [
              {"modelId": "chatgpt-5.5", "name": "ChatGPT 5.5"},
              {"modelId": "chatgpt-5.5-xhigh", "name": "ChatGPT 5.5 (xhigh)"}
            ]
          },
          "configOptions": [
            {
              "type": "select",
              "id": "thought-level",
              "name": "Thought Level",
              "category": "thought_level",
              "currentValue": "xhigh",
              "options": [
                {"value": "low", "name": "Low"},
                {"value": "high", "name": "High"},
                {"value": "xhigh", "name": "Extra High"}
              ]
            }
          ]
        }
      }
      """.trimIndent()
    )

    assertThat(models.map { it.id }).containsExactly("chatgpt-5.5", "chatgpt-5.5-xhigh")
    assertThat(models.map { it.displayName }).containsExactly("ChatGPT 5.5", "ChatGPT 5.5 (xhigh)")
    assertThat(models.map { it.isDefault }).containsExactly(false, true)
    assertThat(models).allSatisfy { model ->
      assertThat(model.supportedReasoningEfforts).containsExactly(
        AgentPromptReasoningEffort.LOW,
        AgentPromptReasoningEffort.HIGH,
        AgentPromptReasoningEffort.XHIGH,
      )
    }
  }

  @Test
  fun `ACP session new parser exposes Junie config option models`() {
    val models = parseJunieAcpGenerationModels(
      """
      {
        "type": "com.agentclientprotocol.rpc.JsonRpcResponse",
        "jsonrpc": "2.0",
        "id": 2,
        "result": {
          "sessionId": "session-1",
          "configOptions": [
            {
              "type": "select",
              "id": "model",
              "name": "Model",
              "currentValue": "gpt-5.5",
              "options": [
                {
                  "value": "gemini-3-flash-preview",
                  "name": "Gemini 3 Flash Preview"
                },
                {
                  "value": "claude-sonnet-4-6",
                  "name": "Claude Sonnet 4.6"
                },
                {
                  "value": "claude-opus-4-6",
                  "name": "Claude Opus 4.6"
                },
                {
                  "value": "claude-opus-4-8",
                  "name": "Claude Opus 4.8"
                },
                {
                  "value": "gpt-5.5",
                  "name": "GPT-5.5"
                },
                {
                  "value": "gpt-5.2-2025-12-11",
                  "name": "GPT-5.2"
                },
                {
                  "value": "grok-4.3",
                  "name": "Grok 4.3"
                },
                {
                  "value": "grok-4.20-multi-agent",
                  "name": "Grok 4.20 Multi Agent"
                },
                {
                  "value": "custom-model",
                  "name": "Custom Model"
                }
              ]
            },
            {
              "type": "boolean",
              "id": "think_more",
              "name": "Think More",
              "currentValue": false
            }
          ]
        }
      }
      """.trimIndent()
    )

    assertThat(models.map { it.id }).containsExactly(
      "gemini-3-flash-preview",
      "claude-sonnet-4-6",
      "claude-opus-4-6",
      "claude-opus-4-8",
      "gpt-5.5",
      "gpt-5.2-2025-12-11",
      "grok-4.3",
      "grok-4.20-multi-agent",
      "custom-model",
    )
    assertThat(models.map { it.displayName }).containsExactly(
      "Gemini 3 Flash Preview",
      "Claude Sonnet 4.6",
      "Claude Opus 4.6",
      "Claude Opus 4.8",
      "GPT-5.5",
      "GPT-5.2",
      "Grok 4.3",
      "Grok 4.20 Multi Agent",
      "Custom Model",
    )
    assertThat(models.map { it.isDefault }).containsExactly(false, false, false, false, true, false, false, false, false)
    val reasoningEffortsByModelId = models.associate { model -> model.id to model.supportedReasoningEfforts }
    assertThat(reasoningEffortsByModelId.getValue("gemini-3-flash-preview")).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
    )
    assertThat(reasoningEffortsByModelId.getValue("claude-sonnet-4-6")).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
    )
    assertThat(reasoningEffortsByModelId.getValue("claude-opus-4-6")).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
    )
    assertThat(reasoningEffortsByModelId.getValue("claude-opus-4-8")).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
    )
    assertThat(reasoningEffortsByModelId.getValue("gpt-5.5")).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
    )
    assertThat(reasoningEffortsByModelId.getValue("gpt-5.2-2025-12-11")).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
    )
    assertThat(reasoningEffortsByModelId.getValue("grok-4.3")).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
    )
    assertThat(reasoningEffortsByModelId.getValue("grok-4.20-multi-agent")).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
    )
    assertThat(reasoningEffortsByModelId.getValue("custom-model")).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
    )
  }

  @Test
  fun `ACP session new parser preserves successful empty model catalog`() {
    val models = parseJunieAcpGenerationModels(
      """
      {
        "jsonrpc": "2.0",
        "id": 2,
        "result": {
          "sessionId": "session-1",
          "models": {
            "availableModels": []
          },
          "configOptions": []
        }
      }
      """.trimIndent()
    )

    assertThat(models).isEmpty()
  }

  @Test
  fun `ACP session new parser rejects failed or malformed responses`() {
    assertThatThrownBy {
      parseJunieAcpGenerationModels(
        """
        {
          "jsonrpc": "2.0",
          "id": 2,
          "error": {
            "code": -32000,
            "message": "startup failed"
          }
        }
        """.trimIndent()
      )
    }.hasMessageContaining("session/new")

    assertThatThrownBy {
      parseJunieAcpGenerationModels("not-json")
    }.isInstanceOf(Exception::class.java)
  }

  @Test
  fun `apply generation settings leaves auto launch spec unchanged`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec =
      descriptor.applyGenerationSettings(baseLaunchSpec, AgentPromptGenerationSettings.AUTO, STANDARD_INITIAL_MESSAGE_PLAN)

    assertThat(updatedLaunchSpec.command).containsExactly("junie-test", "--skip-update-check")
  }

  @Test
  fun `apply generation settings adds model flag`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(modelId = "chatgpt-5.5"),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(updatedLaunchSpec.command).containsExactly("junie-test", "--skip-update-check", "--model", "chatgpt-5.5")
  }

  @Test
  fun `apply generation settings adds effort flag`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.XHIGH),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(updatedLaunchSpec.command).containsExactly("junie-test", "--skip-update-check", "--effort", "xhigh")
  }

  @Test
  fun `apply generation settings ignores plan effort`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(planReasoningEffort = AgentPromptReasoningEffort.XHIGH),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(updatedLaunchSpec.command).containsExactly("junie-test", "--skip-update-check")
  }

  @Test
  fun `apply generation settings adds model and effort flags`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = "chatgpt-5.5",
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(updatedLaunchSpec.command).containsExactly(
      "junie-test",
      "--skip-update-check",
      "--model",
      "chatgpt-5.5",
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
        modelId = "chatgpt-5.5",
        reasoningEffort = AgentPromptReasoningEffort.XHIGH,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(updatedLaunchSpec.command).containsExactly(
      "junie-test",
      "--skip-update-check",
      "--model",
      "chatgpt-5.5",
      "--effort",
      "xhigh",
    )
  }

  @Test
  fun `apply generation settings ignores unsupported max effort`(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = JunieAgentSessionProviderDescriptor(executableResolver = { "junie-test" })
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val updatedLaunchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.MAX),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(updatedLaunchSpec.command).containsExactly("junie-test", "--skip-update-check")
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
        modelId = "chatgpt-5.5",
        reasoningEffort = AgentPromptReasoningEffort.LOW,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(updatedLaunchSpec.command).containsExactly(
      "junie-test",
      "--skip-update-check",
      "--model",
      "chatgpt-5.5",
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

private val STANDARD_INITIAL_MESSAGE_PLAN: AgentInitialMessagePlan = AgentInitialMessagePlan(message = "Implement the feature")

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
