// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.json.createJsonParser
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.sessions.util.JbCentralCliSupport
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
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

private val JBCENTRAL_LOG = logger<PiJbCentralModelCatalog>()
private val JBCENTRAL_JSON_FACTORY = JsonFactory()

internal class PiJbCentralModelCatalog(
  private val jbCentralExecutableResolver: () -> String? = JbCentralCliSupport::findExecutable,
  private val statusRunner: suspend (String) -> PiJbCentralCommandResult? = ::runJbCentralStatus,
  private val piListModelsRunner: suspend (String, String, PiJbCentralLaunchMetadata) -> PiJbCentralCommandResult? = ::runPiListModels,
) {
  suspend fun listAvailableGenerationModels(piExecutable: String, extensionPath: String?): List<AgentPromptGenerationModel> {
    val jbCentralExecutable = jbCentralExecutableResolver() ?: return emptyList()
    val launchMetadata = resolveLaunchMetadata(jbCentralExecutable) ?: return emptyList()
    val normalizedExtensionPath = extensionPath?.takeIf { it.isNotBlank() } ?: return emptyList()
    val output = piListModelsRunner(piExecutable, normalizedExtensionPath, launchMetadata) ?: return emptyList()
    if (output.exitCode != 0) {
      JBCENTRAL_LOG.debug("Pi JBCentral model catalog probe exited with ${output.exitCode}: ${output.stderr}")
      return emptyList()
    }
    val candidates = parsePiListModels(output.stdout, launchMetadata)
    val defaultSelection = candidates.firstOrNull { candidate -> candidate.selection.modelId == PI_JBCENTRAL_DEFAULT_MODEL_ID }?.selection
                           ?: candidates.firstOrNull()?.selection
    return candidates.map { candidate ->
      AgentPromptGenerationModel(
        id = encodeGenerationModelId(candidate.selection),
        displayName = "${candidate.selection.displayName} (JBCentral)",
        supportedReasoningEfforts = PI_SUPPORTED_REASONING_EFFORTS,
        isDefault = candidate.selection == defaultSelection,
      )
    }
  }

  private suspend fun resolveLaunchMetadata(jbCentralExecutable: String): PiJbCentralLaunchMetadata? {
    val statusResult = queryStatus(jbCentralExecutable) ?: return null
    return PiJbCentralLaunchMetadata(
      jbCentralExecutable = jbCentralExecutable,
      proxyPort = statusResult.proxyPort ?: PI_JBCENTRAL_DEFAULT_PROXY_PORT,
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
      return status.takeIf { it.codexWired }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      JBCENTRAL_LOG.debug("Failed to query JBCentral status for $jbCentralExecutable", e)
      return null
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
        val node = parseJsonObject(payload) ?: return null
        val formatVersion = node.intValue("formatVersion") ?: return null
        if (formatVersion != PI_JBCENTRAL_GENERATION_MODEL_FORMAT_VERSION) {
          return null
        }
        val provider = node.stringValue("provider")?.takeIf { it == PI_JBCENTRAL_PROVIDER_NAME } ?: return null
        val modelIdValue = node.stringValue("modelId")?.takeIf { it.isNotBlank() } ?: return null
        val displayName = node.stringValue("displayName")?.takeIf { it.isNotBlank() } ?: modelIdValue
        val jbCentralExecutable = node.stringValue("jbCentralExecutable")?.takeIf { it.isNotBlank() } ?: return null
        val proxyPort = node.intValue("proxyPort")?.takeIf { it > 0 } ?: PI_JBCENTRAL_DEFAULT_PROXY_PORT
        PiJbCentralModelSelection(
          provider = provider,
          modelId = modelIdValue,
          displayName = displayName,
          jbCentralExecutable = jbCentralExecutable,
          proxyPort = proxyPort,
        )
      }
      catch (_: Exception) {
        null
      }
    }

    fun toLaunchEnvironmentValue(selection: PiJbCentralModelSelection): String {
      return PiJbCentralLaunchMetadata(
        jbCentralExecutable = selection.jbCentralExecutable,
        proxyPort = selection.proxyPort,
      ).toJsonString()
    }
  }
}

internal data class PiJbCentralCommandResult(
  val exitCode: Int,
  val stdout: String,
  val stderr: String = "",
)

internal data class PiJbCentralStatus(
  val codexWired: Boolean,
  val proxyPort: Int?,
)

internal data class PiJbCentralLaunchMetadata(
  val jbCentralExecutable: String,
  val proxyPort: Int = PI_JBCENTRAL_DEFAULT_PROXY_PORT,
  val provider: String = PI_JBCENTRAL_PROVIDER_NAME,
)

internal data class PiJbCentralModelSelection(
  val provider: String,
  val modelId: String,
  val displayName: String,
  val jbCentralExecutable: String,
  val proxyPort: Int,
)

internal data class PiJbCentralModelCandidate(
  val selection: PiJbCentralModelSelection,
)

internal const val PI_JBCENTRAL_PROVIDER_ENVIRONMENT_VARIABLE: String = "AGENT_WORKBENCH_PI_JBCENTRAL_PROVIDER"

