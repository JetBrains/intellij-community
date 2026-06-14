// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import com.intellij.agent.workbench.prompt.core.withGroup
import com.intellij.agent.workbench.sessions.util.JbCentralCliSupport
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.spawnProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonGenerator
import tools.jackson.core.json.JsonFactory
import java.io.StringWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration as JavaDuration
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

private val JBCENTRAL_LOG = logger<PiJbCentralModelCatalog>()
private val JBCENTRAL_JSON_FACTORY = JsonFactory()

internal class PiJbCentralModelCatalog(
  private val jbCentralExecutableResolver: () -> String? = JbCentralCliSupport::findExecutable,
  private val statusRunner: suspend (String) -> PiJbCentralCommandResult? = ::runJbCentralStatus,
  private val proxyConfigReader: suspend () -> PiJbCentralProxyConfig? = ::readJbCentralProxyConfig,
  private val directProfileProbeEnabled: () -> Boolean = ::isJbCentralDirectProfileProbeEnabled,
  private val profileCatalogRunner: suspend (PiJbCentralLaunchMetadata) -> List<PiJbCentralModelCandidate> = { metadata ->
    if (directProfileProbeEnabled()) listJbCentralProfileModels(metadata, proxyConfigReader) else emptyList()
  },
  private val piListModelsRunner: suspend (String, String, PiJbCentralLaunchMetadata) -> PiJbCentralCommandResult? = ::runPiListModels,
) {
  suspend fun resolveLaunchMetadata(): PiJbCentralLaunchMetadata? {
    val jbCentralExecutable = jbCentralExecutableResolver() ?: return null
    return resolveLaunchMetadata(jbCentralExecutable)
  }

  suspend fun listAvailableGenerationModels(piExecutable: String, extensionPath: String?): List<AgentPromptGenerationModel> {
    val launchMetadata = resolveLaunchMetadata() ?: return emptyList()
    return listAvailableGenerationModels(piExecutable, extensionPath, launchMetadata)
  }

  suspend fun listAvailableGenerationModels(
    piExecutable: String,
    extensionPath: String?,
    launchMetadata: PiJbCentralLaunchMetadata,
  ): List<AgentPromptGenerationModel> {
    val normalizedExtensionPath = extensionPath?.takeIf { it.isNotBlank() } ?: return emptyList()
    val profileModels = listProfileGenerationModels(launchMetadata)
    if (profileModels.isNotEmpty()) {
      return profileModels
    }
    val output = piListModelsRunner(piExecutable, normalizedExtensionPath, launchMetadata) ?: return emptyList()
    if (output.exitCode != 0) {
      JBCENTRAL_LOG.debug("Pi JBCentral model catalog probe exited with ${output.exitCode}: ${output.stderr}")
      return emptyList()
    }
    val candidates = parsePiListModels(output.stdout, launchMetadata)
    return buildJbCentralGenerationModels(candidates)
  }

  suspend fun listProfileGenerationModels(launchMetadata: PiJbCentralLaunchMetadata): List<AgentPromptGenerationModel> {
    return buildJbCentralGenerationModels(queryProfileCatalog(launchMetadata))
  }

  private suspend fun resolveLaunchMetadata(jbCentralExecutable: String): PiJbCentralLaunchMetadata? {
    val statusResult = queryStatus(jbCentralExecutable) ?: return null
    return PiJbCentralLaunchMetadata(
      jbCentralExecutable = jbCentralExecutable,
      proxyPort = statusResult.proxyPort ?: PI_JBCENTRAL_DEFAULT_PROXY_PORT,
      agents = statusResult.agents,
    )
  }

  private suspend fun queryStatus(jbCentralExecutable: String): PiJbCentralStatus? {
    try {
      val result = statusRunner(jbCentralExecutable) ?: return null
      if (result.exitCode != 0) {
        JBCENTRAL_LOG.debug("JBCentral status probe exited with ${result.exitCode}: ${result.stderr}")
        return null
      }
      val status = parseJbCentralStatus(result.stdout + "\n" + result.stderr)
      return status.takeIf { it.agents.isNotEmpty() }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      JBCENTRAL_LOG.debug("Failed to query JBCentral status for $jbCentralExecutable", e)
      return null
    }
  }

  private suspend fun queryProfileCatalog(launchMetadata: PiJbCentralLaunchMetadata): List<PiJbCentralModelCandidate> {
    try {
      return profileCatalogRunner(launchMetadata)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      JBCENTRAL_LOG.debug("Failed to query JBCentral profiles for Pi model catalog", e)
      return emptyList()
    }
  }

  companion object {
    val DEFAULT: PiJbCentralModelCatalog = PiJbCentralModelCatalog()

    fun encodeGenerationModelId(selection: PiJbCentralModelSelection): String {
      val json = selection.toJsonString()
      val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
      return PI_JBCENTRAL_GENERATION_MODEL_ID_PREFIX + encoded
    }

    fun decodeGenerationModelId(modelId: String?): PiJbCentralModelSelection? {
      val encoded = modelId
                      ?.takeIf { it.startsWith(PI_JBCENTRAL_GENERATION_MODEL_ID_PREFIX) }
                      ?.removePrefix(PI_JBCENTRAL_GENERATION_MODEL_ID_PREFIX)
                    ?: return null
      return try {
        val payload = String(Base64.getUrlDecoder().decode(encoded.padBase64Url()), StandardCharsets.UTF_8)
        val node = JBCENTRAL_JSON_FACTORY.parseJsonObject(payload) ?: return null
        val formatVersion = node.intValue("formatVersion") ?: return null
        if (formatVersion != PI_JBCENTRAL_GENERATION_MODEL_FORMAT_VERSION) {
          return null
        }
        node.stringValue("provider")?.takeIf { it == PI_JBCENTRAL_PROVIDER_NAME } ?: return null
        val modelIdValue = node.stringValue("modelId")?.takeIf { it.isNotBlank() } ?: return null
        val displayName = node.stringValue("displayName")?.takeIf { it.isNotBlank() } ?: modelIdValue
        val jbCentralExecutable = node.stringValue("jbCentralExecutable")?.takeIf { it.isNotBlank() } ?: return null
        val proxyPort = node.intValue("proxyPort")?.takeIf { it > 0 } ?: PI_JBCENTRAL_DEFAULT_PROXY_PORT
        val agent = node.stringValue("agent")?.let(PiJbCentralAgent::fromId) ?: return null
        PiJbCentralModelSelection(
          provider = PI_JBCENTRAL_PROVIDER_NAME,
          modelId = modelIdValue,
          displayName = displayName,
          jbCentralExecutable = jbCentralExecutable,
          proxyPort = proxyPort,
          agent = agent,
          contextWindow = node.intValue("contextWindow")?.takeIf { it > 0 },
          maxTokens = node.intValue("maxTokens")?.takeIf { it > 0 },
          reasoning = node.booleanValue("reasoning", allowYes = true) == true,
          supportsImages = node.booleanValue("supportsImages", allowYes = true) == true,
          profileId = node.stringValue("profileId")?.takeIf { it.isNotBlank() },
        )
      }
      catch (_: Exception) {
        null
      }
    }

    fun toLaunchEnvironmentValue(selection: PiJbCentralModelSelection): String {
      return selection.toJsonString()
    }

    fun toLaunchEnvironmentValue(launchMetadata: PiJbCentralLaunchMetadata): String {
      return launchMetadata.toJsonString()
    }
  }
}

