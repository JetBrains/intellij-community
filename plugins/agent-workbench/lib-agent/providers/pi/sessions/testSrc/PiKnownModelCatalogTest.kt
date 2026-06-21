// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiKnownModelCatalogTest {
  @Test
  fun parsesPiListModelsRows() {
    val models = parsePiKnownListModels(
      """
        provider                model                         context  max-out  thinking  images
        openai                  gpt-5.4                       400K     128K     yes       yes
        anthropic               claude-opus-4-8               200K     64K      no        yes
        JetBrains Central       gpt-5.5                       400K     128K     true      true
        oMLX                    Qwen3.6-27B-MLX-8bit          262K     32K      false     true
      """.trimIndent()
    )

    assertThat(models.map { it.selection.provider }).containsExactly(
      "openai",
      "anthropic",
      "JetBrains Central",
      "oMLX",
    )
    assertThat(models.map { it.selection.modelId }).containsExactly(
      "gpt-5.4",
      "claude-opus-4-8",
      "gpt-5.5",
      "Qwen3.6-27B-MLX-8bit",
    )
    assertThat(models.map { it.selection.reasoning }).containsExactly(true, false, true, false)
  }

  @Test
  fun hidesOldAndDatedClaudeRowsFromPiKnownCatalog() {
    val models = parsePiKnownListModels(
      """
        provider                model                              context  max-out  thinking  images
        anthropic               claude-3-5-haiku-20241022          200K     64K      no        true
        anthropic               claude-opus-4                      200K     64K      no        true
        anthropic               claude-opus-4-5                    200K     64K      no        true
        anthropic               claude-sonnet-4-6-20250929         200K     64K      no        true
        anthropic               claude-sonnet-4-6                  200K     64K      no        true
        anthropic               claude-opus-4-8                    200K     64K      no        true
        anthropic               claude-latest                      200K     64K      no        true
        openai                  gpt-5.4                            400K     128K     yes       true
      """.trimIndent()
    )

    assertThat(models.map { it.selection.modelId }).containsExactly(
      "claude-sonnet-4-6",
      "claude-opus-4-8",
      "claude-latest",
      "gpt-5.4",
    )
  }

  @Test
  fun mergesPiRowsWithExtensionModelsByProviderAndModel() {
    val omlxModelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val jbCentralModelId = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection())
    val extensionModels = listOf(
      AgentPromptGenerationModel(id = omlxModelId, displayName = "Qwen3.6-27B-MLX-8bit (oMLX)"),
      AgentPromptGenerationModel(id = jbCentralModelId, displayName = "gpt-5.5 (JetBrains Central)"),
    )
    val rows = parsePiKnownListModels(
      """
        provider                model                         context  max-out  thinking  images
        oMLX                    Qwen3.6-27B-MLX-8bit          262K     32K     yes       true
        openai                  gpt-5.4                       400K     128K     yes       true
        JetBrains Central       gpt-5.5                       400K     128K     yes       true
      """.trimIndent()
    )

    val models = mergePiKnownModels(rows, extensionModels)

    assertThat(models.map { it.id }).containsExactly(
      omlxModelId,
      checkNotNull(PiKnownModelCatalog.decodeGenerationModelId(models[1].id)).let { models[1].id },
      jbCentralModelId,
    )
    assertThat(models.map { it.displayName }).containsExactly(
      "Qwen3.6-27B-MLX-8bit (oMLX)",
      "gpt-5.4 (openai)",
      "gpt-5.5 (JetBrains Central)",
    )
    assertThat(PiKnownModelCatalog.decodeGenerationModelId(models[1].id)).isEqualTo(
      PiKnownModelSelection(
        provider = "openai",
        modelId = "gpt-5.4",
        displayName = "gpt-5.4 (openai)",
        reasoning = true,
      )
    )
  }

  @Test
  fun appendsExtensionModelsMissingFromPiRows() {
    val omlxModelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val extensionModel = AgentPromptGenerationModel(id = omlxModelId, displayName = "Qwen3.6-27B-MLX-8bit (oMLX)")

    val models = mergePiKnownModels(
      parsePiKnownListModels(
        """
          provider  model    context  max-out  thinking  images
          openai    gpt-5.4  400K     128K     yes       true
        """.trimIndent()
      ),
      listOf(extensionModel),
    )

    assertThat(models.map { it.displayName }).containsExactly("gpt-5.4 (openai)", "Qwen3.6-27B-MLX-8bit (oMLX)")
  }

  @Test
  fun probesPiListModelsWithExtensionCatalogEnvironment(): Unit = runBlocking(Dispatchers.Default) {
    val omlxModelId = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection())
    val jbCentralModelId = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection(profileId = "openai-gpt-5-5"))
    val extensionModels = listOf(
      AgentPromptGenerationModel(id = omlxModelId, displayName = "Qwen3.6-27B-MLX-8bit (oMLX)"),
      AgentPromptGenerationModel(id = jbCentralModelId, displayName = "GPT-5.5 (JetBrains Central)"),
    )
    val requests = mutableListOf<PiKnownListModelsRequest>()
    val catalog = PiKnownModelCatalog(
      piListModelsRunner = { piExecutable, extensionPath, extraEnvironment ->
        requests += PiKnownListModelsRequest(piExecutable, extensionPath, extraEnvironment)
        PiKnownModelCommandResult(
          exitCode = 0,
          stdout = """
            provider                model                         context  max-out  thinking  images
            oMLX                    Qwen3.6-27B-MLX-8bit          262K     32K      yes       true
            openai                  gpt-5.4                       400K     128K     yes       true
          """.trimIndent(),
        )
      }
    )

    val models = catalog.listAvailableGenerationModels(
      piExecutable = "/opt/pi/bin/pi",
      extensionPath = "/tmp/pi-extension/agent-workbench-extension.ts",
      extensionModels = extensionModels,
    )

    assertThat(requests).hasSize(1)
    assertThat(requests.single().piExecutable).isEqualTo("/opt/pi/bin/pi")
    assertThat(requests.single().extensionPath).isEqualTo("/tmp/pi-extension/agent-workbench-extension.ts")
    assertThat(requests.single().extraEnvironment[PI_MODEL_CATALOG_ENVIRONMENT_VARIABLE])
      .contains("Qwen3.6-27B-MLX-8bit")
      .contains("JetBrains Central")
      .contains("\\\"profileId\\\":\\\"openai-gpt-5-5\\\"")
      .contains("\\\"agent\\\":\\\"codex\\\"")
      .doesNotContain("gpt-5.4")
      .doesNotContain("wire-secret")
    assertThat(models.map { it.id }).startsWith(omlxModelId)
    assertThat(PiKnownModelCatalog.decodeGenerationModelId(models[1].id)?.modelId).isEqualTo("gpt-5.4")
    assertThat(PiJbCentralModelCatalog.decodeGenerationModelId(models[2].id)?.profileId).isEqualTo("openai-gpt-5-5")
  }

  @Test
  fun derivesJbCentralModelsFromPiFallbackRowsWithLaunchMetadata(): Unit = runBlocking(Dispatchers.Default) {
    val launchMetadata = PiJbCentralLaunchMetadata(
      jbCentralExecutable = "/usr/local/bin/jbcentral",
      proxyPort = 19517,
      agents = setOf(PiJbCentralAgent.CODEX, PiJbCentralAgent.CLAUDE_CODE),
    )
    val requests = mutableListOf<PiKnownListModelsRequest>()
    val catalog = PiKnownModelCatalog(
      piListModelsRunner = { piExecutable, extensionPath, extraEnvironment ->
        requests += PiKnownListModelsRequest(piExecutable, extensionPath, extraEnvironment)
        PiKnownModelCommandResult(
          exitCode = 0,
          stdout = """
            provider                model                         context  max-out  thinking  images
            openai-codex            gpt-5.4                       400K     128K     yes       true
            openai                  gpt-5.4                       400K     128K     yes       true
            openai-codex            gpt-5.5                       400K     128K     yes       true
            JetBrains Central       claude-sonnet-4-5              200K     64K      no        true
            JetBrains Central       claude-sonnet-4-6-20250929     200K     64K      no        true
            JetBrains Central       claude-opus-4-8               200K     64K      no        true
          """.trimIndent(),
        )
      }
    )

    val models = catalog.listAvailableGenerationModels(
      piExecutable = "/opt/pi/bin/pi",
      extensionPath = "/tmp/pi-extension/agent-workbench-extension.ts",
      extensionModels = emptyList(),
      jbCentralLaunchMetadata = launchMetadata,
    )

    assertThat(requests).hasSize(1)
    assertThat(requests.single().extraEnvironment).doesNotContainKey(PI_MODEL_CATALOG_ENVIRONMENT_VARIABLE)
    assertThat(requests.single().extraEnvironment[PI_JBCENTRAL_PROVIDER_ENVIRONMENT_VARIABLE])
      .contains(
        "\"provider\":\"JetBrains Central\"",
        "\"jbCentralExecutable\":\"/usr/local/bin/jbcentral\"",
        "\"proxyPort\":19517",
        "\"agents\":[\"codex\",\"claude-code\"]",
      )
    assertThat(models.map { it.displayName }).containsExactly(
      "gpt-5.4 (JetBrains Central)",
      "gpt-5.4 (openai)",
      "gpt-5.5 (JetBrains Central)",
      "claude-opus-4-8 (JetBrains Central)",
    )
    assertThat(models.map { it.group }).containsExactly(
      AgentPromptGenerationModelGroup.OPENAI,
      AgentPromptGenerationModelGroup.OPENAI,
      AgentPromptGenerationModelGroup.OPENAI,
      AgentPromptGenerationModelGroup.CLAUDE_CODE,
    )
    assertThat(PiJbCentralModelCatalog.decodeGenerationModelId(models[0].id)?.modelId).isEqualTo("gpt-5.4")
    assertThat(PiKnownModelCatalog.decodeGenerationModelId(models[1].id)?.modelId).isEqualTo("gpt-5.4")
    assertThat(PiJbCentralModelCatalog.decodeGenerationModelId(models[2].id)?.modelId).isEqualTo("gpt-5.5")
    assertThat(PiJbCentralModelCatalog.decodeGenerationModelId(models[3].id)?.modelId).isEqualTo("claude-opus-4-8")
    assertThat(models.map { it.isDefault }).containsExactly(false, false, true, false)
    assertThat(models.map { it.supportedReasoningEfforts }).containsExactly(
      PI_SUPPORTED_REASONING_EFFORTS,
      PI_SUPPORTED_REASONING_EFFORTS,
      PI_SUPPORTED_REASONING_EFFORTS,
      emptySet(),
    )
  }

  @Test
  fun suppressesPiJbCentralFallbackRowsWhenProfileBackedJbCentralModelsAreAvailable() {
    val launchMetadata = PiJbCentralLaunchMetadata(
      jbCentralExecutable = "/usr/local/bin/jbcentral",
      proxyPort = 19517,
      agents = setOf(PiJbCentralAgent.CODEX, PiJbCentralAgent.CLAUDE_CODE),
    )
    val profileBackedJbCentralModel = AgentPromptGenerationModel(
      id = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection(profileId = "openai-gpt-5-5")),
      displayName = "GPT-5.5 (JetBrains Central)",
    )

    val models = mergePiKnownModels(
      parsePiKnownListModels(
        """
          provider                model                         context  max-out  thinking  images
          openai-codex            gpt-5.4                       400K     128K     yes       true
          anthropic               claude-fable-5                200K     64K      no        true
          openai                  gpt-5.4                       400K     128K     yes       true
          JetBrains Central       claude-opus-4-8               200K     64K      no        true
        """.trimIndent()
      ),
      listOf(profileBackedJbCentralModel),
      launchMetadata,
    )

    assertThat(models.map { it.displayName }).containsExactly("gpt-5.4 (openai)", "GPT-5.5 (JetBrains Central)")
    assertThat(PiKnownModelCatalog.decodeGenerationModelId(models[0].id)?.modelId).isEqualTo("gpt-5.4")
    assertThat(PiJbCentralModelCatalog.decodeGenerationModelId(models[1].id)?.profileId).isEqualTo("openai-gpt-5-5")
  }

  @Test
  fun keepsOmlxExtensionModelsWhenSuppressingPiJbCentralFallbackRows() {
    val launchMetadata = PiJbCentralLaunchMetadata(
      jbCentralExecutable = "/usr/local/bin/jbcentral",
      proxyPort = 19517,
      agents = setOf(PiJbCentralAgent.CODEX, PiJbCentralAgent.CLAUDE_CODE),
    )
    val omlxModel = AgentPromptGenerationModel(
      id = PiOmlxModelCatalog.encodeGenerationModelId(omlxSelection()),
      displayName = "Qwen3.6-27B-MLX-8bit (oMLX)",
    )
    val profileBackedJbCentralModel = AgentPromptGenerationModel(
      id = PiJbCentralModelCatalog.encodeGenerationModelId(jbCentralSelection(profileId = "openai-gpt-5-5")),
      displayName = "GPT-5.5 (JetBrains Central)",
    )

    val models = mergePiKnownModels(
      parsePiKnownListModels(
        """
          provider                model                         context  max-out  thinking  images
          openai-codex            gpt-5.4                       400K     128K     yes       true
          anthropic               claude-fable-5                200K     64K      no        true
          JetBrains Central       claude-opus-4-8               200K     64K      no        true
        """.trimIndent()
      ),
      listOf(omlxModel, profileBackedJbCentralModel),
      launchMetadata,
    )

    assertThat(models.map { it.displayName }).containsExactly("Qwen3.6-27B-MLX-8bit (oMLX)", "GPT-5.5 (JetBrains Central)")
    assertThat(PiOmlxModelCatalog.decodeGenerationModelId(models[0].id)?.modelId).isEqualTo("Qwen3.6-27B-MLX-8bit")
    assertThat(PiJbCentralModelCatalog.decodeGenerationModelId(models[1].id)?.profileId).isEqualTo("openai-gpt-5-5")
  }

  @Test
  fun returnsEmptyWhenPiListModelsFails(): Unit = runBlocking(Dispatchers.Default) {
    val catalog = PiKnownModelCatalog(
      piListModelsRunner = { _, _, _ -> PiKnownModelCommandResult(exitCode = 1, stdout = "", stderr = "unsupported") }
    )

    assertThat(catalog.listAvailableGenerationModels("pi", null, emptyList())).isEmpty()
  }
}

private data class PiKnownListModelsRequest(
  val piExecutable: String,
  val extensionPath: String?,
  val extraEnvironment: Map<String, String>,
)

private fun omlxSelection(): PiOmlxModelSelection {
  return PiOmlxModelSelection(
    baseUrl = "http://127.0.0.1:8000",
    modelId = "Qwen3.6-27B-MLX-8bit",
    displayName = "Qwen3.6-27B-MLX-8bit",
    tokenSource = PiOmlxTokenSource.PI_AUTH,
    contextWindow = 262_144,
    maxTokens = 32_768,
    reasoning = true,
    modelType = "vlm/qwen3_5",
  )
}

private fun jbCentralSelection(profileId: String? = null): PiJbCentralModelSelection {
  return PiJbCentralModelSelection(
    provider = PI_JBCENTRAL_PROVIDER_NAME,
    modelId = "gpt-5.5",
    displayName = "gpt-5.5",
    jbCentralExecutable = "/usr/local/bin/jbcentral",
    proxyPort = 19516,
    agent = PiJbCentralAgent.CODEX,
    profileId = profileId,
  )
}
