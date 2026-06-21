// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import com.intellij.agent.workbench.prompt.core.withGroup
import com.intellij.openapi.diagnostic.logger
import tools.jackson.core.JsonGenerator
import tools.jackson.core.json.JsonFactory
import java.io.StringWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

private val OMLX_LOG = logger<PiOmlxModelCatalog>()
private val OMLX_JSON_FACTORY = JsonFactory()
private val OMLX_HTTP_CLIENT: HttpClient = HttpClient.newBuilder()
  .connectTimeout(Duration.ofSeconds(2))
  .build()

internal class PiOmlxModelCatalog(
  private val environmentProvider: () -> Map<String, String> = { System.getenv() },
  private val userHomeProvider: () -> Path? = ::resolveUserHome,
  private val fileTextReader: (Path) -> String? = ::readStringOrNull,
  private val modelsStatusFetcher: suspend (PiOmlxConnection) -> String? = ::fetchModelsStatus,
) {
  suspend fun listAvailableGenerationModels(): List<AgentPromptGenerationModel> {
    val connections = discoverConnections()
    if (connections.isEmpty()) {
      return emptyList()
    }

    val connectionsByBaseUrl = LinkedHashMap<String, MutableList<PiOmlxConnection>>()
    for (connection in connections) {
      connectionsByBaseUrl.getOrPut(connection.baseUrl) { mutableListOf() }.add(connection)
    }

    val multipleConnections = connectionsByBaseUrl.size > 1
    val discoveredModels = connectionsByBaseUrl.values.flatMap { fallbackConnections ->
      queryModelsFromFirstAvailableConnection(fallbackConnections, fallbackConnections.first().toProviderName(multipleConnections))
    }
      .sortedWith(compareBy<PiOmlxModelCandidate> { it.selection.displayName.lowercase() }.thenBy { it.selection.baseUrl })
    val defaultSelection = discoveredModels.firstOrNull { model -> model.loaded }?.selection
    return discoveredModels.map { model ->
      AgentPromptGenerationModel(
        id = encodeGenerationModelId(model.selection),
        displayName = model.toPromptDisplayName(multipleConnections),
        supportedReasoningEfforts = if (model.selection.reasoning) PI_SUPPORTED_REASONING_EFFORTS else emptySet(),
        isDefault = model.selection == defaultSelection,
      ).withGroup(AgentPromptGenerationModelGroup.LOCAL)
    }
  }

  private fun discoverConnections(): List<PiOmlxConnection> {
    val environment = environmentProvider()
    val userHome = userHomeProvider()
    val connections = mutableListOf<PiOmlxConnection>()

    resolvePiAgentDirectory(environment, userHome)
      ?.resolve(PI_AUTH_FILE_NAME)
      ?.let { path -> readPiAuthConnections(path, environment) }
      ?.let(connections::addAll)

    userHome
      ?.resolve(OMLX_SETTINGS_RELATIVE_PATH)
      ?.let(::readOmlxSettingsConnection)
      ?.let(connections::add)

    return connections
  }

  private suspend fun queryModelsFromFirstAvailableConnection(
    connections: List<PiOmlxConnection>,
    provider: String,
  ): List<PiOmlxModelCandidate> {
    for (connection in connections) {
      val models = queryModels(connection, provider)
      if (models.isNotEmpty()) {
        return models
      }
    }
    return emptyList()
  }

  private suspend fun queryModels(connection: PiOmlxConnection, provider: String): List<PiOmlxModelCandidate> {
    try {
      val response = modelsStatusFetcher(connection) ?: return emptyList()
      return parseOmlxModelsStatus(response, provider, connection.baseUrl, connection.tokenSource)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      OMLX_LOG.debug("Failed to query oMLX models from ${connection.baseUrl}", e)
      return emptyList()
    }
  }

  private fun readPiAuthConnections(path: Path, environment: Map<String, String>): List<PiOmlxConnection> {
    val root = readJsonObject(path) ?: return emptyList()
    val connections = mutableListOf<PiOmlxConnection>()
    for ((rawBaseUrl, rawCredential) in root) {
      val baseUrl = normalizeBaseUrl(rawBaseUrl) ?: continue
      val credential = rawCredential as? Map<*, *> ?: continue
      if (credential.stringValue("type") != "api_key") {
        continue
      }
      val key = credential.stringValue("key") ?: ""
      val apiKey = resolvePiAuthApiKey(key, environment) ?: continue
      connections += PiOmlxConnection(
        baseUrl = baseUrl,
        tokenSource = PiOmlxTokenSource.PI_AUTH,
        apiKey = apiKey,
      )
    }
    return connections
  }

  private fun readOmlxSettingsConnection(path: Path): PiOmlxConnection? {
    val root = readJsonObject(path) ?: return null
    val baseUrl = readOmlxSettingsBaseUrl(root) ?: return null
    val auth = root["auth"] as? Map<*, *>
    return PiOmlxConnection(
      baseUrl = baseUrl,
      tokenSource = PiOmlxTokenSource.OMLX_SETTINGS,
      apiKey = auth?.stringValue("api_key").orEmpty(),
    )
  }

  private fun readJsonObject(path: Path): Map<String, Any?>? {
    val text = fileTextReader(path) ?: return null
    return try {
      OMLX_JSON_FACTORY.parseJsonObject(text)
    }
    catch (e: Exception) {
      OMLX_LOG.debug("Failed to parse $path", e)
      null
    }
  }

  companion object {
    val DEFAULT: PiOmlxModelCatalog = PiOmlxModelCatalog()

    fun encodeGenerationModelId(selection: PiOmlxModelSelection): String {
      val json = selection.toJsonString()
      val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
      return PI_OMLX_GENERATION_MODEL_ID_PREFIX + encoded
    }

    fun decodeGenerationModelId(modelId: String?): PiOmlxModelSelection? {
      val encoded = modelId
                      ?.takeIf { it.startsWith(PI_OMLX_GENERATION_MODEL_ID_PREFIX) }
                      ?.removePrefix(PI_OMLX_GENERATION_MODEL_ID_PREFIX)
                    ?: return null
      return try {
        val payload = String(Base64.getUrlDecoder().decode(encoded.padBase64Url()), StandardCharsets.UTF_8)
        val node = OMLX_JSON_FACTORY.parseJsonObject(payload) ?: return null
        val formatVersion = node.intValue("formatVersion") ?: return null
        if (formatVersion != PI_OMLX_GENERATION_MODEL_FORMAT_VERSION) {
          return null
        }
        val baseUrl = normalizeBaseUrl(node.stringValue("baseUrl").orEmpty()) ?: return null
        val provider = node.stringValue("provider")?.takeIf { it.isNotBlank() } ?: PI_OMLX_PROVIDER_NAME
        val modelIdValue = node.stringValue("modelId")?.takeIf { it.isNotBlank() } ?: return null
        val displayName = node.stringValue("displayName")?.takeIf { it.isNotBlank() } ?: modelIdValue
        val tokenSource = PiOmlxTokenSource.fromJsonValue(node.stringValue("tokenSource")) ?: return null
        PiOmlxModelSelection(
          provider = provider,
          baseUrl = baseUrl,
          modelId = modelIdValue,
          displayName = displayName,
          tokenSource = tokenSource,
          contextWindow = node.intValue("contextWindow"),
          maxTokens = node.intValue("maxTokens"),
          reasoning = node.booleanValue("reasoning", trimString = true) == true,
          modelType = node.stringValue("modelType")?.takeIf { it.isNotBlank() },
        )
      }
      catch (_: Exception) {
        null
      }
    }

    fun toLaunchEnvironmentValue(selection: PiOmlxModelSelection): String {
      return selection.toJsonString()
    }
  }
}