internal data class PiJbCentralCommandResult(
  val exitCode: Int,
  val stdout: String,
  val stderr: String = "",
)

internal data class PiJbCentralStatus(
  val agents: Set<PiJbCentralAgent>,
  val proxyPort: Int?,
)

@ApiStatus.Internal
enum class PiJbCentralAgent(val id: String, statusName: String) {
  CODEX("codex", "Codex"),
  CLAUDE_CODE("claude-code", "Claude Code"),
  ;

  private val statusRegex = Regex("""(?is)\bAgents\b.*\b${Regex.escape(statusName)}\b""")

  fun isPresentInStatus(output: String): Boolean {
    return statusRegex.containsMatchIn(output)
  }

  companion object {
    fun fromId(id: String): PiJbCentralAgent? {
      return entries.firstOrNull { agent -> agent.id == id }
    }
  }
}

@ApiStatus.Internal
data class PiJbCentralLaunchMetadata(
  val jbCentralExecutable: String,
  val proxyPort: Int = PI_JBCENTRAL_DEFAULT_PROXY_PORT,
  val provider: String = PI_JBCENTRAL_PROVIDER_NAME,
  val agents: Set<PiJbCentralAgent> = PI_JBCENTRAL_DEFAULT_AGENTS,
)

@ApiStatus.Internal
data class PiJbCentralModelSelection(
  val provider: String,
  val modelId: String,
  val displayName: String,
  val jbCentralExecutable: String,
  val proxyPort: Int,
  val agent: PiJbCentralAgent,
  val contextWindow: Int? = null,
  val maxTokens: Int? = null,
  val reasoning: Boolean = false,
  val supportsImages: Boolean = false,
  val profileId: String? = null,
)

