// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.channels.EelSendChannelException
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.lines
import com.intellij.platform.eel.spawnProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<JunieAcpGenerationModelCatalog>()
private val JSON_MAPPER = ObjectMapper()

internal object JunieAcpGenerationModelCatalog {
  suspend fun listAvailableGenerationModels(executable: String, projectPath: String): List<AgentPromptGenerationModel> {
    var process: EelProcess? = null
    try {
      val response = withTimeoutOrNull(JUNIE_ACP_CATALOG_TIMEOUT) {
        val eelApi = LocalEelDescriptor.toEelApi()
        val acpProcess = try {
          eelApi.exec
            .spawnProcess(executable)
            .args(ACP_FLAG)
            .workingDirectory(EelPath.parse(projectPath, eelApi.descriptor))
            .eelIt()
        }
        catch (e: ExecuteProcessException) {
          throw IllegalStateException("Failed to start Junie ACP model catalog probe for $executable", e)
        }
        process = acpProcess

        acpProcess.sendJsonRpcRequest(
          id = INITIALIZE_REQUEST_ID,
          method = "initialize",
          params = linkedMapOf(
            "protocolVersion" to 1,
            "clientCapabilities" to linkedMapOf(
              "fs" to linkedMapOf(
                "readTextFile" to true,
                "writeTextFile" to true,
              ),
              "terminal" to false,
            ),
          ),
        )
        val initializeResponse = acpProcess.readJsonRpcResponse(INITIALIZE_REQUEST_ID)
                                 ?: error("Junie ACP initialize response is missing")
        parseJsonRpcSuccess(initializeResponse, "initialize")

        acpProcess.sendJsonRpcRequest(
          id = SESSION_NEW_REQUEST_ID,
          method = "session/new",
          params = linkedMapOf(
            "cwd" to projectPath,
            "mcpServers" to emptyList<Map<String, Any?>>(),
          ),
        )
        val sessionNewResponse = acpProcess.readJsonRpcResponse(SESSION_NEW_REQUEST_ID)
                                 ?: error("Junie ACP session/new response is missing")
        sessionNewResponse
      }
      if (response == null) {
        error("Timed out while querying Junie ACP generation models for $executable")
      }
      return parseJunieAcpGenerationModels(response)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.debug("Failed to query Junie ACP generation models for $executable", e)
      throw e
    }
    finally {
      process?.kill()
    }
  }
}

internal fun parseJunieAcpGenerationModels(sessionNewResponseJson: String): List<AgentPromptGenerationModel> {
  val response = parseJsonRpcSuccess(sessionNewResponseJson, "session/new")
  val result = response.path("result")
  if (result.isMissingNode || result.isNull || !result.isObject) {
    error("Junie ACP session/new result is missing")
  }
  val models = result.path("models")
  val currentModelId = models.path("currentModelId").textValueOrNull()
  val supportedReasoningEfforts = result.path("configOptions")
    .arrayElements()
    .firstOrNull { option -> option.path("category").textValueOrNull() == THOUGHT_LEVEL_CATEGORY }
    ?.path("options")
    ?.arrayElements()
    ?.mapNotNull { option -> option.path("value").textValueOrNull().toJunieReasoningEffort() }
    ?.toCollection(LinkedHashSet())
    .orEmpty()

  val acpModels = models.path("availableModels")
    .arrayElements()
    .mapNotNull { model ->
      val modelId = model.path("modelId").textValueOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val displayName = model.path("name").textValueOrNull()?.takeIf { it.isNotBlank() } ?: modelId
      AgentPromptGenerationModel(
        id = modelId,
        displayName = displayName,
        supportedReasoningEfforts = supportedReasoningEfforts,
        isDefault = currentModelId == modelId,
      )
    }
    .toList()
  if (acpModels.isNotEmpty()) {
    return acpModels
  }

  val modelOption = result.path("configOptions")
    .arrayElements()
    .firstOrNull { option -> option.path("id").textValueOrNull() == MODEL_CONFIG_OPTION_ID }
  val currentConfigModelId = modelOption?.path("currentValue")?.textValueOrNull()
  return modelOption
    ?.path("options")
    ?.arrayElements()
    ?.mapNotNull { option ->
      val modelId = option.path("value").textValueOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val displayName = option.path("name").textValueOrNull()?.takeIf { it.isNotBlank() } ?: modelId
      AgentPromptGenerationModel(
        id = modelId,
        displayName = displayName,
        supportedReasoningEfforts = configOptionModelReasoningEfforts(modelId, supportedReasoningEfforts),
        isDefault = currentConfigModelId == modelId,
      )
    }
    ?.toList()
    .orEmpty()
}

