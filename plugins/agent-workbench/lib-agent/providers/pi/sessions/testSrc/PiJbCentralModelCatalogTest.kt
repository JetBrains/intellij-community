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

    assertThat(status.agents).containsExactly(PiJbCentralAgent.CODEX)
    assertThat(status.proxyPort).isEqualTo(19517)
    val esc = "\u001B"
    val coloredStatus = parseJbCentralStatus(
      """
        ${esc}[1mAgents${esc}[m    ${esc}[38;2;117;117;117mClaude Code, Codex${esc}[m
        Proxy running on port 19516
      """.trimIndent()
    )
    assertThat(coloredStatus.agents).containsExactly(PiJbCentralAgent.CODEX, PiJbCentralAgent.CLAUDE_CODE)
    assertThat(coloredStatus.proxyPort).isEqualTo(19516)
    val claudeOnlyStatus = parseJbCentralStatus("Agents: Claude Code on port 19516")
    assertThat(claudeOnlyStatus.agents).containsExactly(PiJbCentralAgent.CLAUDE_CODE)
    assertThat(parseJbCentralStatus("Agents: Junie on port 19516").agents).isEmpty()
    assertThat(parseJbCentralStatus("Agents: Codex").proxyPort).isNull()
  }

  @Test
  fun parsesJbCentralProxyConfig() {
    assertThat(
      parseJbCentralProxyConfig(
        """
          {
            "proxy_port": 19516,
            "proxy_secret": " wire-secret "
          }
        """.trimIndent()
      )
    ).isEqualTo(PiJbCentralProxyConfig(proxyPort = 19516, proxySecret = "wire-secret"))

    assertThat(parseJbCentralProxyConfig("{\"proxy_secret\":\"wire-secret\"}"))
      .isEqualTo(PiJbCentralProxyConfig(proxyPort = null, proxySecret = "wire-secret"))
    assertThat(parseJbCentralProxyConfig("{\"proxy_port\":\"19517\"}"))
      .isEqualTo(PiJbCentralProxyConfig(proxyPort = 19517, proxySecret = null))
    assertThat(parseJbCentralProxyConfig("{\"proxy_port\":0,\"proxy_secret\":\" \"}")).isNull()
  }

  @Test
  fun resolvesProxyAccessFromCommandKeyAndConfigPort(): Unit = runBlocking(Dispatchers.Default) {
    val access = resolveJbCentralProxyAccess(
      launchMetadata = PiJbCentralLaunchMetadata(jbCentralExecutable = "/usr/local/bin/jbcentral", proxyPort = 19516),
      proxyConfigReader = { PiJbCentralProxyConfig(proxyPort = 19517, proxySecret = "config-secret") },
      proxyKeyRunner = { executable ->
        assertThat(executable).isEqualTo("/usr/local/bin/jbcentral")
        PiJbCentralCommandResult(exitCode = 0, stdout = " command-secret \n")
      },
    )

    assertThat(access).isEqualTo(PiJbCentralProxyAccess(proxyPort = 19517, proxySecret = "command-secret"))
  }

  @Test
  fun resolvesProxyAccessFromConfigWhenCommandCannotReturnKey(): Unit = runBlocking(Dispatchers.Default) {
    val access = resolveJbCentralProxyAccess(
      launchMetadata = PiJbCentralLaunchMetadata(jbCentralExecutable = "/usr/local/bin/jbcentral", proxyPort = 19516),
      proxyConfigReader = { PiJbCentralProxyConfig(proxyPort = 19517, proxySecret = "config-secret") },
      proxyKeyRunner = { PiJbCentralCommandResult(exitCode = 1, stdout = "", stderr = "proxy already running") },
    )

    assertThat(access).isEqualTo(PiJbCentralProxyAccess(proxyPort = 19517, proxySecret = "config-secret"))
  }

  @Test
  fun directProfilesProbeIsDisabledByDefault(): Unit = runBlocking(Dispatchers.Default) {
    var proxyConfigQueried = false
    val catalog = PiJbCentralModelCatalog(
      proxyConfigReader = {
        proxyConfigQueried = true
        PiJbCentralProxyConfig(proxyPort = 19517, proxySecret = "config-secret")
      },
    )

    withDirectProfilesProperty(null) {
      val models = catalog.listProfileGenerationModels(
        PiJbCentralLaunchMetadata(jbCentralExecutable = "/usr/local/bin/jbcentral")
      )
      assertThat(models).isEmpty()
    }
    assertThat(proxyConfigQueried).isFalse()
  }

  @Test
  fun readsDirectProfilesProbeSystemProperty() {
    withDirectProfilesProperty(null) {
      assertThat(isJbCentralDirectProfileProbeEnabled()).isFalse()
    }
    withDirectProfilesProperty("false") {
      assertThat(isJbCentralDirectProfileProbeEnabled()).isFalse()
    }
    withDirectProfilesProperty("true") {
      assertThat(isJbCentralDirectProfileProbeEnabled()).isTrue()
    }
  }

  @Test
  fun parsesPiListModelsAndFiltersJetBrainsCentralRows() {
    val metadata = PiJbCentralLaunchMetadata(
      jbCentralExecutable = "/usr/local/bin/jbcentral",
      proxyPort = 19516,
      agents = setOf(PiJbCentralAgent.CODEX, PiJbCentralAgent.CLAUDE_CODE),
    )

    val models = parsePiListModels(
      """
        provider                model                         context  max-out  thinking  images
        openai-codex            gpt-5.4                       400000   128000   true      true
        anthropic               claude-3-5-haiku-20241022     200000   64000    false     true
        anthropic               claude-fable-5                200000   64000    false     true
        openai                  gpt-4.1                       128000   16384    false     true
        JetBrains Central       gpt-5.5                       400000   128000   true      true
        JetBrains Central       claude-3-5-haiku-20241022     200000   64000    false     true
        JetBrains Central       claude-sonnet-4-5             200000   64000    false     true
        JetBrains Central       claude-sonnet-4-6-20250929    200000   64000    false     true
        JetBrains Central       claude-sonnet-4-6             200000   64000    false     true
        JetBrains Central       claude-opus-4-8               200000   64000    false     true
      """.trimIndent(),
      metadata,
    )

    assertThat(models.map { it.selection.modelId }).containsExactly("gpt-5.5", "claude-sonnet-4-6", "claude-opus-4-8")
    assertThat(models.map { it.selection.provider }).containsOnly("JetBrains Central")
    assertThat(models.map { it.selection.jbCentralExecutable }).containsOnly("/usr/local/bin/jbcentral")
    assertThat(models.map { it.selection.proxyPort }).containsOnly(19516)
    assertThat(models.map { it.selection.agent }).containsExactly(
      PiJbCentralAgent.CODEX,
      PiJbCentralAgent.CLAUDE_CODE,
      PiJbCentralAgent.CLAUDE_CODE,
    )
    assertThat(models.map { it.selection.reasoning }).containsExactly(true, false, false)
    assertThat(models.map { it.selection.supportsImages }).containsExactly(true, true, true)
  }

  @Test
  fun parsesProfilesV8AndFiltersCompatibleCentralProfiles() {
    val metadata = PiJbCentralLaunchMetadata(
      jbCentralExecutable = "/usr/local/bin/jbcentral",
      proxyPort = 19516,
      agents = setOf(PiJbCentralAgent.CODEX, PiJbCentralAgent.CLAUDE_CODE),
    )

    val models = parseJbCentralProfiles(
      """
        {
          "profiles": [
            {
              "id": "openai-gpt-5-5",
              "providerModelID": "gpt-5.5",
              "features": ["Responses", "Proxy"],
              "chatDefinition": {
                "parameters": [
                  {"fqdn": "llm.parameters.reasoning-effort"}
                ],
                "multimediaDataDefinition": {
                  "supportedTypes": ["image/png"]
                }
              },
              "deprecated": false,
              "contextLimit": 400000,
              "maxOutputTokens": 128000,
              "experimental": false,
              "provider": "OpenAI",
              "modelName": "GPT-5.5"
            },
            {
              "id": "anthropic-claude-4-5-sonnet",
              "features": ["Chat", "Proxy"],
              "chatDefinition": {
                "roles": ["user", "assistant", "system", "tool"],
                "parameters": [
                  {"fqdn": "llm.parameters.tools"}
                ]
              },
              "deprecated": false,
              "contextLimit": 200000,
              "maxOutputTokens": 64000,
              "experimental": false,
              "provider": "Anthropic",
              "modelName": "Claude 4.5 Sonnet"
            },
            {
              "id": "openai-gpt-4-1",
              "providerModelID": "gpt-4.1",
              "features": ["Chat"],
              "provider": "OpenAI",
              "modelName": "GPT-4.1"
            },
            {
              "id": "anthropic-claude-fable-5",
              "features": ["Chat", "Proxy"],
              "chatDefinition": {
                "roles": ["user", "assistant", "system", "tool"],
                "parameters": [
                  {"fqdn": "llm.parameters.tools"}
                ]
              },
              "provider": "Anthropic",
              "modelName": "Claude Fable 5",
              "lifeCycle": {"experimental": true}
            },
            {
              "id": "third-party/openai-gpt-5-5",
              "providerModelID": "gpt-5.5",
              "features": ["Responses"],
              "provider": "OpenAI",
              "modelName": "Third-party GPT-5.5"
            }
          ]
        }
      """.trimIndent(),
      metadata,
    )

    assertThat(models.map { it.selection.modelId }).containsExactly("gpt-5.5", "anthropic-claude-4-5-sonnet")
    assertThat(models.map { it.selection.displayName }).containsExactly("GPT-5.5", "Claude 4.5 Sonnet")
    assertThat(models.map { it.selection.agent }).containsExactly(PiJbCentralAgent.CODEX, PiJbCentralAgent.CLAUDE_CODE)
    assertThat(models.map { it.selection.reasoning }).containsExactly(true, false)
    assertThat(models.map { it.selection.supportsImages }).containsExactly(true, false)
    assertThat(models.map { it.selection.contextWindow }).containsExactly(400_000, 200_000)
    assertThat(models.map { it.selection.maxTokens }).containsExactly(128_000, 64_000)
    assertThat(models.map { it.selection.profileId }).containsExactly("openai-gpt-5-5", "anthropic-claude-4-5-sonnet")
  }

  @Test
  fun probesProfilesBeforePiStaticCatalog(): Unit = runBlocking(Dispatchers.Default) {
    var piCatalogQueried = false
    val catalog = PiJbCentralModelCatalog(
      jbCentralExecutableResolver = { "/usr/local/bin/jbcentral" },
      statusRunner = { executable ->
        assertThat(executable).isEqualTo("/usr/local/bin/jbcentral")
        PiJbCentralCommandResult(
          exitCode = 0,
          stdout = "Agents: Claude Code, Codex wired through proxy on port 19517",
        )
      },
      profileCatalogRunner = { metadata ->
        listOf(
          PiJbCentralModelCandidate(
            PiJbCentralModelSelection(
              provider = metadata.provider,
              modelId = "openai-gpt-5",
              displayName = "GPT-5",
              jbCentralExecutable = metadata.jbCentralExecutable,
              proxyPort = metadata.proxyPort,
              agent = PiJbCentralAgent.CODEX,
              reasoning = true,
              profileId = "openai-gpt-5",
            )
          )
        )
      },
      piListModelsRunner = { _, _, _ ->
        piCatalogQueried = true
        PiJbCentralCommandResult(exitCode = 0, stdout = "")
      },
    )

    val models = catalog.listAvailableGenerationModels(
      piExecutable = "/opt/pi/bin/pi",
      extensionPath = "/tmp/pi-extension/agent-workbench-extension.ts",
    )

    assertThat(models.map { it.displayName }).containsExactly("GPT-5 (JetBrains Central)")
    assertThat(PiJbCentralModelCatalog.decodeGenerationModelId(models.single().id)?.profileId).isEqualTo("openai-gpt-5")
    assertThat(piCatalogQueried).isFalse()
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
          stdout = "Agents: Claude Code, Codex wired through proxy on port 19517",
        )
      },
      profileCatalogRunner = { emptyList() },
      piListModelsRunner = { piExecutable, extensionPath, metadata ->
        listRequests += PiListModelsRequest(piExecutable, extensionPath, metadata)
        PiJbCentralCommandResult(
          exitCode = 0,
          stdout = """
            provider                model                         context  max-out  thinking  images
            openai-codex            gpt-5.4                       400000   128000   true      true
            openai-codex            gpt-5.5                       400000   128000   true      true
            anthropic               claude-fable-5                200000   64000    false     true
            JetBrains Central       gpt-5.5                       400000   128000   true      true
            JetBrains Central       claude-opus-4-8               200000   64000    false     true
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
        metadata = PiJbCentralLaunchMetadata(
          jbCentralExecutable = "/usr/local/bin/jbcentral",
          proxyPort = 19517,
          agents = setOf(PiJbCentralAgent.CODEX, PiJbCentralAgent.CLAUDE_CODE),
        ),
      )
    )
    assertThat(models.map { it.displayName }).containsExactly(
      "gpt-5.5 (JetBrains Central)",
      "claude-opus-4-8 (JetBrains Central)",
    )
    assertThat(models.map { it.supportedReasoningEfforts })
      .containsExactly(
        PI_SUPPORTED_REASONING_EFFORTS,
        emptySet(),
      )
    assertThat(models.map { it.isDefault }).containsExactly(true, false)
    assertThat(PiJbCentralModelCatalog.decodeGenerationModelId(models[0].id)).isEqualTo(
      PiJbCentralModelSelection(
        provider = "JetBrains Central",
        modelId = "gpt-5.5",
        displayName = "gpt-5.5",
        jbCentralExecutable = "/usr/local/bin/jbcentral",
        proxyPort = 19517,
        agent = PiJbCentralAgent.CODEX,
        contextWindow = 400_000,
        maxTokens = 128_000,
        reasoning = true,
        supportsImages = true,
      )
    )
    assertThat(PiJbCentralModelCatalog.toLaunchEnvironmentValue(checkNotNull(PiJbCentralModelCatalog.decodeGenerationModelId(models[0].id))))
      .contains(
        "\"provider\":\"JetBrains Central\"",
        "\"jbCentralExecutable\":\"/usr/local/bin/jbcentral\"",
        "\"proxyPort\":19517",
        "\"agent\":\"codex\"",
        "\"reasoning\":true",
        "\"supportsImages\":true",
      )
      .doesNotContain("wire-secret")
  }

  @Test
  fun resolvesLaunchMetadataWithoutPiCatalogProbe(): Unit = runBlocking(Dispatchers.Default) {
    var piCatalogQueried = false
    val catalog = PiJbCentralModelCatalog(
      jbCentralExecutableResolver = { "/usr/local/bin/jbcentral" },
      statusRunner = { executable ->
        assertThat(executable).isEqualTo("/usr/local/bin/jbcentral")
        PiJbCentralCommandResult(
          exitCode = 0,
          stdout = "Agents: Claude Code wired through proxy on port 19517",
        )
      },
      piListModelsRunner = { _, _, _ ->
        piCatalogQueried = true
        PiJbCentralCommandResult(exitCode = 0, stdout = "")
      },
    )

    assertThat(catalog.resolveLaunchMetadata()).isEqualTo(
      PiJbCentralLaunchMetadata(
        jbCentralExecutable = "/usr/local/bin/jbcentral",
        proxyPort = 19517,
        agents = setOf(PiJbCentralAgent.CLAUDE_CODE),
      )
    )
    assertThat(piCatalogQueried).isFalse()
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
  fun returnsEmptyWhenJbCentralDoesNotExposeSupportedAgents(): Unit = runBlocking(Dispatchers.Default) {
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
      statusRunner = { PiJbCentralCommandResult(exitCode = 0, stdout = "Agents: Claude Code") },
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
      statusRunner = { PiJbCentralCommandResult(exitCode = 0, stdout = "Agents: Claude Code") },
      profileCatalogRunner = { emptyList() },
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

private inline fun withDirectProfilesProperty(value: String?, action: () -> Unit) {
  val oldValue = System.getProperty(PI_JBCENTRAL_DIRECT_PROFILES_PROPERTY)
  try {
    if (value == null) {
      System.clearProperty(PI_JBCENTRAL_DIRECT_PROFILES_PROPERTY)
    }
    else {
      System.setProperty(PI_JBCENTRAL_DIRECT_PROFILES_PROPERTY, value)
    }
    action()
  }
  finally {
    if (oldValue == null) {
      System.clearProperty(PI_JBCENTRAL_DIRECT_PROFILES_PROPERTY)
    }
    else {
      System.setProperty(PI_JBCENTRAL_DIRECT_PROFILES_PROPERTY, oldValue)
    }
  }
}