@ApiStatus.Internal
data class PiJbCentralModelCandidate(
  val selection: PiJbCentralModelSelection,
)

@ApiStatus.Internal
data class PiJbCentralProxyConfig(
  val proxyPort: Int?,
  val proxySecret: String?,
)

internal data class PiJbCentralProxyAccess(
  val proxyPort: Int,
  val proxySecret: String,
)

internal const val PI_JBCENTRAL_PROVIDER_ENVIRONMENT_VARIABLE: String = "AGENT_WORKBENCH_PI_JBCENTRAL_PROVIDER"

internal fun parseJbCentralStatus(output: String): PiJbCentralStatus {
  val normalizedOutput = stripAnsiControlSequences(output)
  return PiJbCentralStatus(
    agents = parseJbCentralStatusAgents(normalizedOutput),
    proxyPort = JBCENTRAL_PROXY_PORT_REGEX.find(normalizedOutput)?.groupValues?.getOrNull(1)?.toIntOrNull(),
  )
}

internal fun isJbCentralDirectProfileProbeEnabled(): Boolean {
  return java.lang.Boolean.getBoolean(PI_JBCENTRAL_DIRECT_PROFILES_PROPERTY)
}

private fun parseJbCentralStatusAgents(output: String): Set<PiJbCentralAgent> {
  return PiJbCentralAgent.entries
    .filterTo(LinkedHashSet()) { agent -> agent.isPresentInStatus(output) }
}

private fun stripAnsiControlSequences(text: String): String {
  return ANSI_CONTROL_SEQUENCE_REGEX.replace(text, "")
}

internal fun parsePiListModels(output: String, launchMetadata: PiJbCentralLaunchMetadata): List<PiJbCentralModelCandidate> {
  return parsePiListModelsRows(output)
    .filterNot { row -> row.isHiddenPiReportedClaudeModel() }
    .mapNotNull { row ->
      if (!row.provider.isJbCentralListModelsProvider(launchMetadata)) {
        return@mapNotNull null
      }
      val agent = row.modelId.toJbCentralAgent(launchMetadata.agents) ?: return@mapNotNull null
      PiJbCentralModelCandidate(
        PiJbCentralModelSelection(
          provider = launchMetadata.provider,
          modelId = row.modelId,
          displayName = row.modelId,
          jbCentralExecutable = launchMetadata.jbCentralExecutable,
          proxyPort = launchMetadata.proxyPort,
          agent = agent,
          contextWindow = row.contextWindow,
          maxTokens = row.maxTokens,
          reasoning = row.reasoning,
          supportsImages = row.supportsImages,
        )
      )
    }
}