internal data class PiOmlxConnection(
  val baseUrl: String,
  val tokenSource: PiOmlxTokenSource,
  val apiKey: String,
)

internal data class PiOmlxModelSelection(
  val provider: String = PI_OMLX_PROVIDER_NAME,
  val baseUrl: String,
  val modelId: String,
  val displayName: String,
  val tokenSource: PiOmlxTokenSource,
  val contextWindow: Int? = null,
  val maxTokens: Int? = null,
  val reasoning: Boolean = false,
  val modelType: String? = null,
)

internal enum class PiOmlxTokenSource(val jsonValue: String) {
  PI_AUTH("pi-auth"),
  OMLX_SETTINGS("omlx-settings"),
  ;

  companion object {
    fun fromJsonValue(value: String?): PiOmlxTokenSource? {
      return entries.firstOrNull { source -> source.jsonValue == value }
    }
  }
}

internal const val PI_OMLX_PROVIDER_ENVIRONMENT_VARIABLE: String = "AGENT_WORKBENCH_PI_OMLX_PROVIDER"

internal data class PiOmlxModelCandidate(
  val selection: PiOmlxModelSelection,
  val loaded: Boolean,
)

internal fun parseOmlxModelsStatus(
  responseJson: String,
  provider: String = PI_OMLX_PROVIDER_NAME,
  baseUrl: String,
  tokenSource: PiOmlxTokenSource,
): List<PiOmlxModelCandidate> {
  val root = OMLX_JSON_FACTORY.parseJsonObject(responseJson) ?: return emptyList()
  val models = root["models"] as? List<*> ?: return emptyList()
  return models
    .asSequence()
    .mapNotNull { rawEntry ->
      val entry = rawEntry as? Map<*, *> ?: return@mapNotNull null
      val modelId = entry.stringValue("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val rawModelType = entry.stringValue("model_type")?.lowercase()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      if (rawModelType != "llm" && rawModelType != "vlm") {
        return@mapNotNull null
      }
      val displayName = entry.stringValue("model_alias")?.takeIf { it.isNotBlank() }
                        ?: entry.stringValue("display_name")?.takeIf { it.isNotBlank() }
                        ?: modelId
      val configModelType = entry.stringValue("config_model_type")?.lowercase()?.takeIf { it.isNotBlank() }
      val modelType = if (configModelType != null && configModelType != rawModelType) "$rawModelType/$configModelType" else rawModelType
      PiOmlxModelCandidate(
        selection = PiOmlxModelSelection(
          provider = provider,
          baseUrl = baseUrl,
          modelId = modelId,
          displayName = displayName,
          tokenSource = tokenSource,
          contextWindow = entry.intValue("max_context_window"),
          maxTokens = entry.intValue("max_tokens"),
          reasoning = entry.booleanValue("thinking_default", trimString = true) == true,
          modelType = modelType,
        ),
        loaded = entry.booleanValue("loaded", trimString = true) == true,
      )
    }
    .toList()
}

