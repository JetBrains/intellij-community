// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import com.intellij.agent.workbench.prompt.core.withGroup
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.spawnProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import tools.jackson.core.json.JsonFactory
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

private val KNOWN_MODEL_LOG = logger<PiKnownModelCatalog>()
private val KNOWN_MODEL_JSON_FACTORY = JsonFactory()

internal class PiKnownModelCatalog(
  private val piListModelsRunner: suspend (String, String?, Map<String, String>) -> PiKnownModelCommandResult? = ::runPiKnownListModels,
) {
  suspend fun listAvailableGenerationModels(
    piExecutable: String,
    extensionPath: String?,
    extensionModels: List<AgentPromptGenerationModel>,
    jbCentralLaunchMetadata: PiJbCentralLaunchMetadata? = null,
  ): List<AgentPromptGenerationModel> {
    try {
      val extraEnvironment = buildPiKnownListModelsEnvironment(extensionModels, jbCentralLaunchMetadata)
      val output = piListModelsRunner(piExecutable, extensionPath?.takeIf { it.isNotBlank() }, extraEnvironment) ?: return emptyList()
      if (output.exitCode != 0) {
        KNOWN_MODEL_LOG.debug("Pi model catalog probe exited with ${output.exitCode}: ${output.stderr}")
        return emptyList()
      }
      val candidates = parsePiKnownListModels(output.stdout)
      return mergePiKnownModels(candidates, extensionModels, jbCentralLaunchMetadata)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      KNOWN_MODEL_LOG.debug("Failed to query Pi model catalog for $piExecutable", e)
      return emptyList()
    }
  }

  companion object {
    val DEFAULT: PiKnownModelCatalog = PiKnownModelCatalog()

    fun encodeGenerationModelId(selection: PiKnownModelSelection): String {
      val json = selection.toJsonString()
      val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
      return PI_KNOWN_GENERATION_MODEL_ID_PREFIX + encoded
    }

    fun decodeGenerationModelId(modelId: String?): PiKnownModelSelection? {
      val encoded = modelId
                      ?.takeIf { it.startsWith(PI_KNOWN_GENERATION_MODEL_ID_PREFIX) }
                      ?.removePrefix(PI_KNOWN_GENERATION_MODEL_ID_PREFIX)
                    ?: return null
      return try {
        val payload = String(Base64.getUrlDecoder().decode(encoded.padBase64Url()), StandardCharsets.UTF_8)
        val node = KNOWN_MODEL_JSON_FACTORY.parseJsonObject(payload) ?: return null
        val formatVersion = node.intValue("formatVersion") ?: return null
        if (formatVersion != PI_KNOWN_GENERATION_MODEL_FORMAT_VERSION) {
          return null
        }
        val provider = node.stringValue("provider")?.takeIf { it.isNotBlank() } ?: return null
        val modelIdValue = node.stringValue("modelId")?.takeIf { it.isNotBlank() } ?: return null
        val displayName = node.stringValue("displayName")?.takeIf { it.isNotBlank() } ?: defaultPiKnownDisplayName(provider, modelIdValue)
        PiKnownModelSelection(
          provider = provider,
          modelId = modelIdValue,
          displayName = displayName,
          reasoning = node.booleanValue("reasoning") == true,
        )
      }
      catch (_: Exception) {
        null
      }
    }
  }
}

internal data class PiKnownModelCommandResult(
  val exitCode: Int,
  val stdout: String,
  val stderr: String = "",
)

internal data class PiKnownModelSelection(
  val provider: String,
  val modelId: String,
  val displayName: String,
  val reasoning: Boolean,
)

internal data class PiKnownModelCandidate(
  val selection: PiKnownModelSelection,
)

internal data class PiKnownModelIdentity(
  val provider: String,
  val modelId: String,
)

internal data class PiListModelsRow(
  val provider: String,
  val modelId: String,
  val reasoning: Boolean,
  val contextWindow: Int? = null,
  val maxTokens: Int? = null,
  val supportsImages: Boolean = false,
)