internal fun parseJbCentralProfiles(output: String, launchMetadata: PiJbCentralLaunchMetadata): List<PiJbCentralModelCandidate> {
  val root = JBCENTRAL_JSON_FACTORY.parseJsonObject(output) ?: return emptyList()
  val profiles = root.listValue("profiles") ?: return emptyList()
  val candidatesByIdentity = LinkedHashMap<Pair<PiJbCentralAgent, String>, PiJbCentralModelCandidate>()
  for (profile in profiles.filterIsInstance<Map<*, *>>()) {
    val candidate = profile.toJbCentralProfileCandidate(launchMetadata) ?: continue
    candidatesByIdentity.putIfAbsent(candidate.selection.agent to candidate.selection.modelId, candidate)
  }
  return candidatesByIdentity.values.toList()
}

internal fun parseJbCentralProxyConfig(output: String): PiJbCentralProxyConfig? {
  val root = JBCENTRAL_JSON_FACTORY.parseJsonObject(output) ?: return null
  val proxyPort = root.intValue("proxy_port")?.takeIf { it > 0 }
  val proxySecret = root.stringValue("proxy_secret")?.trim()?.takeIf { it.isNotBlank() }
  return PiJbCentralProxyConfig(
    proxyPort = proxyPort,
    proxySecret = proxySecret,
  ).takeIf { config -> config.proxyPort != null || config.proxySecret != null }
}

internal fun buildJbCentralGenerationModels(candidates: List<PiJbCentralModelCandidate>): List<AgentPromptGenerationModel> {
  val defaultSelection = candidates.firstOrNull { candidate -> candidate.selection.modelId == PI_JBCENTRAL_DEFAULT_MODEL_ID }?.selection
                         ?: candidates.firstOrNull()?.selection
  return candidates.map { candidate ->
    AgentPromptGenerationModel(
      id = PiJbCentralModelCatalog.encodeGenerationModelId(candidate.selection),
      displayName = "${candidate.selection.displayName} (JetBrains Central)",
      supportedReasoningEfforts = if (candidate.selection.reasoning) PI_SUPPORTED_REASONING_EFFORTS else emptySet(),
      isDefault = candidate.selection == defaultSelection,
    ).withGroup(candidate.selection.agent.toPromptGenerationModelGroup())
  }
}

private fun PiJbCentralAgent.toPromptGenerationModelGroup(): AgentPromptGenerationModelGroup {
  return when (this) {
    PiJbCentralAgent.CODEX -> AgentPromptGenerationModelGroup.OPENAI
    PiJbCentralAgent.CLAUDE_CODE -> AgentPromptGenerationModelGroup.CLAUDE_CODE
  }
}

internal fun String.toJbCentralAgent(availableAgents: Set<PiJbCentralAgent>): PiJbCentralAgent? {
  if (startsWith("claude", ignoreCase = true)) {
    return PiJbCentralAgent.CLAUDE_CODE.takeIf { it in availableAgents }
  }
  return PiJbCentralAgent.CODEX.takeIf { it in availableAgents }
}

private fun Map<*, *>.toJbCentralProfileCandidate(launchMetadata: PiJbCentralLaunchMetadata): PiJbCentralModelCandidate? {
  val profileId = stringValue("id")?.trim()?.takeIf { it.isNotBlank() } ?: return null
  if (profileId.contains(THIRD_PARTY_PROFILE_ID_DELIMITER) || isDeprecatedOrExperimental()) {
    return null
  }
  val agent = toJbCentralProfileAgent(launchMetadata.agents) ?: return null
  val modelId = (stringValue("providerModelID") ?: stringValue("providerModelId"))
                  ?.trim()
                  ?.takeIf { it.isNotBlank() }
                ?: profileId
  val displayName = stringValue("modelName")?.trim()?.takeIf { it.isNotBlank() } ?: modelId
  return PiJbCentralModelCandidate(
    PiJbCentralModelSelection(
      provider = launchMetadata.provider,
      modelId = modelId,
      displayName = displayName,
      jbCentralExecutable = launchMetadata.jbCentralExecutable,
      proxyPort = launchMetadata.proxyPort,
      agent = agent,
      contextWindow = intValue("contextLimit")?.takeIf { it > 0 },
      maxTokens = intValue("maxOutputTokens")?.takeIf { it > 0 },
      reasoning = supportsReasoningEffort(),
      supportsImages = supportsImageInput(),
      profileId = profileId,
    )
  )
}