private fun parseJsonRpcSuccess(responseJson: String, method: String): JsonNode {
  val response = JSON_MAPPER.readTree(responseJson)
  val errorNode = response.path("error")
  if (!errorNode.isMissingNode && !errorNode.isNull) {
    error("Junie ACP $method failed: $errorNode")
  }
  return response
}

private suspend fun EelProcess.sendJsonRpcRequest(id: Int, method: String, params: Map<String, Any?>) {
  val request = JSON_MAPPER.writeValueAsString(
    linkedMapOf(
      "type" to "com.agentclientprotocol.rpc.JsonRpcRequest",
      "id" to id,
      "method" to method,
      "params" to params,
      "jsonrpc" to "2.0",
    )
  )
  try {
    stdin.sendWholeBuffer(ByteBuffer.wrap((request + "\n").toByteArray(Charsets.UTF_8)))
  }
  catch (e: EelSendChannelException) {
    throw IOException("Eel send channel closed", e)
  }
}

private suspend fun EelProcess.readJsonRpcResponse(id: Int): String? {
  return stdout.lines(Charsets.UTF_8).firstOrNull { line -> jsonRpcResponseId(line) == id }
}

private fun jsonRpcResponseId(line: String): Int? {
  val node = runCatching { JSON_MAPPER.readTree(line) }.getOrNull() ?: return null
  val id = node.path("id")
  return if (id.isNumber) id.asInt() else id.textValueOrNull()?.toIntOrNull()
}

private fun JsonNode.arrayElements(): Sequence<JsonNode> {
  if (!isArray) {
    return emptySequence()
  }
  return values().asSequence()
}

private fun JsonNode.textValueOrNull(): String? {
  return takeUnless { it.isMissingNode || it.isNull }?.asString()
}

private fun String?.toJunieReasoningEffort(): AgentPromptReasoningEffort? {
  return when (normalizeJunieToken()) {
    "low" -> AgentPromptReasoningEffort.LOW
    "medium" -> AgentPromptReasoningEffort.MEDIUM
    "high" -> AgentPromptReasoningEffort.HIGH
    "xhigh", "extrahigh" -> AgentPromptReasoningEffort.XHIGH
    else -> null
  }
}

private fun configOptionModelReasoningEfforts(
  modelId: String,
  explicitReasoningEfforts: Set<AgentPromptReasoningEffort>,
): Set<AgentPromptReasoningEffort> {
  if (explicitReasoningEfforts.isNotEmpty()) {
    return explicitReasoningEfforts
  }
  return if (modelId.supportsJunieXHighEffort()) JUNIE_CONFIG_OPTION_XHIGH_REASONING_EFFORTS
  else JUNIE_CONFIG_OPTION_DEFAULT_REASONING_EFFORTS
}

private fun String.supportsJunieXHighEffort(): Boolean {
  val normalizedModelId = normalizeJunieToken()
  return JUNIE_XHIGH_MODEL_ID_PREFIXES.any { prefix -> normalizedModelId.startsWith(prefix) }
}

private fun String?.normalizeJunieToken(): String {
  return this
    ?.trim()
    ?.lowercase()
    ?.replace("_", "")
    ?.replace("-", "")
    ?.replace(".", "")
    ?.replace(" ", "")
    .orEmpty()
}

private const val ACP_FLAG: String = "--acp=true"
private const val INITIALIZE_REQUEST_ID: Int = 1
private const val SESSION_NEW_REQUEST_ID: Int = 2
private const val MODEL_CONFIG_OPTION_ID: String = "model"
private const val THOUGHT_LEVEL_CATEGORY: String = "thought_level"
private val JUNIE_ACP_CATALOG_TIMEOUT = 3.seconds
private val JUNIE_CONFIG_OPTION_DEFAULT_REASONING_EFFORTS = setOf(
  AgentPromptReasoningEffort.LOW,
  AgentPromptReasoningEffort.MEDIUM,
  AgentPromptReasoningEffort.HIGH,
)
private val JUNIE_CONFIG_OPTION_XHIGH_REASONING_EFFORTS = JUNIE_CONFIG_OPTION_DEFAULT_REASONING_EFFORTS + AgentPromptReasoningEffort.XHIGH
private val JUNIE_XHIGH_MODEL_ID_PREFIXES = setOf(
  "gpt52",
  "gpt53codex",
  "gpt54",
  "gpt55",
  "claudeopus47",
  "claudeopus48",
  "grok420multiagent",
)