internal fun parsePiKnownListModels(output: String): List<PiKnownModelCandidate> {
  return parsePiListModelsRows(output)
    .filterNot { row -> row.isHiddenPiReportedClaudeModel() }
    .map { row ->
      PiKnownModelCandidate(
        PiKnownModelSelection(
          provider = row.provider,
          modelId = row.modelId,
          displayName = defaultPiKnownDisplayName(row.provider, row.modelId),
          reasoning = row.reasoning,
        )
      )
    }
}

internal fun PiListModelsRow.isHiddenPiReportedClaudeModel(): Boolean {
  val modelId = modelId.trim().lowercase()
  if (!modelId.startsWith("claude")) {
    return false
  }
  if (CLAUDE_DATED_MODEL_REGEX.containsMatchIn(modelId)) {
    return true
  }
  val versionNumbers = CLAUDE_VERSION_NUMBER_REGEX.findAll(modelId)
    .mapNotNull { match -> match.value.toIntOrNull() }
    .filter { number -> number < 100 }
    .toList()
  val major = versionNumbers.getOrNull(0) ?: return false
  val minor = versionNumbers.getOrNull(1) ?: 0
  return major < 4 || major == 4 && minor < 6
}

internal fun parsePiListModelsRows(output: String): List<PiListModelsRow> {
  val lines = stripAnsiControlSequences(output).lineSequence()
    .map { line -> line.trimEnd() }
    .filter { line -> line.isNotBlank() }
    .toList()
  val headerIndex = lines.indexOfFirst { line -> line.trimStart().startsWith("provider") }
  if (headerIndex < 0) {
    return lines.mapNotNull(::parseWhitespacePiListModelsRow)
  }
  val columns = PiListModelsColumns.fromHeader(lines[headerIndex]) ?: return lines
    .asSequence()
    .drop(headerIndex + 1)
    .mapNotNull(::parseWhitespacePiListModelsRow)
    .toList()
  return lines.asSequence()
    .drop(headerIndex + 1)
    .mapNotNull { line -> columns.parseRow(line) }
    .toList()
}

internal fun mergePiKnownModels(
  candidates: List<PiKnownModelCandidate>,
  extensionModels: List<AgentPromptGenerationModel>,
  jbCentralLaunchMetadata: PiJbCentralLaunchMetadata? = null,
): List<AgentPromptGenerationModel> {
  val profileBackedJbCentralModelsAvailable = extensionModels.any { model -> model.isProfileBackedJbCentralModel() }
  val candidatesToMerge = if (profileBackedJbCentralModelsAvailable && jbCentralLaunchMetadata != null) {
    // Profile-backed Central rows are the authoritative availability list.
    // Keep PI's static Central-like rows only as the fallback when no profile-backed rows are available.
    candidates.filterNot { candidate -> candidate.selection.isJbCentralFallbackModel(jbCentralLaunchMetadata) }
  }
  else {
    candidates
  }

  val extensionModelsByIdentity = LinkedHashMap<PiKnownModelIdentity, AgentPromptGenerationModel>()
  for (model in extensionModels) {
    model.id.toPiKnownModelIdentity()?.let { identity -> extensionModelsByIdentity.putIfAbsent(identity, model) }
  }
  if (jbCentralLaunchMetadata != null) {
    for (model in candidatesToMerge.toJbCentralGenerationModels(jbCentralLaunchMetadata)) {
      model.id.toPiKnownModelIdentity()?.let { identity -> extensionModelsByIdentity.putIfAbsent(identity, model) }
    }
  }

  val modelsById = LinkedHashMap<String, AgentPromptGenerationModel>()
  for (candidate in candidatesToMerge) {
    val extensionModel = extensionModelsByIdentity.remove(candidate.selection.identity(jbCentralLaunchMetadata))
    val model = extensionModel ?: candidate.toGenerationModel()
    modelsById.putIfAbsent(model.id.trim(), model)
  }
  for (model in extensionModelsByIdentity.values) {
    modelsById.putIfAbsent(model.id.trim(), model)
  }
  return modelsById.values.toList()
}

private fun AgentPromptGenerationModel.isProfileBackedJbCentralModel(): Boolean {
  return PiJbCentralModelCatalog.decodeGenerationModelId(id)?.profileId != null
}