private fun Map<*, *>.isDeprecatedOrExperimental(): Boolean {
  val lifeCycle = objectValue("lifeCycle")
  return booleanValue("deprecated", allowYes = true) == true ||
         booleanValue("experimental", allowYes = true) == true ||
         lifeCycle?.booleanValue("deprecated", allowYes = true) == true ||
         lifeCycle?.booleanValue("experimental", allowYes = true) == true
}

private fun Map<*, *>.toJbCentralProfileAgent(availableAgents: Set<PiJbCentralAgent>): PiJbCentralAgent? {
  val provider = stringValue("provider")
  if (provider.equals("OpenAI", ignoreCase = true)) {
    return PiJbCentralAgent.CODEX.takeIf { it in availableAgents && supportsFeature("Responses") }
  }
  if (provider.equals("Anthropic", ignoreCase = true)) {
    return PiJbCentralAgent.CLAUDE_CODE.takeIf {
      it in availableAgents && supportsFeature("Chat") && supportsToolCalling() && supportsSystemMessage()
    }
  }
  return null
}

private fun Map<*, *>.supportsFeature(featureName: String): Boolean {
  return stringListValue("features").any { feature -> feature.equals(featureName, ignoreCase = true) }
}

private fun Map<*, *>.supportsToolCalling(): Boolean {
  val chatDefinition = objectValue("chatDefinition") ?: return false
  return chatDefinition.stringListValue("roles").any { role -> role.equals("tool", ignoreCase = true) } &&
         chatDefinition.supportsParameter("llm.parameters.tools")
}

private fun Map<*, *>.supportsSystemMessage(): Boolean {
  return objectValue("chatDefinition")
    ?.stringListValue("roles")
    ?.any { role -> role.equals("system", ignoreCase = true) } == true
}

private fun Map<*, *>.supportsReasoningEffort(): Boolean {
  return objectValue("chatDefinition")?.supportsParameter("llm.parameters.reasoning-effort") == true
}

private fun Map<*, *>.supportsImageInput(): Boolean {
  return objectValue("chatDefinition")
    ?.objectValue("multimediaDataDefinition")
    ?.stringListValue("supportedTypes")
    ?.any { type -> type.startsWith("image/", ignoreCase = true) } == true
}

private fun Map<*, *>.supportsParameter(fqdn: String): Boolean {
  return listValue("parameters")?.any { parameter ->
    when (parameter) {
      is Map<*, *> -> parameter.stringValue("fqdn") == fqdn
      is String -> parameter == fqdn
      else -> false
    }
  } == true
}

private suspend fun runJbCentralStatus(executable: String): PiJbCentralCommandResult? {
  var process: EelProcess? = null
  var probeFailed = false
  val result = withTimeoutOrNull(PI_JBCENTRAL_STATUS_TIMEOUT) {
    try {
      val eelApi = LocalEelDescriptor.toEelApi()
      process = eelApi.exec.spawnProcess(executable).args(JBCENTRAL_STATUS_COMMAND).eelIt()
      process.awaitProcessResult()
    }
    catch (e: ExecuteProcessException) {
      probeFailed = true
      JBCENTRAL_LOG.debug("Failed to start JBCentral status probe for $executable", e)
      null
    }
  }
  if (result == null) {
    if (!probeFailed) {
      process?.kill()
      JBCENTRAL_LOG.debug("Timed out while querying JBCentral status for $executable")
    }
    return null
  }
  return PiJbCentralCommandResult(
    exitCode = result.exitCode,
    stdout = String(result.stdout, Charsets.UTF_8),
    stderr = String(result.stderr, Charsets.UTF_8),
  )
}