private fun PiOmlxModelCandidate.toPromptDisplayName(multipleConnections: Boolean): String {
  val suffix = if (multipleConnections) selection.provider else PI_OMLX_PROVIDER_NAME
  return "${selection.displayName} ($suffix)"
}

private fun PiOmlxConnection.toProviderName(multipleConnections: Boolean): String {
  return if (multipleConnections) "$PI_OMLX_PROVIDER_NAME $baseUrl" else PI_OMLX_PROVIDER_NAME
}

private fun PiOmlxModelSelection.toJsonString(): String {
  val writer = StringWriter()
  OMLX_JSON_FACTORY.createJsonGenerator(writer).use { generator ->
    generator.writeStartObject()
    generator.writeName("formatVersion")
    generator.writeNumber(PI_OMLX_GENERATION_MODEL_FORMAT_VERSION)
    generator.writeName("provider")
    generator.writeString(provider)
    generator.writeName("baseUrl")
    generator.writeString(baseUrl)
    generator.writeName("modelId")
    generator.writeString(modelId)
    generator.writeName("displayName")
    generator.writeString(displayName)
    generator.writeName("tokenSource")
    generator.writeString(tokenSource.jsonValue)
    generator.writeNullableNumberField("contextWindow", contextWindow)
    generator.writeNullableNumberField("maxTokens", maxTokens)
    generator.writeName("reasoning")
    generator.writeBoolean(reasoning)
    generator.writeNullableStringField("modelType", modelType)
    generator.writeEndObject()
  }
  return writer.toString()
}

private fun resolvePiAgentDirectory(environment: Map<String, String>, userHome: Path?): Path? {
  val configuredAgentDir = environment[PI_CODING_AGENT_DIR_ENVIRONMENT_VARIABLE]
    ?.takeIf { it.isNotBlank() }
    ?.let(Path::of)
  return configuredAgentDir ?: userHome?.resolve(PI_AGENT_RELATIVE_PATH)
}

private fun resolveUserHome(): Path? {
  return System.getProperty("user.home")
    ?.takeIf { it.isNotBlank() }
    ?.let(Path::of)
}

private fun readStringOrNull(path: Path): String? {
  return try {
    Files.newBufferedReader(path).use { reader -> reader.readText() }
  }
  catch (_: Exception) {
    null
  }
}