private fun PiKnownModelSelection.isJbCentralFallbackModel(launchMetadata: PiJbCentralLaunchMetadata): Boolean {
  return provider.isJbCentralListModelsProvider(launchMetadata) ||
         (provider == PI_OPENAI_CODEX_PROVIDER && PiJbCentralAgent.CODEX in launchMetadata.agents) ||
         (provider == PI_ANTHROPIC_PROVIDER && PiJbCentralAgent.CLAUDE_CODE in launchMetadata.agents)
}

private fun List<PiKnownModelCandidate>.toJbCentralGenerationModels(
  launchMetadata: PiJbCentralLaunchMetadata,
): List<AgentPromptGenerationModel> {
  return buildJbCentralGenerationModels(
    mapNotNull { candidate -> candidate.toJbCentralModelCandidate(launchMetadata) }
  )
}

private fun PiKnownModelCandidate.toJbCentralModelCandidate(launchMetadata: PiJbCentralLaunchMetadata): PiJbCentralModelCandidate? {
  // When Central profiles are unavailable, the Pi extension wires Central into PI built-ins for a static fallback catalog.
  // Recode those rows to the single JetBrains Central provider before exposing them in Agent Workbench.
  val knownSelection = selection.takeIf { it.isJbCentralFallbackModel(launchMetadata) } ?: return null
  val agent = knownSelection.toJbCentralFallbackAgent(launchMetadata.agents) ?: return null
  return PiJbCentralModelCandidate(
    PiJbCentralModelSelection(
      provider = launchMetadata.provider,
      modelId = knownSelection.modelId,
      displayName = knownSelection.modelId,
      jbCentralExecutable = launchMetadata.jbCentralExecutable,
      proxyPort = launchMetadata.proxyPort,
      agent = agent,
      reasoning = knownSelection.reasoning,
    )
  )
}

private fun PiKnownModelSelection.toJbCentralFallbackAgent(availableAgents: Set<PiJbCentralAgent>): PiJbCentralAgent? {
  return when (provider) {
    PI_OPENAI_CODEX_PROVIDER -> PiJbCentralAgent.CODEX.takeIf { it in availableAgents }
    PI_ANTHROPIC_PROVIDER -> PiJbCentralAgent.CLAUDE_CODE.takeIf { it in availableAgents }
    else -> modelId.toJbCentralAgent(availableAgents)
  }
}

internal fun String?.toPiKnownModelIdentity(): PiKnownModelIdentity? {
  PiOmlxModelCatalog.decodeGenerationModelId(this)?.let { selection ->
    return PiKnownModelIdentity(provider = selection.provider, modelId = selection.modelId)
  }
  PiJbCentralModelCatalog.decodeGenerationModelId(this)?.let { selection ->
    return PiKnownModelIdentity(provider = selection.provider, modelId = selection.modelId)
  }
  PiKnownModelCatalog.decodeGenerationModelId(this)?.let { selection ->
    return selection.identity()
  }
  return null
}

private fun PiKnownModelCandidate.toGenerationModel(): AgentPromptGenerationModel {
  return AgentPromptGenerationModel(
    id = PiKnownModelCatalog.encodeGenerationModelId(selection),
    displayName = selection.displayName,
    supportedReasoningEfforts = if (selection.reasoning) PI_SUPPORTED_REASONING_EFFORTS else emptySet(),
  ).withGroup(selection.toPromptGenerationModelGroup())
}

private fun PiKnownModelSelection.toPromptGenerationModelGroup(): AgentPromptGenerationModelGroup {
  return when (provider.lowercase()) {
    "ollama", "omlx", "lmstudio", "lm-studio", "local" -> AgentPromptGenerationModelGroup.LOCAL
    "openai", PI_OPENAI_CODEX_PROVIDER -> AgentPromptGenerationModelGroup.OPENAI
    PI_ANTHROPIC_PROVIDER -> AgentPromptGenerationModelGroup.CLAUDE_CODE
    else -> AgentPromptGenerationModelGroup.OTHER
  }
}

private fun PiKnownModelSelection.identity(launchMetadata: PiJbCentralLaunchMetadata? = null): PiKnownModelIdentity {
  if (launchMetadata != null && isJbCentralFallbackModel(launchMetadata)) {
    return PiKnownModelIdentity(provider = launchMetadata.provider, modelId = modelId)
  }
  return PiKnownModelIdentity(provider = provider, modelId = modelId)
}

