// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiAgentSessionProviderDescriptorTest {
  private val descriptor = PiAgentSessionProviderDescriptor(
    executableResolver = { "pi" },
    sessionIdGenerator = { "pi-session-1" },
    cliAvailableProbe = { true },
    extensionLaunchResourcesResolver = {
      PiExtensionLaunchResources(
        extensionPath = Path.of("/tmp/pi-extension/agent-workbench-extension.ts"),
        stateFilePath = Path.of("/tmp/pi-extension/state/current-theme.txt"),
      )
    },
    statusLaunchEnvironmentResolver = { sessionId ->
      mapOf(
        PI_STATUS_ENDPOINT_ENVIRONMENT_VARIABLE to "http://localhost:63342/agent-workbench/pi/status",
        PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE to "status-token-$sessionId",
      )
    },
    omlxSupportEnabledResolver = { true },
    jbCentralSupportEnabledResolver = { false },
  )

  @Test
  fun exposesPiProviderMetadata() {
    assertThat(descriptor.provider).isEqualTo(AgentSessionProvider.PI)
    assertThat(descriptor.displayPriority).isEqualTo(3)
    assertThat(descriptor.displayNameKey).isEqualTo("toolwindow.provider.pi")
    assertThat(descriptor.newSessionLabelKey).isEqualTo("toolwindow.action.new.session.pi")
    assertThat(descriptor.cliMissingMessageKey).isEqualTo("toolwindow.error.pi.cli")
    assertThat(descriptor.cliVisibilityPolicy).isEqualTo(AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE)
    assertThat(descriptor.terminalAgentKey).isEqualTo("pi")
    assertThat(descriptor.supportedLaunchModes).containsExactly(AgentSessionLaunchMode.STANDARD)
    assertThat(descriptor.promptOptions).isEmpty()
    assertThat(descriptor.supportsArchiveThread).isTrue()
    assertThat(descriptor.supportsUnarchiveThread).isTrue()
    assertThat(descriptor.supportsNewThreadRebind).isTrue()
    assertThat(descriptor.emitsScopedRefreshSignals).isTrue()
    assertThat(descriptor.refreshPathAfterCreateNewSession).isTrue()
    assertThat(descriptor.supportsGenerationModelSelection).isTrue()
  }

  @Test
  fun listsInjectedOmlxGenerationModels(): Unit = runBlocking(Dispatchers.Default) {
    val generationModel = AgentPromptGenerationModel(
      id = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection()),
      displayName = "Qwen3.6-27B-MLX-8bit (oMLX)",
    )
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      extensionLaunchResourcesResolver = { null },
      omlxGenerationModelCatalogResolver = { listOf(generationModel) },
      piKnownGenerationModelCatalogResolver = { _, _, extensionModels, _ -> extensionModels },
      omlxSupportEnabledResolver = { true },
      jbCentralSupportEnabledResolver = { false },
    )

    assertThat(descriptor.listAvailableGenerationModels(null)).containsExactly(generationModel)
  }

  @Test
  fun listsInjectedJbCentralGenerationModels(): Unit = runBlocking(Dispatchers.Default) {
    val generationModel = AgentPromptGenerationModel(
      id = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection()),
      displayName = "gpt-5.5 (JetBrains Central)",
    )
    val launchMetadata = PiJbCentralLaunchMetadata(jbCentralExecutable = "/usr/local/bin/jbcentral", proxyPort = 19516)
    var requestedPiExecutable: String? = null
    var requestedExtensionPath: String? = null
    var requestedExtensionModels: List<AgentPromptGenerationModel>? = null
    var requestedLaunchMetadata: PiJbCentralLaunchMetadata? = null
    var fallbackCatalogQueried = false
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      extensionLaunchResourcesResolver = {
        PiExtensionLaunchResources(
          extensionPath = Path.of("/tmp/pi-extension/agent-workbench-extension.ts"),
          stateFilePath = Path.of("/tmp/pi-extension/state/current-theme.txt"),
        )
      },
      jbCentralLaunchMetadataResolver = { launchMetadata },
      jbCentralGenerationModelCatalogResolver = { _, _, _ ->
        fallbackCatalogQueried = true
        emptyList()
      },
      jbCentralContributorGenerationModelCatalogResolver = { listOf(generationModel) },
      piKnownGenerationModelCatalogResolver = { piExecutable, extensionPath, extensionModels, metadata ->
        requestedPiExecutable = piExecutable
        requestedExtensionPath = extensionPath
        requestedExtensionModels = extensionModels
        requestedLaunchMetadata = metadata
        listOf(generationModel)
      },
      omlxSupportEnabledResolver = { false },
      jbCentralSupportEnabledResolver = { true },
    )

    assertThat(descriptor.listAvailableGenerationModels(null)).containsExactly(generationModel)
    assertThat(requestedPiExecutable).isEqualTo("pi")
    assertThat(requestedExtensionPath).isEqualTo("/tmp/pi-extension/agent-workbench-extension.ts")
    assertThat(requestedExtensionModels).containsExactly(generationModel)
    assertThat(requestedLaunchMetadata).isEqualTo(launchMetadata)
    assertThat(fallbackCatalogQueried).isFalse()
  }

  @Test
  fun fallsBackToFilteredJbCentralCatalogWhenFullPiCatalogIsUnavailable(): Unit = runBlocking(Dispatchers.Default) {
    val launchMetadata = PiJbCentralLaunchMetadata(jbCentralExecutable = "/usr/local/bin/jbcentral", proxyPort = 19516)
    val generationModel = AgentPromptGenerationModel(
      id = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection()),
      displayName = "gpt-5.5 (JetBrains Central)",
    )
    var requestedPiExecutable: String? = null
    var requestedExtensionPath: String? = null
    var requestedLaunchMetadata: PiJbCentralLaunchMetadata? = null
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      extensionLaunchResourcesResolver = {
        PiExtensionLaunchResources(
          extensionPath = Path.of("/tmp/pi-extension/agent-workbench-extension.ts"),
          stateFilePath = Path.of("/tmp/pi-extension/state/current-theme.txt"),
        )
      },
      jbCentralLaunchMetadataResolver = { launchMetadata },
      jbCentralGenerationModelCatalogResolver = { piExecutable, extensionPath, metadata ->
        requestedPiExecutable = piExecutable
        requestedExtensionPath = extensionPath
        requestedLaunchMetadata = metadata
        listOf(generationModel)
      },
      piKnownGenerationModelCatalogResolver = { _, _, _, _ -> emptyList() },
      omlxSupportEnabledResolver = { false },
      jbCentralSupportEnabledResolver = { true },
    )

    assertThat(descriptor.listAvailableGenerationModels(null)).containsExactly(generationModel)
    assertThat(requestedPiExecutable).isEqualTo("pi")
    assertThat(requestedExtensionPath).isEqualTo("/tmp/pi-extension/agent-workbench-extension.ts")
    assertThat(requestedLaunchMetadata).isEqualTo(launchMetadata)
  }

  @Test
  fun mergesJbCentralFallbackWhenKnownCatalogContainsOnlyOmlxModels(): Unit = runBlocking(Dispatchers.Default) {
    val launchMetadata = PiJbCentralLaunchMetadata(jbCentralExecutable = "/usr/local/bin/jbcentral", proxyPort = 19516)
    val omlxModel = AgentPromptGenerationModel(
      id = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection()),
      displayName = "Qwen3.6-27B-MLX-8bit (oMLX)",
    )
    val jbCentralModel = AgentPromptGenerationModel(
      id = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection()),
      displayName = "gpt-5.5 (JetBrains Central)",
    )
    var requestedExtensionModels: List<AgentPromptGenerationModel>? = null
    var fallbackCatalogQueried = false
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      extensionLaunchResourcesResolver = {
        PiExtensionLaunchResources(
          extensionPath = Path.of("/tmp/pi-extension/agent-workbench-extension.ts"),
          stateFilePath = Path.of("/tmp/pi-extension/state/current-theme.txt"),
        )
      },
      omlxGenerationModelCatalogResolver = { listOf(omlxModel) },
      jbCentralLaunchMetadataResolver = { launchMetadata },
      jbCentralContributorGenerationModelCatalogResolver = { emptyList() },
      jbCentralGenerationModelCatalogResolver = { _, _, metadata ->
        assertThat(metadata).isEqualTo(launchMetadata)
        fallbackCatalogQueried = true
        listOf(jbCentralModel)
      },
      piKnownGenerationModelCatalogResolver = { _, _, extensionModels, metadata ->
        requestedExtensionModels = extensionModels
        assertThat(metadata).isEqualTo(launchMetadata)
        listOf(omlxModel)
      },
      omlxSupportEnabledResolver = { true },
      jbCentralSupportEnabledResolver = { true },
    )

    assertThat(descriptor.listAvailableGenerationModels(null)).containsExactly(omlxModel, jbCentralModel)
    assertThat(requestedExtensionModels).containsExactly(omlxModel)
    assertThat(fallbackCatalogQueried).isTrue()
  }

  @Test
  fun disabledGenerationModelSupportHidesGenerationModelSelectionAndSkipsCatalogRefresh(): Unit = runBlocking(Dispatchers.Default) {
    var omlxCatalogQueried = false
    var jbCentralCatalogQueried = false
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      omlxGenerationModelCatalogResolver = {
        omlxCatalogQueried = true
        listOf(
          AgentPromptGenerationModel(
            id = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection()),
            displayName = "Qwen3.6-27B-MLX-8bit (oMLX)",
          )
        )
      },
      jbCentralGenerationModelCatalogResolver = { _, _, _ ->
        jbCentralCatalogQueried = true
        listOf(
          AgentPromptGenerationModel(
            id = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection()),
            displayName = "gpt-5.5 (JetBrains Central)",
          )
        )
      },
      piKnownGenerationModelCatalogResolver = { _, _, _, _ -> error("Pi known catalog resolver must not be called") },
      omlxSupportEnabledResolver = { false },
      jbCentralSupportEnabledResolver = { false },
    )

    assertThat(descriptor.supportsGenerationModelSelection).isFalse()
    assertThat(descriptor.listAvailableGenerationModels(null)).isEmpty()
    assertThat(omlxCatalogQueried).isFalse()
    assertThat(jbCentralCatalogQueried).isFalse()
  }

  @Test
  fun listsFullPiGenerationModelsWhenKnownCatalogIsAvailable(): Unit = runBlocking(Dispatchers.Default) {
    val extensionModel = AgentPromptGenerationModel(
      id = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection()),
      displayName = "Qwen3.6-27B-MLX-8bit (oMLX)",
    )
    val knownModel = AgentPromptGenerationModel(
      id = PiKnownModelCatalog.encodeGenerationModelId(knownSelection("openai", "gpt-5.4")),
      displayName = "gpt-5.4 (openai)",
      supportedReasoningEfforts = PI_SUPPORTED_REASONING_EFFORTS,
    )
    var requestedPiExecutable: String? = null
    var requestedExtensionPath: String? = null
    var requestedExtensionModels: List<AgentPromptGenerationModel>? = null
    var requestedLaunchMetadata: PiJbCentralLaunchMetadata? = null
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      extensionLaunchResourcesResolver = {
        PiExtensionLaunchResources(
          extensionPath = Path.of("/tmp/pi-extension/agent-workbench-extension.ts"),
          stateFilePath = Path.of("/tmp/pi-extension/state/current-theme.txt"),
        )
      },
      omlxGenerationModelCatalogResolver = { listOf(extensionModel) },
      piKnownGenerationModelCatalogResolver = { piExecutable, extensionPath, extensionModels, launchMetadata ->
        requestedPiExecutable = piExecutable
        requestedExtensionPath = extensionPath
        requestedExtensionModels = extensionModels
        requestedLaunchMetadata = launchMetadata
        listOf(knownModel, extensionModel)
      },
      omlxSupportEnabledResolver = { true },
      jbCentralSupportEnabledResolver = { false },
    )

    assertThat(descriptor.listAvailableGenerationModels(null)).containsExactly(knownModel, extensionModel)
    assertThat(requestedPiExecutable).isEqualTo("pi")
    assertThat(requestedExtensionPath).isEqualTo("/tmp/pi-extension/agent-workbench-extension.ts")
    assertThat(requestedExtensionModels).containsExactly(extensionModel)
    assertThat(requestedLaunchMetadata).isNull()
  }

  @Test
  fun buildNewSessionLaunchSpecUsesSessionIdFlag(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(launchSpec.command).containsExactly(
      "pi", "--extension", "/tmp/pi-extension/agent-workbench-extension.ts", "--session-id", "pi-session-1"
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_THEME_STATE_ENVIRONMENT_VARIABLE,
      "/tmp/pi-extension/state/current-theme.txt",
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_STATUS_ENDPOINT_ENVIRONMENT_VARIABLE,
      "http://localhost:63342/agent-workbench/pi/status",
    )
    assertThat(launchSpec.envVariables).containsEntry(PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE, "status-token-pi-session-1")
    assertThat(launchSpec.preallocatedSessionId).isEqualTo("pi-session-1")
  }

  @Test
  fun buildResumeLaunchSpecUsesSessionFlag(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = descriptor.buildResumeLaunchSpec("thread-1")

    assertThat(launchSpec.command).containsExactly(
      "pi", "--extension", "/tmp/pi-extension/agent-workbench-extension.ts", "--session", "thread-1"
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_THEME_STATE_ENVIRONMENT_VARIABLE,
      "/tmp/pi-extension/state/current-theme.txt",
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_STATUS_ENDPOINT_ENVIRONMENT_VARIABLE,
      "http://localhost:63342/agent-workbench/pi/status",
    )
    assertThat(launchSpec.envVariables).containsEntry(PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE, "status-token-thread-1")
  }

  @Test
  fun buildLaunchSpecOmitsExtensionWhenExtensionSupportUnavailable(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      sessionIdGenerator = { "pi-session-1" },
      cliAvailableProbe = { true },
      extensionLaunchResourcesResolver = { null },
      omlxSupportEnabledResolver = { true },
      jbCentralSupportEnabledResolver = { false },
    )

    val launchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(launchSpec.command).containsExactly("pi", "--session-id", "pi-session-1")
    assertThat(launchSpec.envVariables).isEmpty()
  }

  @Test
  fun buildLaunchSpecWithInitialMessageAppendsPositionalArgument(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = descriptor.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = descriptor.buildResumeLaunchSpec("thread-1"),
      initialMessagePlan = AgentInitialMessagePlan(message = "Summarize changes"),
    )

    assertThat(launchSpec.command).containsExactly(
      "pi", "--extension", "/tmp/pi-extension/agent-workbench-extension.ts", "--session", "thread-1", "Summarize changes"
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_THEME_STATE_ENVIRONMENT_VARIABLE,
      "/tmp/pi-extension/state/current-theme.txt",
    )
  }

  @Test
  fun applyGenerationSettingsAddsOmlxProviderAndModelBeforeSessionFlags(): Unit = runBlocking(Dispatchers.Default) {
    val modelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = modelId,
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(launchSpec.command).containsExactly(
      "pi",
      "--extension",
      "/tmp/pi-extension/agent-workbench-extension.ts",
      "--provider",
      "oMLX",
      "--model",
      "Qwen3.6-27B-MLX-8bit",
      "--thinking",
      "high",
      "--session-id",
      "pi-session-1",
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_THEME_STATE_ENVIRONMENT_VARIABLE,
      "/tmp/pi-extension/state/current-theme.txt",
    )
    assertThat(launchSpec.envVariables).containsEntry(PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE, "status-token-pi-session-1")
    val providerMetadata = launchSpec.envVariables[PI_OMLX_PROVIDER_ENVIRONMENT_VARIABLE]
    assertThat(providerMetadata)
      .contains(
        "\"baseUrl\":\"http://127.0.0.1:8000\"",
        "\"provider\":\"oMLX\"",
        "\"modelId\":\"Qwen3.6-27B-MLX-8bit\"",
        "\"tokenSource\":\"pi-auth\"",
      )
      .doesNotContain("local-api-key")
  }

  @Test
  fun applyGenerationSettingsIgnoresPlanReasoningEffort(): Unit = runBlocking(Dispatchers.Default) {
    val modelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = modelId,
        planReasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(launchSpec.command).containsExactly(
      "pi",
      "--extension",
      "/tmp/pi-extension/agent-workbench-extension.ts",
      "--provider",
      "oMLX",
      "--model",
      "Qwen3.6-27B-MLX-8bit",
      "--session-id",
      "pi-session-1",
    )
  }

  @Test
  fun applyGenerationModelCatalogAddsPiScopedModelsBeforeSessionFlags(): Unit = runBlocking(Dispatchers.Default) {
    val modelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val knownModelId = PiKnownModelCatalog.encodeGenerationModelId(knownSelection("openai", "gpt-5.4"))
    val otherModelId = PiJbCentralModelCatalog.encodeGenerationModelId(
      jbCentralSelection("claude-opus-4-8", PiJbCentralAgent.CLAUDE_CODE)
    )
    val generationSettings = AgentPromptGenerationSettings(
      modelId = modelId,
      reasoningEffort = AgentPromptReasoningEffort.HIGH,
    )
    val baseLaunchSpec = descriptor.applyGenerationSettings(
      descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD),
      generationSettings,
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    val launchSpec = descriptor.applyGenerationModelCatalog(
      baseLaunchSpec = baseLaunchSpec,
      generationSettings = generationSettings,
      generationModelCatalog = listOf(
        AgentPromptGenerationModel(id = modelId, displayName = "Qwen3.6-27B-MLX-8bit (oMLX)"),
        AgentPromptGenerationModel(id = knownModelId, displayName = "gpt-5.4 (openai)"),
        AgentPromptGenerationModel(id = otherModelId, displayName = "claude-opus-4-8 (JetBrains Central)"),
      ),
    )

    assertThat(launchSpec.command).containsExactly(
      "pi",
      "--extension",
      "/tmp/pi-extension/agent-workbench-extension.ts",
      "--provider",
      "oMLX",
      "--model",
      "Qwen3.6-27B-MLX-8bit",
      "--thinking",
      "high",
      "--models",
      "oMLX/Qwen3.6-27B-MLX-8bit,openai/gpt-5.4,JetBrains Central/claude-opus-4-8",
      "--session-id",
      "pi-session-1",
    )
    assertThat(launchSpec.envVariables[PI_MODEL_CATALOG_ENVIRONMENT_VARIABLE])
      .contains("Qwen3.6-27B-MLX-8bit")
      .contains("JetBrains Central")
      .contains("agent")
      .contains("claude-code")
      .doesNotContain("gpt-5.4")
  }

  @Test
  fun applyGenerationModelCatalogAddsPiScopedModelsForAutoSettingsWithoutSelectingModel(): Unit = runBlocking(Dispatchers.Default) {
    val modelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val knownModelId = PiKnownModelCatalog.encodeGenerationModelId(knownSelection("openai", "gpt-5.4"))
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = descriptor.applyGenerationModelCatalog(
      baseLaunchSpec = baseLaunchSpec,
      generationSettings = AgentPromptGenerationSettings.AUTO,
      generationModelCatalog = listOf(
        AgentPromptGenerationModel(id = modelId, displayName = "Qwen3.6-27B-MLX-8bit (oMLX)"),
        AgentPromptGenerationModel(id = knownModelId, displayName = "gpt-5.4 (openai)"),
      ),
    )

    assertThat(launchSpec.command).containsExactly(
      "pi",
      "--extension",
      "/tmp/pi-extension/agent-workbench-extension.ts",
      "--models",
      "oMLX/Qwen3.6-27B-MLX-8bit,openai/gpt-5.4",
      "--session-id",
      "pi-session-1",
    )
    assertThat(launchSpec.envVariables[PI_MODEL_CATALOG_ENVIRONMENT_VARIABLE])
      .contains("Qwen3.6-27B-MLX-8bit")
      .doesNotContain("gpt-5.4")
  }

  @Test
  fun applyGenerationModelCatalogLeavesAutoSettingsUnchangedWhenCatalogIsEmpty(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = descriptor.applyGenerationModelCatalog(
      baseLaunchSpec = baseLaunchSpec,
      generationSettings = AgentPromptGenerationSettings.AUTO,
      generationModelCatalog = emptyList(),
    )

    assertThat(launchSpec).isEqualTo(baseLaunchSpec)
  }

  @Test
  fun applyGenerationModelCatalogKeepsSelectedPiModelFirstInScope(): Unit = runBlocking(Dispatchers.Default) {
    val modelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val knownModelId = PiKnownModelCatalog.encodeGenerationModelId(knownSelection("openai", "gpt-5.4"))
    val otherModelId = PiJbCentralModelCatalog.encodeGenerationModelId(
      jbCentralSelection("claude-opus-4-8", PiJbCentralAgent.CLAUDE_CODE)
    )
    val baseLaunchSpec = descriptor.buildResumeLaunchSpec("thread-1")

    val launchSpec = descriptor.applyGenerationModelCatalog(
      baseLaunchSpec = baseLaunchSpec,
      generationSettings = AgentPromptGenerationSettings(modelId = modelId),
      generationModelCatalog = listOf(
        AgentPromptGenerationModel(id = otherModelId, displayName = "claude-opus-4-8 (JetBrains Central)"),
        AgentPromptGenerationModel(id = knownModelId, displayName = "gpt-5.4 (openai)"),
        AgentPromptGenerationModel(id = modelId, displayName = "Qwen3.6-27B-MLX-8bit (oMLX)"),
      ),
    )

    assertThat(launchSpec.command).containsSubsequence(
      "--models",
      "oMLX/Qwen3.6-27B-MLX-8bit,JetBrains Central/claude-opus-4-8,openai/gpt-5.4",
      "--session",
      "thread-1",
    )
    assertThat(launchSpec.envVariables[PI_MODEL_CATALOG_ENVIRONMENT_VARIABLE])
      .contains("Qwen3.6-27B-MLX-8bit")
      .contains("JetBrains Central")
      .contains("agent")
      .contains("claude-code")
      .doesNotContain("gpt-5.4")
  }

  @Test
  fun applyGenerationSettingsAddsKnownPiProviderAndModelBeforeSessionFlags(): Unit = runBlocking(Dispatchers.Default) {
    val modelId = PiKnownModelCatalog.encodeGenerationModelId(knownSelection("openai", "gpt-5.4"))
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = modelId,
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(launchSpec.command).containsExactly(
      "pi",
      "--extension",
      "/tmp/pi-extension/agent-workbench-extension.ts",
      "--provider",
      "openai",
      "--model",
      "gpt-5.4",
      "--thinking",
      "high",
      "--session-id",
      "pi-session-1",
    )
    assertThat(launchSpec.envVariables).doesNotContainKeys(
      PI_OMLX_PROVIDER_ENVIRONMENT_VARIABLE,
      PI_JBCENTRAL_PROVIDER_ENVIRONMENT_VARIABLE,
    )
  }

  @Test
  fun applyGenerationSettingsAddsJbCentralProviderAndModelBeforeSessionFlags(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      sessionIdGenerator = { "pi-session-1" },
      extensionLaunchResourcesResolver = {
        PiExtensionLaunchResources(
          extensionPath = Path.of("/tmp/pi-extension/agent-workbench-extension.ts"),
          stateFilePath = Path.of("/tmp/pi-extension/state/current-theme.txt"),
        )
      },
      statusLaunchEnvironmentResolver = { sessionId ->
        mapOf(PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE to "status-token-$sessionId")
      },
      omlxSupportEnabledResolver = { false },
      jbCentralSupportEnabledResolver = { true },
    )
    val modelId = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection())
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = modelId,
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(launchSpec.command).containsExactly(
      "pi",
      "--extension",
      "/tmp/pi-extension/agent-workbench-extension.ts",
      "--provider",
      "JetBrains Central",
      "--model",
      "gpt-5.5",
      "--thinking",
      "high",
      "--session-id",
      "pi-session-1",
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_THEME_STATE_ENVIRONMENT_VARIABLE,
      "/tmp/pi-extension/state/current-theme.txt",
    )
    assertThat(launchSpec.envVariables).containsEntry(PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE, "status-token-pi-session-1")
    assertThat(launchSpec.envVariables).doesNotContainKey(PI_OMLX_PROVIDER_ENVIRONMENT_VARIABLE)
    val providerMetadata = launchSpec.envVariables[PI_JBCENTRAL_PROVIDER_ENVIRONMENT_VARIABLE]
    assertThat(providerMetadata)
      .contains(
        "\"provider\":\"JetBrains Central\"",
        "\"jbCentralExecutable\":\"/usr/local/bin/jbcentral\"",
        "\"proxyPort\":19516",
        "\"agent\":\"codex\"",
      )
      .doesNotContain("wire-secret")
  }

  @Test
  fun applyGenerationSettingsOmitsThinkingForNonReasoningOmlxModels(): Unit = runBlocking(Dispatchers.Default) {
    val modelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection(reasoning = false))
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = modelId,
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(
      descriptor.sanitizeGenerationSettings(
        AgentPromptGenerationSettings(
          modelId = modelId,
          reasoningEffort = AgentPromptReasoningEffort.HIGH,
        )
      )
    )
      .isEqualTo(AgentPromptGenerationSettings(modelId = modelId))
    assertThat(launchSpec.command).containsExactly(
      "pi",
      "--extension",
      "/tmp/pi-extension/agent-workbench-extension.ts",
      "--provider",
      "oMLX",
      "--model",
      "Qwen3.6-27B-MLX-8bit",
      "--session-id",
      "pi-session-1",
    )
  }

  @Test
  fun applyGenerationSettingsOmitsThinkingForNonReasoningKnownPiModels(): Unit = runBlocking(Dispatchers.Default) {
    val modelId = PiKnownModelCatalog.encodeGenerationModelId(knownSelection("openai", "gpt-4.1", reasoning = false))
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    val launchSpec = descriptor.applyGenerationSettings(
      baseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = modelId,
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(
      descriptor.sanitizeGenerationSettings(
        AgentPromptGenerationSettings(
          modelId = modelId,
          reasoningEffort = AgentPromptReasoningEffort.HIGH,
        )
      )
    )
      .isEqualTo(AgentPromptGenerationSettings(modelId = modelId))
    assertThat(launchSpec.command).containsExactly(
      "pi",
      "--extension",
      "/tmp/pi-extension/agent-workbench-extension.ts",
      "--provider",
      "openai",
      "--model",
      "gpt-4.1",
      "--session-id",
      "pi-session-1",
    )
  }

  @Test
  fun displaysEncodedGenerationModelIdsAsDecodedModelNames() {
    val omlxModelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val jbCentralModelId = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection())
    val knownModelId = PiKnownModelCatalog.encodeGenerationModelId(knownSelection("openai", "gpt-5.5"))

    assertThat(descriptor.displayNameForGenerationModelId(omlxModelId)).isEqualTo("Qwen3.6-27B-MLX-8bit")
    assertThat(descriptor.displayNameForGenerationModelId(jbCentralModelId)).isEqualTo("gpt-5.5")
    assertThat(descriptor.displayNameForGenerationModelId(knownModelId)).isEqualTo("gpt-5.5 (openai)")
    assertThat(descriptor.displayNameForGenerationModelId("raw-model-id")).isNull()
  }

  @Test
  fun applyGenerationSettingsReplacesStalePiGenerationArgs(): Unit = runBlocking(Dispatchers.Default) {
    val modelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val staleBaseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD).copy(
      command = listOf(
        "pi",
        "--extension",
        "/tmp/pi-extension/agent-workbench-extension.ts",
        "--provider",
        "old-provider",
        "--model",
        "old-model",
        "--thinking",
        "high",
        "--session-id",
        "pi-session-1",
      )
    )

    val launchSpec = descriptor.applyGenerationSettings(
      staleBaseLaunchSpec,
      AgentPromptGenerationSettings(
        modelId = modelId,
        reasoningEffort = AgentPromptReasoningEffort.LOW,
      ),
      STANDARD_INITIAL_MESSAGE_PLAN,
    )

    assertThat(launchSpec.command).containsExactly(
      "pi",
      "--extension",
      "/tmp/pi-extension/agent-workbench-extension.ts",
      "--provider",
      "oMLX",
      "--model",
      "Qwen3.6-27B-MLX-8bit",
      "--thinking",
      "low",
      "--session-id",
      "pi-session-1",
    )
  }

  @Test
  fun applyGenerationSettingsLeavesAutoAndUnknownModelsUnchanged(): Unit = runBlocking(Dispatchers.Default) {
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(descriptor.applyGenerationSettings(baseLaunchSpec, AgentPromptGenerationSettings.AUTO, STANDARD_INITIAL_MESSAGE_PLAN))
      .isEqualTo(baseLaunchSpec)

    val sanitized = descriptor.sanitizeGenerationSettings(
      AgentPromptGenerationSettings(
        modelId = "Qwen3.6-27B-MLX-8bit",
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      )
    )
    assertThat(sanitized.modelId).isNull()
    assertThat(sanitized.reasoningEffort).isEqualTo(AgentPromptReasoningEffort.AUTO)
    assertThat(descriptor.applyGenerationSettings(baseLaunchSpec, sanitized, STANDARD_INITIAL_MESSAGE_PLAN)).isEqualTo(baseLaunchSpec)

    val validModelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    assertThat(
      descriptor.sanitizeGenerationSettings(
        AgentPromptGenerationSettings(
          modelId = validModelId,
          reasoningEffort = AgentPromptReasoningEffort.MAX,
        )
      )
    ).isEqualTo(AgentPromptGenerationSettings(modelId = validModelId))
  }

  @Test
  fun disabledOmlxSupportIgnoresSavedOmlxSelection(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      sessionIdGenerator = { "pi-session-1" },
      extensionLaunchResourcesResolver = { null },
      omlxSupportEnabledResolver = { false },
      jbCentralSupportEnabledResolver = { false },
    )
    val generationSettings = AgentPromptGenerationSettings(
      modelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection()),
      reasoningEffort = AgentPromptReasoningEffort.HIGH,
    )
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(descriptor.sanitizeGenerationSettings(generationSettings)).isEqualTo(AgentPromptGenerationSettings.AUTO)
    assertThat(descriptor.applyGenerationSettings(baseLaunchSpec, generationSettings, STANDARD_INITIAL_MESSAGE_PLAN)).isEqualTo(
      baseLaunchSpec)
  }

  @Test
  fun disabledJbCentralSupportIgnoresSavedJbCentralSelection(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      sessionIdGenerator = { "pi-session-1" },
      extensionLaunchResourcesResolver = { null },
      omlxSupportEnabledResolver = { false },
      jbCentralSupportEnabledResolver = { false },
    )
    val generationSettings = AgentPromptGenerationSettings(
      modelId = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection()),
      reasoningEffort = AgentPromptReasoningEffort.HIGH,
    )
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(descriptor.sanitizeGenerationSettings(generationSettings)).isEqualTo(AgentPromptGenerationSettings.AUTO)
    assertThat(descriptor.applyGenerationSettings(baseLaunchSpec, generationSettings, STANDARD_INITIAL_MESSAGE_PLAN)).isEqualTo(
      baseLaunchSpec)
  }

  @Test
  fun buildInitialMessagePlanUsesStandardComposeDefault() {
    val plan = descriptor.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "  Refactor this  ",
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.SNIPPET,
            title = "Selection",
            body = "val answer = 42",
            source = "editor",
          )
        ),
      )
    )

    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.message).startsWith("Refactor this\n\n### IDE Context")
  }

  @Test
  fun cliAvailabilityUsesInjectedProbe(): Unit = runBlocking(Dispatchers.Default) {
    val unavailableDescriptor = PiAgentSessionProviderDescriptor(
      sessionSource = emptySource(),
      executableResolver = { "pi" },
      cliAvailableProbe = { false },
      omlxSupportEnabledResolver = { true },
      jbCentralSupportEnabledResolver = { false },
    )

    assertThat(unavailableDescriptor.isCliAvailable()).isFalse()
  }

  @Test
  fun resolverTerminalAgentUsesPiLookupLocations() {
    val terminalAgent = PiCliSupport.resolverTerminalAgent

    assertThat(terminalAgent.agentKey.key).isEqualTo("pi")
    assertThat(terminalAgent.binaryName).isEqualTo("pi")
    assertThat(terminalAgent.posixKnownLocationCandidates).containsExactly(
      "$" + "HOME/.local/bin",
      "/opt/homebrew/bin",
      "/usr/local/bin",
    )
    assertThat(terminalAgent.windowsKnownLocationCandidates).containsExactly(
      "$" + "HOME\\AppData\\Roaming\\npm",
      "$" + "HOME\\.local\\bin",
    )
  }

  @Test
  fun archiveUnarchiveAndRenameDelegateToThreadMutationBackend(): Unit = runBlocking(Dispatchers.Default) {
    var archivedPath: String? = null
    var archivedThreadId: String? = null
    var unarchivedPath: String? = null
    var unarchivedThreadId: String? = null
    var renamedPath: String? = null
    var renamedThreadId: String? = null
    var renamedName: String? = null
    val descriptor = PiAgentSessionProviderDescriptor(
      sessionSource = emptySource(),
      threadMutationBackend = object : PiSessionThreadMutationBackend {
        override fun renameThread(path: String, threadId: String, normalizedName: String): Boolean {
          renamedPath = path
          renamedThreadId = threadId
          renamedName = normalizedName
          return true
        }

        override fun archiveThread(path: String, threadId: String): Boolean {
          archivedPath = path
          archivedThreadId = threadId
          return true
        }

        override fun unarchiveThread(path: String, threadId: String): Boolean {
          unarchivedPath = path
          unarchivedThreadId = threadId
          return true
        }
      },
    )

    assertThat(descriptor.archiveThread("/tmp/project", "thread-1")).isTrue()
    assertThat(descriptor.unarchiveThread("/tmp/project", "thread-1")).isTrue()
    val renameAction = checkNotNull(descriptor.threadRenameAction)
    assertThat(renameAction("/tmp/project", "thread-1", "Renamed thread")).isTrue()
    assertThat(archivedPath).isEqualTo("/tmp/project")
    assertThat(archivedThreadId).isEqualTo("thread-1")
    assertThat(unarchivedPath).isEqualTo("/tmp/project")
    assertThat(unarchivedThreadId).isEqualTo("thread-1")
    assertThat(renamedPath).isEqualTo("/tmp/project")
    assertThat(renamedThreadId).isEqualTo("thread-1")
    assertThat(renamedName).isEqualTo("Renamed thread")
  }
}