private suspend fun runPiListModels(
  piExecutable: String,
  extensionPath: String,
  launchMetadata: PiJbCentralLaunchMetadata,
): PiJbCentralCommandResult? {
  var process: EelProcess? = null
  var probeFailed = false
  val result = withTimeoutOrNull(PI_JBCENTRAL_LIST_MODELS_TIMEOUT) {
    try {
      val eelApi = LocalEelDescriptor.toEelApi()
      val environment = readEnvironmentVariables(eelApi.exec) +
                        mapOf(PI_JBCENTRAL_PROVIDER_ENVIRONMENT_VARIABLE to launchMetadata.toJsonString())
      process = eelApi.exec
        .spawnProcess(piExecutable)
        .args(PI_EXTENSION_FLAG, extensionPath, PI_LIST_MODELS_FLAG, PI_JBCENTRAL_PROVIDER_NAME)
        .env(environment)
        .eelIt()
      process.awaitProcessResult()
    }
    catch (e: ExecuteProcessException) {
      probeFailed = true
      JBCENTRAL_LOG.debug("Failed to start Pi JBCentral model catalog probe for $piExecutable", e)
      null
    }
  }
  if (result == null) {
    if (!probeFailed) {
      process?.kill()
      JBCENTRAL_LOG.debug("Timed out while querying Pi JBCentral models for $piExecutable")
    }
    return null
  }
  return PiJbCentralCommandResult(
    exitCode = result.exitCode,
    stdout = String(result.stdout, Charsets.UTF_8),
    stderr = String(result.stderr, Charsets.UTF_8),
  )
}

private suspend fun listJbCentralProfileModels(
  launchMetadata: PiJbCentralLaunchMetadata,
  proxyConfigReader: suspend () -> PiJbCentralProxyConfig?,
): List<PiJbCentralModelCandidate> {
  val proxyAccess = resolveJbCentralProxyAccess(launchMetadata, proxyConfigReader) ?: return emptyList()
  val profileLaunchMetadata = launchMetadata.copy(proxyPort = proxyAccess.proxyPort)
  for (path in JBCENTRAL_PROFILE_PATHS) {
    val url = buildJbCentralProfileUrl(proxyAccess, path)
    val result = fetchJbCentralProfiles(url) ?: continue
    if (result.statusCode in 200..299) {
      return parseJbCentralProfiles(result.body, profileLaunchMetadata)
    }
    JBCENTRAL_LOG.debug("JBCentral profiles probe exited with HTTP ${result.statusCode}")
  }
  return emptyList()
}

internal suspend fun resolveJbCentralProxyAccess(
  launchMetadata: PiJbCentralLaunchMetadata,
  proxyConfigReader: suspend () -> PiJbCentralProxyConfig?,
  proxyKeyRunner: suspend (String) -> PiJbCentralCommandResult? = ::runJbCentralProxyStartReturnKey,
): PiJbCentralProxyAccess? {
  val proxyConfig = try {
    proxyConfigReader()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    JBCENTRAL_LOG.debug("Failed to read JBCentral proxy config", e)
    null
  }
  val proxyPort = proxyConfig?.proxyPort ?: launchMetadata.proxyPort
  val proxyKeyResult = proxyKeyRunner(launchMetadata.jbCentralExecutable)
  if (proxyKeyResult?.exitCode == 0) {
    proxyKeyResult.stdout.trim().takeIf { it.isNotBlank() }?.let { proxySecret ->
      return PiJbCentralProxyAccess(proxyPort = proxyPort, proxySecret = proxySecret)
    }
  }
  else if (proxyKeyResult != null) {
    JBCENTRAL_LOG.debug("JBCentral proxy key probe exited with ${proxyKeyResult.exitCode}: ${proxyKeyResult.stderr}")
  }
  val proxySecret = proxyConfig?.proxySecret ?: return null
  return PiJbCentralProxyAccess(proxyPort = proxyPort, proxySecret = proxySecret)
}