private fun resolvePiAuthApiKey(rawKey: String, environment: Map<String, String>): String? {
  val trimmed = rawKey.trim()
  if (trimmed.startsWith("!")) {
    return null
  }
  if (trimmed.startsWith(PI_AUTH_ENV_BRACED_PREFIX) && trimmed.endsWith(PI_AUTH_ENV_BRACED_SUFFIX)) {
    return environment[trimmed.removePrefix(PI_AUTH_ENV_BRACED_PREFIX).removeSuffix(PI_AUTH_ENV_BRACED_SUFFIX)]
  }
  if (trimmed.startsWith('$')) {
    return environment[trimmed.drop(1)]
  }
  return rawKey
}

private fun readOmlxSettingsBaseUrl(root: Map<String, Any?>): String? {
  val server = root["server"] as? Map<*, *>
  val rawHost = server?.stringValue("host")?.takeIf { it.isNotBlank() } ?: "127.0.0.1"
  val port = server?.intValue("port")
  val endpoint = rawHost.toOmlxEndpointHost()
  val endpointWithScheme = if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) endpoint else "http://$endpoint"
  if (port == null) {
    return normalizeBaseUrl(endpointWithScheme)
  }
  val uri = runCatching { URI.create(endpointWithScheme) }.getOrNull() ?: return normalizeBaseUrl("$endpointWithScheme:$port")
  if (uri.port >= 0) {
    return normalizeBaseUrl(endpointWithScheme)
  }
  val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: ""
  val host = uri.host?.toUriHostLiteral() ?: return normalizeBaseUrl("$endpointWithScheme:$port")
  return normalizeBaseUrl("${uri.scheme}://$host:$port$path")
}

private fun String.toOmlxEndpointHost(): String {
  return when (trim()) {
    "0.0.0.0" -> "127.0.0.1"
    "::" -> "[::1]"
    else -> trim()
  }
}

private fun String.toUriHostLiteral(): String {
  return if (contains(':') && !startsWith('[')) "[$this]" else this
}

private fun normalizeBaseUrl(raw: String): String? {
  val trimmed = raw.trim().trimEnd('/')
  if (trimmed.isEmpty() || (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))) {
    return null
  }
  return trimmed.removeSuffix("/v1").trimEnd('/')
}

private fun fetchModelsStatus(connection: PiOmlxConnection): String? {
  return try {
    val requestBuilder = HttpRequest.newBuilder(URI.create("${connection.baseUrl}/v1/models/status"))
      .timeout(Duration.ofSeconds(5))
      .GET()
    if (connection.apiKey.isNotEmpty()) {
      requestBuilder.header("authorization", "Bearer ${connection.apiKey}")
    }
    val response = OMLX_HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    if (response.statusCode() !in 200..299) {
      OMLX_LOG.info("oMLX model status request to ${connection.baseUrl} returned HTTP ${response.statusCode()}")
      return null
    }
    response.body()
  }
  catch (e: Exception) {
    OMLX_LOG.info("Failed to query oMLX model status from ${connection.baseUrl}: ${e.message}")
    null
  }
}

private fun JsonGenerator.writeNullableStringField(fieldName: String, value: String?) {
  writeName(fieldName)
  if (value == null) {
    writeNull()
  }
  else {
    writeString(value)
  }
}

private fun JsonGenerator.writeNullableNumberField(fieldName: String, value: Int?) {
  writeName(fieldName)
  if (value == null) {
    writeNull()
  }
  else {
    writeNumber(value)
  }
}

private const val PI_CODING_AGENT_DIR_ENVIRONMENT_VARIABLE: String = "PI_CODING_AGENT_DIR"
private const val PI_AGENT_RELATIVE_PATH: String = ".pi/agent"
private const val PI_AUTH_FILE_NAME: String = "auth.json"
private const val PI_OMLX_PROVIDER_NAME: String = "oMLX"
private val PI_AUTH_ENV_BRACED_PREFIX: String = '$'.toString() + "{"
private const val PI_AUTH_ENV_BRACED_SUFFIX: String = "}"
private const val OMLX_SETTINGS_RELATIVE_PATH: String = ".omlx/settings.json"
private const val PI_OMLX_GENERATION_MODEL_ID_PREFIX: String = "omlx:"
private const val PI_OMLX_GENERATION_MODEL_FORMAT_VERSION: Int = 1