internal fun defaultPiKnownDisplayName(provider: String, modelId: String): String {
  return "$modelId ($provider)"
}

private suspend fun runPiKnownListModels(
  piExecutable: String,
  extensionPath: String?,
  extraEnvironment: Map<String, String>,
): PiKnownModelCommandResult? {
  var process: EelProcess? = null
  var probeFailed = false
  val result = withTimeoutOrNull(PI_KNOWN_LIST_MODELS_TIMEOUT) {
    try {
      val eelApi = LocalEelDescriptor.toEelApi()
      val environment = readEnvironmentVariables(eelApi.exec) + extraEnvironment
      process = eelApi.exec
        .spawnProcess(piExecutable)
        .args(buildPiKnownListModelsArgs(extensionPath))
        .env(environment)
        .eelIt()
      process.awaitProcessResult()
    }
    catch (e: ExecuteProcessException) {
      probeFailed = true
      KNOWN_MODEL_LOG.debug("Failed to start Pi model catalog probe for $piExecutable", e)
      null
    }
  }
  if (result == null) {
    if (!probeFailed) {
      process?.kill()
      KNOWN_MODEL_LOG.debug("Timed out while querying Pi models for $piExecutable")
    }
    return null
  }
  return PiKnownModelCommandResult(
    exitCode = result.exitCode,
    stdout = String(result.stdout, Charsets.UTF_8),
    stderr = String(result.stderr, Charsets.UTF_8),
  )
}

private fun buildPiKnownListModelsArgs(extensionPath: String?): List<String> {
  return buildList {
    if (extensionPath != null) {
      add(PI_EXTENSION_FLAG)
      add(extensionPath)
    }
    add(PI_LIST_MODELS_FLAG)
  }
}

private fun buildPiKnownListModelsEnvironment(
  extensionModels: List<AgentPromptGenerationModel>,
  jbCentralLaunchMetadata: PiJbCentralLaunchMetadata?,
): Map<String, String> {
  val environment = LinkedHashMap<String, String>()
  // Pi's /model selector is backed by Pi's provider registry. The managed extension consumes this catalog during
  // --list-models and registers Agent Workbench-backed providers before Pi builds the visible model list.
  buildPiModelCatalogEnvironmentValueForGenerationModels(extensionModels)?.let { catalogEnvironmentValue ->
    environment[PI_MODEL_CATALOG_ENVIRONMENT_VARIABLE] = catalogEnvironmentValue
  }
  if (jbCentralLaunchMetadata != null) {
    environment[PI_JBCENTRAL_PROVIDER_ENVIRONMENT_VARIABLE] = PiJbCentralModelCatalog.toLaunchEnvironmentValue(jbCentralLaunchMetadata)
  }
  return environment
}

private suspend fun readEnvironmentVariables(exec: EelExecApi): Map<String, String> {
  return try {
    exec.environmentVariables().eelIt().await()
  }
  catch (e: EelExecApi.EnvironmentVariablesException) {
    KNOWN_MODEL_LOG.debug("Failed to read environment variables for Pi model catalog probe", e)
    emptyMap()
  }
}

private fun PiKnownModelSelection.toJsonString(): String {
  val writer = StringWriter()
  KNOWN_MODEL_JSON_FACTORY.createJsonGenerator(writer).use { generator ->
    generator.writeStartObject()
    generator.writeName("formatVersion")
    generator.writeNumber(PI_KNOWN_GENERATION_MODEL_FORMAT_VERSION)
    generator.writeName("provider")
    generator.writeString(provider)
    generator.writeName("modelId")
    generator.writeString(modelId)
    generator.writeName("displayName")
    generator.writeString(displayName)
    generator.writeName("reasoning")
    generator.writeBoolean(reasoning)
    generator.writeEndObject()
  }
  return writer.toString()
}

private fun String?.isPiListModelsTruthy(): Boolean {
  return equals("true", ignoreCase = true) || equals("yes", ignoreCase = true)
}

