// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiJbCentralModelCatalogTest {
  @Test
  fun parsesJbCentralStatus() {
    val status = parseJbCentralStatus(
      """
        JBCentral is running
        Agents:
          Codex wired through proxy on port 19517
      """.trimIndent()
    )

    assertThat(status.codexWired).isTrue()
    assertThat(status.proxyPort).isEqualTo(19517)
    val esc = "\u001B"
    val coloredStatus = parseJbCentralStatus(
      """
        ${esc}[1mAgents${esc}[m    ${esc}[38;2;117;117;117mClaude Code, Codex${esc}[m
        Proxy running on port 19516
      """.trimIndent()
    )
    assertThat(coloredStatus.codexWired).isTrue()
    assertThat(coloredStatus.proxyPort).isEqualTo(19516)
    assertThat(parseJbCentralStatus("Agents: Junie on port 19516").codexWired).isFalse()
    assertThat(parseJbCentralStatus("Agents: Codex").proxyPort).isNull()
  }

  @Test
  fun parsesPiListModelsAndFiltersOpenAiCodexRows() {
    val metadata = PiJbCentralLaunchMetadata(jbCentralExecutable = "/usr/local/bin/jbcentral", proxyPort = 19516)

    val models = parsePiListModels(
      """
        provider       model      context  max-out  thinking  images
        openai-codex   gpt-5.4    400000   128000   true      true
        openai         gpt-4.1    128000   16384    false     true
        openai-codex   gpt-5.5    400000   128000   true      true
      """.trimIndent(),
      metadata,
    )

    assertThat(models.map { it.selection.modelId }).containsExactly("gpt-5.4", "gpt-5.5")
    assertThat(models.map { it.selection.provider }).containsOnly("openai-codex")
    assertThat(models.map { it.selection.jbCentralExecutable }).containsOnly("/usr/local/bin/jbcentral")
    assertThat(models.map { it.selection.proxyPort }).containsOnly(19516)
  }

  @Test
  fun probesJbCentralStatusThenPiCatalogAndDefaultsGpt55(): Unit = runBlocking(Dispatchers.Default) {
    val listRequests = mutableListOf<PiListModelsRequest>()
    val catalog = PiJbCentralModelCatalog(
      jbCentralExecutableResolver = { "/usr/local/bin/jbcentral" },
      statusRunner = { executable ->
        assertThat(executable).isEqualTo("/usr/local/bin/jbcentral")
        PiJbCentralCommandResult(
          exitCode = 0,
          stdout = "Agents: Codex wired through proxy on port 19517",
        )
      },
      piListModelsRunner = { piExecutable, extensionPath, metadata ->
        listRequests += PiListModelsRequest(piExecutable, extensionPath, metadata)
        PiJbCentralCommandResult(
          exitCode = 0,
          stdout = """
            provider       model      context  max-out  thinking  images
            openai-codex   gpt-5.4    400000   128000   true      true
            openai-codex   gpt-5.5    400000   128000   true      true
          """.trimIndent(),
        )
      },
    )

    val models = catalog.listAvailableGenerationModels(
      piExecutable = "/opt/pi/bin/pi",
      extensionPath = "/tmp/pi-extension/agent-workbench-extension.ts",
    )

    assertThat(listRequests).containsExactly(
      PiListModelsRequest(
        piExecutable = "/opt/pi/bin/pi",
        extensionPath = "/tmp/pi-extension/agent-workbench-extension.ts",
        metadata = PiJbCentralLaunchMetadata(jbCentralExecutable = "/usr/local/bin/jbcentral", proxyPort = 19517),
      )
    )
    assertThat(models.map { it.displayName }).containsExactly("gpt-5.4 (JBCentral)", "gpt-5.5 (JBCentral)")
    assertThat(models.map { it.supportedReasoningEfforts })
      .containsExactly(PI_SUPPORTED_REASONING_EFFORTS, PI_SUPPORTED_REASONING_EFFORTS)
    assertThat(models.map { it.isDefault }).containsExactly(false, true)
    assertThat(PiJbCentralModelCatalog.decodeGenerationModelId(models[1].id)).isEqualTo(
      PiJbCentralModelSelection(
        provider = "openai-codex",
        modelId = "gpt-5.5",
        displayName = "gpt-5.5",
        jbCentralExecutable = "/usr/local/bin/jbcentral",
        proxyPort = 19517,
      )
    )
    assertThat(PiJbCentralModelCatalog.toLaunchEnvironmentValue(checkNotNull(PiJbCentralModelCatalog.decodeGenerationModelId(models[1].id))))
      .contains(
        "\"provider\":\"openai-codex\"",
        "\"jbCentralExecutable\":\"/usr/local/bin/jbcentral\"",
        "\"proxyPort\":19517",
      )
      .doesNotContain("wire-secret")
  }

  @Test
  fun returnsEmptyWhenJbCentralCliIsMissing(): Unit = runBlocking(Dispatchers.Default) {
    val catalog = PiJbCentralModelCatalog(
      jbCentralExecutableResolver = { null },
      statusRunner = { error("status runner must not be called") },
      piListModelsRunner = { _, _, _ -> error("Pi list models runner must not be called") },
    )

    assertThat(catalog.listAvailableGenerationModels("pi", "/tmp/extension.ts")).isEmpty()
  }

  @Test
  fun returnsEmptyWhenJbCentralDoesNotExposeCodex(): Unit = runBlocking(Dispatchers.Default) {
    var piCatalogQueried = false
    val catalog = PiJbCentralModelCatalog(
      jbCentralExecutableResolver = { "/usr/local/bin/jbcentral" },
      statusRunner = { PiJbCentralCommandResult(exitCode = 0, stdout = "Agents: Junie on port 19516") },
      piListModelsRunner = { _, _, _ ->
        piCatalogQueried = true
        PiJbCentralCommandResult(exitCode = 0, stdout = "")
      },
    )

    assertThat(catalog.listAvailableGenerationModels("pi", "/tmp/extension.ts")).isEmpty()
    assertThat(piCatalogQueried).isFalse()
  }

  @Test
  fun returnsEmptyWhenExtensionPathIsMissing(): Unit = runBlocking(Dispatchers.Default) {
    var piCatalogQueried = false
    val catalog = PiJbCentralModelCatalog(
      jbCentralExecutableResolver = { "/usr/local/bin/jbcentral" },
      statusRunner = { PiJbCentralCommandResult(exitCode = 0, stdout = "Agents: Codex") },
      piListModelsRunner = { _, _, _ ->
        piCatalogQueried = true
        PiJbCentralCommandResult(exitCode = 0, stdout = "")
      },
    )

    assertThat(catalog.listAvailableGenerationModels("pi", " ")).isEmpty()
    assertThat(piCatalogQueried).isFalse()
  }

  @Test
  fun returnsEmptyWhenPiListModelsFails(): Unit = runBlocking(Dispatchers.Default) {
    val catalog = PiJbCentralModelCatalog(
      jbCentralExecutableResolver = { "/usr/local/bin/jbcentral" },
      statusRunner = { PiJbCentralCommandResult(exitCode = 0, stdout = "Agents: Codex") },
      piListModelsRunner = { _, _, _ -> PiJbCentralCommandResult(exitCode = 1, stdout = "", stderr = "unsupported") },
    )

    assertThat(catalog.listAvailableGenerationModels("pi", "/tmp/extension.ts")).isEmpty()
  }
}

private data class PiListModelsRequest(
  val piExecutable: String,
  val extensionPath: String,
  val metadata: PiJbCentralLaunchMetadata,
)