private suspend fun readJbCentralProxyConfig(): PiJbCentralProxyConfig? {
  return withContext(Dispatchers.IO) {
    try {
      val eelApi = LocalEelDescriptor.toEelApi()
      parseJbCentralProxyConfig(EelFiles.readString(eelApi.userInfo.home.asNioPath().resolve(".wire").resolve("config.json")))
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      JBCENTRAL_LOG.debug("Failed to read JBCentral proxy config", e)
      null
    }
  }
}

private suspend fun runJbCentralProxyStartReturnKey(executable: String): PiJbCentralCommandResult? {
  var process: EelProcess? = null
  var probeFailed = false
  val result = withTimeoutOrNull(PI_JBCENTRAL_PROFILE_KEY_TIMEOUT) {
    try {
      val eelApi = LocalEelDescriptor.toEelApi()
      process = eelApi.exec
        .spawnProcess(executable)
        .args(JBCENTRAL_PROXY_START_RETURN_KEY_ARGS)
        .eelIt()
      process.awaitProcessResult()
    }
    catch (e: ExecuteProcessException) {
      probeFailed = true
      JBCENTRAL_LOG.debug("Failed to start JBCentral proxy key probe for $executable", e)
      null
    }
  }
  if (result == null) {
    if (!probeFailed) {
      process?.kill()
      JBCENTRAL_LOG.debug("Timed out while querying JBCentral proxy key for $executable")
    }
    return null
  }
  return PiJbCentralCommandResult(
    exitCode = result.exitCode,
    stdout = String(result.stdout, Charsets.UTF_8),
    stderr = String(result.stderr, Charsets.UTF_8),
  )
}

private suspend fun fetchJbCentralProfiles(url: String): PiJbCentralHttpResult? {
  return withContext(Dispatchers.IO) {
    try {
      val request = HttpRequest.newBuilder(URI.create(url))
        .timeout(PI_JBCENTRAL_PROFILE_HTTP_TIMEOUT)
        .header("Accept", "application/json")
        .GET()
        .build()
      val response = HttpClient.newBuilder()
        .connectTimeout(PI_JBCENTRAL_PROFILE_HTTP_TIMEOUT)
        .build()
        .send(request, HttpResponse.BodyHandlers.ofString())
      PiJbCentralHttpResult(response.statusCode(), response.body())
    }
    catch (e: Exception) {
      JBCENTRAL_LOG.debug("Failed to query JBCentral profiles endpoint", e)
      null
    }
  }
}

private suspend fun readEnvironmentVariables(exec: EelExecApi): Map<String, String> {
  return try {
    exec.environmentVariables().eelIt().await()
  }
  catch (e: EelExecApi.EnvironmentVariablesException) {
    JBCENTRAL_LOG.debug("Failed to read environment variables for Pi JBCentral model catalog probe", e)
    emptyMap()
  }
}

private fun PiJbCentralModelSelection.toJsonString(): String {
  val writer = StringWriter()
  JBCENTRAL_JSON_FACTORY.createJsonGenerator(writer).use { generator ->
    generator.writeStartObject()
    generator.writeNumberField("formatVersion", PI_JBCENTRAL_GENERATION_MODEL_FORMAT_VERSION)
    generator.writeStringField("provider", provider)
    generator.writeStringField("modelId", modelId)
    generator.writeStringField("displayName", displayName)
    generator.writeStringField("jbCentralExecutable", jbCentralExecutable)
    generator.writeNumberField("proxyPort", proxyPort)
    generator.writeStringField("agent", agent.id)
    contextWindow?.let { generator.writeNumberField("contextWindow", it) }
    maxTokens?.let { generator.writeNumberField("maxTokens", it) }
    generator.writeName("reasoning")
    generator.writeBoolean(reasoning)
    generator.writeName("supportsImages")
    generator.writeBoolean(supportsImages)
    profileId?.let { generator.writeStringField("profileId", it) }
    generator.writeEndObject()
  }
  return writer.toString()
}