private data class PiListModelsColumns(
  val providerStart: Int,
  val modelStart: Int,
  val contextStart: Int,
  val maxOutStart: Int,
  val thinkingStart: Int,
  val imagesStart: Int,
) {
  fun parseRow(line: String): PiListModelsRow? {
    val provider = line.columnValue(providerStart, modelStart) ?: return null
    val modelId = line.columnValue(modelStart, contextStart) ?: return null
    val contextWindow = line.columnValue(contextStart, maxOutStart).parsePiListModelsSize()
    val maxTokens = line.columnValue(maxOutStart, thinkingStart).parsePiListModelsSize()
    val reasoning = line.columnValue(thinkingStart, imagesStart).isPiListModelsTruthy()
    val supportsImages = line.columnValue(imagesStart, line.length).isPiListModelsTruthy()
    return PiListModelsRow(
      provider = provider,
      modelId = modelId,
      reasoning = reasoning,
      contextWindow = contextWindow,
      maxTokens = maxTokens,
      supportsImages = supportsImages,
    )
  }

  companion object {
    fun fromHeader(header: String): PiListModelsColumns? {
      val providerStart = header.indexOf("provider")
      val modelStart = header.indexOf("model", providerStart + 1)
      val contextStart = header.indexOf("context", modelStart + 1)
      val maxOutStart = header.indexOf("max-out", contextStart + 1)
      val thinkingStart = header.indexOf("thinking", maxOutStart + 1)
      val imagesStart = header.indexOf("images", thinkingStart + 1)
      val starts = listOf(providerStart, modelStart, contextStart, maxOutStart, thinkingStart, imagesStart)
      if (starts.any { it < 0 } || starts.zipWithNext().any { (left, right) -> left >= right }) {
        return null
      }
      return PiListModelsColumns(providerStart, modelStart, contextStart, maxOutStart, thinkingStart, imagesStart)
    }
  }
}

private fun String.columnValue(start: Int, end: Int): String? {
  if (start >= length) {
    return null
  }
  return substring(start, minOf(end, length)).trim().takeIf { it.isNotBlank() }
}

private fun parseWhitespacePiListModelsRow(line: String): PiListModelsRow? {
  val trimmed = line.trim()
  if (trimmed.isEmpty() || trimmed.startsWith("provider")) {
    return null
  }
  val columns = trimmed.split(PI_LIST_MODELS_COLUMNS_REGEX)
  return PiListModelsRow(
    provider = columns.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null,
    modelId = columns.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null,
    reasoning = columns.getOrNull(4).isPiListModelsTruthy(),
    contextWindow = columns.getOrNull(2).parsePiListModelsSize(),
    maxTokens = columns.getOrNull(3).parsePiListModelsSize(),
    supportsImages = columns.getOrNull(5).isPiListModelsTruthy(),
  )
}

private fun String?.parsePiListModelsSize(): Int? {
  val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
  val lastChar = raw.last().lowercaseChar()
  val multiplier = when (lastChar) {
    'k' -> 1_000
    'm' -> 1_000_000
    else -> 1
  }
  val numberText = if (multiplier == 1) raw else raw.dropLast(1)
  val value = numberText.toDoubleOrNull() ?: return null
  return (value * multiplier).toInt().takeIf { it > 0 }
}

private fun stripAnsiControlSequences(text: String): String {
  return ANSI_CONTROL_SEQUENCE_REGEX.replace(text, "")
}

private val ANSI_CONTROL_SEQUENCE_REGEX = Regex("${'\u001B'}\\[[0-?]*[ -/]*[@-~]")
private val CLAUDE_DATED_MODEL_REGEX = Regex("""(?:^|[^0-9])20\d{6}(?:$|[^0-9])""")
private val CLAUDE_VERSION_NUMBER_REGEX = Regex("""\d+""")
private val PI_LIST_MODELS_COLUMNS_REGEX = Regex("\\s+")
private const val PI_OPENAI_CODEX_PROVIDER: String = "openai-codex"
private const val PI_ANTHROPIC_PROVIDER: String = "anthropic"
private const val PI_EXTENSION_FLAG: String = "--extension"
private const val PI_LIST_MODELS_FLAG: String = "--list-models"
private const val PI_KNOWN_GENERATION_MODEL_ID_PREFIX: String = "pi:"
private const val PI_KNOWN_GENERATION_MODEL_FORMAT_VERSION: Int = 1
private val PI_KNOWN_LIST_MODELS_TIMEOUT = 10.seconds