private val STANDARD_INITIAL_MESSAGE_PLAN: AgentInitialMessagePlan = AgentInitialMessagePlan(message = "Refactor this")

private fun omlxSelection(reasoning: Boolean = true): PiOmlxModelSelection {
  return PiOmlxModelSelection(
    baseUrl = "http://127.0.0.1:8000",
    modelId = "Qwen3.6-27B-MLX-8bit",
    displayName = "Qwen3.6-27B-MLX-8bit",
    tokenSource = PiOmlxTokenSource.PI_AUTH,
    contextWindow = 262_144,
    maxTokens = 32_768,
    reasoning = reasoning,
    modelType = "vlm/qwen3_5",
  )
}

private fun jbCentralSelection(
  modelId: String = "gpt-5.5",
  agent: PiJbCentralAgent = if (modelId.startsWith("claude")) PiJbCentralAgent.CLAUDE_CODE else PiJbCentralAgent.CODEX,
): PiJbCentralModelSelection {
  return PiJbCentralModelSelection(
    provider = PI_JBCENTRAL_PROVIDER_NAME,
    modelId = modelId,
    displayName = modelId,
    jbCentralExecutable = "/usr/local/bin/jbcentral",
    proxyPort = 19516,
    agent = agent,
    reasoning = true,
  )
}

private fun knownSelection(provider: String, modelId: String, reasoning: Boolean = true): PiKnownModelSelection {
  return PiKnownModelSelection(
    provider = provider,
    modelId = modelId,
    displayName = defaultPiKnownDisplayName(provider, modelId),
    reasoning = reasoning,
  )
}

private fun emptySource(): AgentSessionSource {
  return object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = AgentSessionProvider.PI

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }
}