private fun PiJbCentralLaunchMetadata.toJsonString(): String {
  val writer = StringWriter()
  JBCENTRAL_JSON_FACTORY.createJsonGenerator(writer).use { generator ->
    generator.writeStartObject()
    generator.writeNumberField("formatVersion", PI_JBCENTRAL_GENERATION_MODEL_FORMAT_VERSION)
    generator.writeStringField("provider", provider)
    generator.writeStringField("jbCentralExecutable", jbCentralExecutable)
    generator.writeNumberField("proxyPort", proxyPort)
    generator.writeJbCentralAgentsField(agents)
    generator.writeEndObject()
  }
  return writer.toString()
}

private fun JsonGenerator.writeJbCentralAgentsField(agents: Set<PiJbCentralAgent>) {
  writeName("agents")
  writeStartArray()
  agents.inJbCentralCatalogOrder().forEach { agent -> writeString(agent.id) }
  writeEndArray()
}

private fun Set<PiJbCentralAgent>.inJbCentralCatalogOrder(): List<PiJbCentralAgent> {
  return PiJbCentralAgent.entries.filter { agent -> agent in this }
}

private fun JsonGenerator.writeNumberField(fieldName: String, value: Int) {
  writeName(fieldName)
  writeNumber(value)
}

private fun JsonGenerator.writeStringField(fieldName: String, value: String) {
  writeName(fieldName)
  writeString(value)
}

private val JBCENTRAL_PROXY_PORT_REGEX = Regex("""(?i)\bport\s+(\d+)\b""")
private val ANSI_CONTROL_SEQUENCE_REGEX = Regex("${'\u001B'}\\[[0-?]*[ -/]*[@-~]")

private const val JBCENTRAL_STATUS_COMMAND: String = "status"
private val JBCENTRAL_PROXY_START_RETURN_KEY_ARGS: List<String> = listOf("proxy", "start", "--return-key")
private val JBCENTRAL_PROFILE_PATHS: List<String> = listOf(
  "user/v5/llm/profiles/v8",
  "api/ai/user/v5/llm/profiles/v8",
)
private const val PI_EXTENSION_FLAG: String = "--extension"
private const val PI_LIST_MODELS_FLAG: String = "--list-models"
internal const val PI_JBCENTRAL_DIRECT_PROFILES_PROPERTY: String = "agent.workbench.pi.jbcentral.direct.profiles.enabled"
internal const val PI_JBCENTRAL_PROVIDER_NAME: String = "JetBrains Central"
private const val PI_JBCENTRAL_DEFAULT_MODEL_ID: String = "gpt-5.5"
private const val PI_JBCENTRAL_DEFAULT_PROXY_PORT: Int = 19516
private const val PI_JBCENTRAL_GENERATION_MODEL_ID_PREFIX: String = "jbcentral:"
private const val PI_JBCENTRAL_GENERATION_MODEL_FORMAT_VERSION: Int = 2
private val PI_JBCENTRAL_STATUS_TIMEOUT = 3.seconds
private val PI_JBCENTRAL_LIST_MODELS_TIMEOUT = 10.seconds
private val PI_JBCENTRAL_PROFILE_KEY_TIMEOUT = 10.seconds
private val PI_JBCENTRAL_PROFILE_HTTP_TIMEOUT = JavaDuration.ofSeconds(10)
val PI_JBCENTRAL_DEFAULT_AGENTS: Set<PiJbCentralAgent> = setOf(PiJbCentralAgent.CODEX)
private const val THIRD_PARTY_PROFILE_ID_DELIMITER: Char = '/'

internal fun String.isJbCentralListModelsProvider(launchMetadata: PiJbCentralLaunchMetadata): Boolean {
  return this == launchMetadata.provider
}

private fun buildJbCentralProfileUrl(proxyAccess: PiJbCentralProxyAccess, path: String): String {
  return "http://127.0.0.1:${proxyAccess.proxyPort}/wire/${proxyAccess.proxySecret}/$path"
}

private data class PiJbCentralHttpResult(
  val statusCode: Int,
  val body: String,
)