internal fun parseJbCentralStatus(output: String): PiJbCentralStatus {
  val normalizedOutput = stripAnsiControlSequences(output)
  return PiJbCentralStatus(
    codexWired = JBCENTRAL_CODEX_AGENT_REGEX.containsMatchIn(normalizedOutput),
    proxyPort = JBCENTRAL_PROXY_PORT_REGEX.find(normalizedOutput)?.groupValues?.getOrNull(1)?.toIntOrNull(),
  )
}

private fun stripAnsiControlSequences(text: String): String {
  return ANSI_CONTROL_SEQUENCE_REGEX.replace(text, "")
}

internal fun parsePiListModels(output: String, launchMetadata: PiJbCentralLaunchMetadata): List<PiJbCentralModelCandidate> {
  return output.lineSequence()
    .map { line -> line.trim() }
    .filter { line -> line.isNotEmpty() && !line.startsWith("provider") }
    .mapNotNull { line ->
      val columns = line.split(Regex("\\s+"))
      val provider = columns.getOrNull(0)?.takeIf { it == PI_JBCENTRAL_PROVIDER_NAME } ?: return@mapNotNull null
      val modelId = columns.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      PiJbCentralModelCandidate(
        PiJbCentralModelSelection(
          provider = provider,
          modelId = modelId,
          displayName = modelId,
          jbCentralExecutable = launchMetadata.jbCentralExecutable,
          proxyPort = launchMetadata.proxyPort,
        )
      )
    }
    .toList()
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
    generator.writeEndObject()
  }
  return writer.toString()
}

private fun parseJsonObject(text: String): Map<String, Any?>? {
  JBCENTRAL_JSON_FACTORY.createJsonParser(text).use { parser ->
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      return null
    }
    return parser.readObjectValue()
  }
}

@Suppress("DuplicatedCode")
private fun JsonParser.readJsonValue(): Any? {
  return when (currentToken()) {
    JsonToken.START_OBJECT -> readObjectValue()
    JsonToken.START_ARRAY -> readArrayValue()
    JsonToken.VALUE_STRING -> string
    JsonToken.VALUE_NUMBER_INT -> longValue
    JsonToken.VALUE_NUMBER_FLOAT -> doubleValue
    JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE -> booleanValue
    JsonToken.VALUE_NULL -> null
    else -> {
      skipChildren()
      null
    }
  }
}

@Suppress("DuplicatedCode")
private fun JsonParser.readObjectValue(): Map<String, Any?> {
  val result = LinkedHashMap<String, Any?>()
  while (true) {
    val token = nextToken() ?: return result
    if (token == JsonToken.END_OBJECT) {
      return result
    }
    if (token != JsonToken.PROPERTY_NAME) {
      skipChildren()
      continue
    }
    val fieldName = currentName()
    if (nextToken() == null) {
      return result
    }
    result[fieldName] = readJsonValue()
  }
}

private fun JsonParser.readArrayValue(): List<Any?> {
  val result = mutableListOf<Any?>()
  while (true) {
    val token = nextToken() ?: return result
    if (token == JsonToken.END_ARRAY) {
      return result
    }
    result += readJsonValue()
  }
}

private fun Map<*, *>.stringValue(key: String): String? {
  return when (val value = this[key]) {
    is String -> value
    is Number, is Boolean -> value.toString()
    else -> null
  }
}

private fun Map<*, *>.intValue(key: String): Int? {
  return when (val value = this[key]) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
    else -> null
  }
}

private fun JsonGenerator.writeNumberField(fieldName: String, value: Int) {
  writeName(fieldName)
  writeNumber(value)
}

private fun JsonGenerator.writeStringField(fieldName: String, value: String) {
  writeName(fieldName)
  writeString(value)
}

private fun String.padBase64Url(): String {
  val padding = (4 - length % 4) % 4
  return this + "=".repeat(padding)
}

private val JBCENTRAL_CODEX_AGENT_REGEX = Regex("""(?is)\bAgents\b.*\bCodex\b""")
private val JBCENTRAL_PROXY_PORT_REGEX = Regex("""(?i)\bport\s+(\d+)\b""")
private val ANSI_CONTROL_SEQUENCE_REGEX = Regex("${'\u001B'}\\[[0-?]*[ -/]*[@-~]")

private const val JBCENTRAL_STATUS_COMMAND: String = "status"
private const val PI_EXTENSION_FLAG: String = "--extension"
private const val PI_LIST_MODELS_FLAG: String = "--list-models"
private const val PI_JBCENTRAL_PROVIDER_NAME: String = "openai-codex"
private const val PI_JBCENTRAL_DEFAULT_MODEL_ID: String = "gpt-5.5"
private const val PI_JBCENTRAL_DEFAULT_PROXY_PORT: Int = 19516
private const val PI_JBCENTRAL_GENERATION_MODEL_ID_PREFIX: String = "jbcentral:"
private const val PI_JBCENTRAL_GENERATION_MODEL_FORMAT_VERSION: Int = 1
private val PI_JBCENTRAL_STATUS_TIMEOUT = 3.seconds
private val PI_JBCENTRAL_LIST_MODELS_TIMEOUT = 10.seconds
