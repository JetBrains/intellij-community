// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiOmlxModelCatalogTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun parsesOmlxModelsStatusAndFiltersNonLanguageModels() {
    val models = parseOmlxModelsStatus(
      responseJson = """
        {
          "models": [
            {
              "id": "Qwen3.6-27B-MLX-8bit",
              "model_alias": "Qwen 27B",
              "max_context_window": 262144,
              "max_tokens": 32768,
              "thinking_default": true,
              "model_type": "vlm",
              "config_model_type": "qwen3_5",
              "loaded": true
            },
            {
              "id": "MarkItDown",
              "display_name": "MarkItDown",
              "model_type": "markitdown",
              "loaded": true
            },
            {
              "id": "broken"
            }
          ]
        }
      """.trimIndent(),
      baseUrl = "http://127.0.0.1:8000",
      tokenSource = PiOmlxTokenSource.PI_AUTH,
    )

    assertThat(models).hasSize(1)
    assertThat(models.single().loaded).isTrue()
    assertThat(models.single().selection).isEqualTo(
      PiOmlxModelSelection(
        baseUrl = "http://127.0.0.1:8000",
        modelId = "Qwen3.6-27B-MLX-8bit",
        displayName = "Qwen 27B",
        tokenSource = PiOmlxTokenSource.PI_AUTH,
        contextWindow = 262_144,
        maxTokens = 32_768,
        reasoning = true,
        modelType = "vlm/qwen3_5",
      )
    )
  }

  @Test
  fun encodesSelectionWithoutCredentialsAndDecodesIt() {
    val selection = PiOmlxModelSelection(
      baseUrl = "http://127.0.0.1:8000",
      modelId = "Qwen3.6-27B-MLX-8bit",
      displayName = "Qwen 27B",
      tokenSource = PiOmlxTokenSource.OMLX_SETTINGS,
      contextWindow = 262_144,
      maxTokens = 32_768,
      reasoning = true,
      modelType = "vlm/qwen3_5",
    )

    val encoded = PiOmlxModelCatalog.encodeGenerationModelId(selection)
    val launchEnvironmentValue = PiOmlxModelCatalog.toLaunchEnvironmentValue(selection)

    assertThat(encoded).startsWith("omlx:")
    assertThat(encoded).doesNotContain("local-api-key")
    assertThat(PiOmlxModelCatalog.decodeGenerationModelId(encoded)).isEqualTo(selection)
    assertThat(PiOmlxModelCatalog.decodeGenerationModelId("Qwen3.6-27B-MLX-8bit")).isNull()
    assertThat(launchEnvironmentValue)
      .contains(
        "\"baseUrl\":\"http://127.0.0.1:8000\"",
        "\"modelId\":\"Qwen3.6-27B-MLX-8bit\"",
        "\"tokenSource\":\"omlx-settings\"",
      )
      .doesNotContain("local-api-key")
  }

  @Test
  fun discoversPiAuthConnectionAndPrefersItOverMatchingOmlxSettings(): Unit = runBlocking(Dispatchers.Default) {
    val agentDir = tempDir.resolve("pi-agent")
    val piLocalKeyReference = '$'.toString() + "PI_LOCAL_KEY"
    val files = mapOf(
      agentDir.resolve("auth.json") to """
        {
          "http://127.0.0.1:8000/v1": {
            "type": "api_key",
            "key": "$piLocalKeyReference"
          }
        }
      """.trimIndent(),
      tempDir.resolve(".omlx/settings.json") to """
        {
          "server": {"host": "127.0.0.1", "port": 8000},
          "auth": {"api_key": "settings-key"}
        }
      """.trimIndent(),
    )
    val requestedConnections = mutableListOf<PiOmlxConnection>()
    val catalog = PiOmlxModelCatalog(
      environmentProvider = { mapOf("PI_CODING_AGENT_DIR" to agentDir.toString(), "PI_LOCAL_KEY" to "pi-auth-key") },
      userHomeProvider = { tempDir },
      fileTextReader = { path -> files[path] },
      modelsStatusFetcher = { connection ->
        requestedConnections += connection
        statusJson(modelId = "Qwen3.6-27B-MLX-8bit", displayName = "Qwen 27B", loaded = true)
      },
    )

    val models = catalog.listAvailableGenerationModels()

    assertThat(requestedConnections).containsExactly(
      PiOmlxConnection(
        baseUrl = "http://127.0.0.1:8000",
        tokenSource = PiOmlxTokenSource.PI_AUTH,
        apiKey = "pi-auth-key",
      )
    )
    assertThat(models.map { it.displayName }).containsExactly("Qwen 27B (oMLX)")
    assertThat(models.single().group).isEqualTo(AgentPromptGenerationModelGroup.LOCAL)
    assertThat(models.single().isDefault).isTrue()
    assertThat(models.single().supportedReasoningEfforts).isEqualTo(PI_SUPPORTED_REASONING_EFFORTS)
    assertThat(PiOmlxModelCatalog.decodeGenerationModelId(models.single().id)?.tokenSource).isEqualTo(PiOmlxTokenSource.PI_AUTH)
  }

  @Test
  fun fallsBackToMatchingOmlxSettingsWhenPiAuthConnectionIsUnavailable(): Unit = runBlocking(Dispatchers.Default) {
    val agentDir = tempDir.resolve("pi-agent")
    val files = mapOf(
      agentDir.resolve("auth.json") to """
        {
          "http://127.0.0.1:8000": {
            "type": "api_key",
            "key": "stale-pi-key"
          }
        }
      """.trimIndent(),
      tempDir.resolve(".omlx/settings.json") to """
        {
          "server": {"host": "127.0.0.1", "port": 8000},
          "auth": {"api_key": "settings-key"}
        }
      """.trimIndent(),
    )
    val requestedConnections = mutableListOf<PiOmlxConnection>()
    val catalog = PiOmlxModelCatalog(
      environmentProvider = { mapOf("PI_CODING_AGENT_DIR" to agentDir.toString()) },
      userHomeProvider = { tempDir },
      fileTextReader = { path -> files[path] },
      modelsStatusFetcher = { connection ->
        requestedConnections += connection
        when (connection.tokenSource) {
          PiOmlxTokenSource.PI_AUTH -> null
          PiOmlxTokenSource.OMLX_SETTINGS -> statusJson(modelId = "Qwen3.6-27B-MLX-8bit", displayName = "Qwen 27B", loaded = true)
        }
      },
    )

    val models = catalog.listAvailableGenerationModels()

    assertThat(requestedConnections).containsExactly(
      PiOmlxConnection("http://127.0.0.1:8000", PiOmlxTokenSource.PI_AUTH, "stale-pi-key"),
      PiOmlxConnection("http://127.0.0.1:8000", PiOmlxTokenSource.OMLX_SETTINGS, "settings-key"),
    )
    assertThat(models.map { it.displayName }).containsExactly("Qwen 27B (oMLX)")
    assertThat(PiOmlxModelCatalog.decodeGenerationModelId(models.single().id)?.tokenSource).isEqualTo(PiOmlxTokenSource.OMLX_SETTINGS)
  }

  @Test
  fun omitsReasoningEffortsForNonThinkingOmlxModels(): Unit = runBlocking(Dispatchers.Default) {
    val agentDir = tempDir.resolve("pi-agent")
    val files = mapOf(
      agentDir.resolve("auth.json") to """
        {
          "http://127.0.0.1:8000": {
            "type": "api_key",
            "key": "pi-key"
          }
        }
      """.trimIndent(),
    )
    val catalog = PiOmlxModelCatalog(
      environmentProvider = { mapOf("PI_CODING_AGENT_DIR" to agentDir.toString()) },
      userHomeProvider = { tempDir },
      fileTextReader = { path -> files[path] },
      modelsStatusFetcher = {
        statusJson(modelId = "FastModel", displayName = "Fast Model", loaded = true, thinking = false)
      },
    )

    val models = catalog.listAvailableGenerationModels()

    assertThat(models.single().supportedReasoningEfforts).isEmpty()
  }

  @Test
  fun discoversDistinctPiAuthAndOmlxSettingsConnections(): Unit = runBlocking(Dispatchers.Default) {
    val agentDir = tempDir.resolve("pi-agent")
    val files = mapOf(
      agentDir.resolve("auth.json") to """
        {
          "http://127.0.0.1:8000": {
            "type": "api_key",
            "key": "pi-key"
          }
        }
      """.trimIndent(),
      tempDir.resolve(".omlx/settings.json") to """
        {
          "server": {"host": "0.0.0.0", "port": 9000},
          "auth": {"api_key": "settings-key"}
        }
      """.trimIndent(),
    )
    val requestedConnections = mutableListOf<PiOmlxConnection>()
    val catalog = PiOmlxModelCatalog(
      environmentProvider = { mapOf("PI_CODING_AGENT_DIR" to agentDir.toString()) },
      userHomeProvider = { tempDir },
      fileTextReader = { path -> files[path] },
      modelsStatusFetcher = { connection ->
        requestedConnections += connection
        when (connection.baseUrl) {
          "http://127.0.0.1:8000" -> statusJson(modelId = "Alpha", displayName = "Alpha", loaded = false)
          "http://127.0.0.1:9000" -> statusJson(modelId = "Beta", displayName = "Beta", loaded = true)
          else -> error("Unexpected connection ${connection.baseUrl}")
        }
      },
    )

    val models = catalog.listAvailableGenerationModels()

    assertThat(requestedConnections).containsExactly(
      PiOmlxConnection("http://127.0.0.1:8000", PiOmlxTokenSource.PI_AUTH, "pi-key"),
      PiOmlxConnection("http://127.0.0.1:9000", PiOmlxTokenSource.OMLX_SETTINGS, "settings-key"),
    )
    assertThat(models.map { it.displayName }).containsExactly(
      "Alpha (oMLX http://127.0.0.1:8000)",
      "Beta (oMLX http://127.0.0.1:9000)",
    )
    assertThat(models.map { it.isDefault }).containsExactly(false, true)
    assertThat(models.map { it.supportedReasoningEfforts }).containsOnly(PI_SUPPORTED_REASONING_EFFORTS)
    assertThat(models.map { PiOmlxModelCatalog.decodeGenerationModelId(it.id)?.tokenSource })
      .containsExactly(PiOmlxTokenSource.PI_AUTH, PiOmlxTokenSource.OMLX_SETTINGS)
  }

  @Test
  fun ignoresMalformedConfigAndUnavailableEndpoints(): Unit = runBlocking(Dispatchers.Default) {
    val files = mapOf(
      tempDir.resolve(".pi/agent/auth.json") to "not-json",
      tempDir.resolve(".omlx/settings.json") to """
        {
          "server": {"host": "127.0.0.1", "port": 8000},
          "auth": {"api_key": "settings-key"}
        }
      """.trimIndent(),
    )
    val catalog = PiOmlxModelCatalog(
      environmentProvider = { emptyMap() },
      userHomeProvider = { tempDir },
      fileTextReader = { path -> files[path] },
      modelsStatusFetcher = { null },
    )

    assertThat(catalog.listAvailableGenerationModels()).isEmpty()
  }
}

private fun statusJson(modelId: String, displayName: String, loaded: Boolean, thinking: Boolean = true): String {
  return """
    {
      "models": [
        {
          "id": "$modelId",
          "display_name": "$displayName",
          "max_context_window": 262144,
          "max_tokens": 32768,
          "thinking_default": $thinking,
          "model_type": "llm",
          "config_model_type": "qwen3_5",
          "loaded": $loaded
        }
      ]
    }
  """.trimIndent()
}
