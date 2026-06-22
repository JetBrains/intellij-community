// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.prompt.suggestions

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.platform.ai.agent.codex.common.CodexAppServerException
import com.intellij.platform.ai.agent.codex.common.CodexCliNotFoundException
import com.intellij.platform.ai.agent.codex.common.CodexCliUtils
import com.intellij.platform.ai.agent.codex.common.currentToken
import com.intellij.platform.ai.agent.codex.common.forEachObjectField
import com.intellij.platform.ai.agent.codex.common.readStringOrNull
import com.intellij.platform.ai.agent.codex.common.writeArrayField
import com.intellij.platform.ai.agent.codex.common.writeBooleanField
import com.intellij.platform.ai.agent.codex.common.writeFieldName
import com.intellij.platform.ai.agent.codex.common.writeObject
import com.intellij.platform.ai.agent.codex.common.writeObjectField
import com.intellij.platform.ai.agent.codex.common.writeStringField
import com.intellij.platform.ai.agent.json.createJsonGenerator
import com.intellij.platform.ai.agent.json.createJsonParser
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.awaitExit
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private const val REQUEST_TIMEOUT_MS = 30_000L
private const val PROCESS_TERMINATION_TIMEOUT_MS = 2_000L
private const val DEFAULT_IDLE_SHUTDOWN_TIMEOUT_MS = 60_000L
private const val READ_ONLY_EPHEMERAL_TURN_CLEANUP_TIMEOUT_MS = 500L
private const val CODEX_AUTO_UPDATE_CONFIG = "check_for_update_on_startup=false"
private const val CODEX_APP_SERVER_SYSTEM_DIR = "agent-workbench/codex-app-server"

private val LOG = logger<CodexPromptSuggestionAppServerClient>()

internal class CodexPromptSuggestionAppServerClient(
  private val coroutineScope: CoroutineScope,
  private val executablePathProvider: suspend () -> String? = { CodexCliUtils.findExecutableViaTerminalResolver() },
  private val environmentOverrides: Map<String, String> = emptyMap(),
  workingDirectory: Path? = null,
  idleShutdownTimeoutMs: Long = DEFAULT_IDLE_SHUTDOWN_TIMEOUT_MS,
) {
  private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
  private val requestCounter = AtomicLong(0)
  private val writeMutex = Mutex()
  private val startMutex = Mutex()
  private val initMutex = Mutex()
  private val protocol = PromptSuggestionAppServerProtocol()
  private val notificationsChannel = Channel<PromptSuggestionAppServerNotification>(capacity = Channel.UNLIMITED)
  private val workingDirectoryPath = workingDirectory
  private val idleShutdownTimeoutMs = idleShutdownTimeoutMs.coerceAtLeast(0)

  @Volatile
  private var process: Process? = null

  @Volatile
  private var initialized = false

  private var writer: BufferedWriter? = null
  private var readerJob: Job? = null
  private var stderrJob: Job? = null
  private var waitJob: Job? = null
  private var idleShutdownJob: Job? = null
  private var inFlightRequestCount: Int = 0

  suspend fun suggestPrompt(request: CodexPromptSuggestionRequest): AgentPromptSuggestionAiResult? {
    val payload = runReadOnlyEphemeralTurn(
      cwd = request.cwd,
      inputText = buildPromptSuggestionTurnInput(request),
      model = request.model,
      reasoningEffort = request.reasoningEffort,
      outputSchemaWriter = { generator -> writePromptSuggestionOutputSchema(generator, request) },
    )
    return payload?.let(::parseCodexPromptSuggestionResult)
  }

  internal suspend fun runReadOnlyEphemeralTurn(
    cwd: String?,
    inputText: String,
    model: String,
    reasoningEffort: String? = null,
    outputSchemaWriter: (JsonGenerator) -> Unit,
  ): String? {
    val session = startThread(cwd = cwd)
    var turnId: String? = null
    var terminalObserved = false
    try {
      val turn = request(
        method = "turn/start",
        paramsWriter = { generator ->
          generator.writeReadOnlyEphemeralTurnParams(
            threadId = session.threadId,
            inputText = inputText,
            model = model,
            reasoningEffort = reasoningEffort,
            outputSchemaWriter = outputSchemaWriter,
          )
        },
        resultParser = { parser -> protocol.parseTurnStartResult(parser) },
        defaultResult = null,
      ) ?: throw CodexAppServerException("Codex app-server returned empty turn/start result")
      turnId = turn.turnId
      val completion = awaitReadOnlyEphemeralTurnCompletion(threadId = session.threadId, turnId = turn.turnId)
      terminalObserved = true
      return completion.toAgentMessageText()
    }
    catch (t: Throwable) {
      if (turnId != null && !terminalObserved) {
        cleanupReadOnlyEphemeralTurn(threadId = session.threadId, turnId = turnId)
      }
      throw t
    }
  }

  fun shutdown() {
    stopProcess()
  }

  private suspend fun startThread(cwd: String?): PromptSuggestionStartedThreadSession {
    return request(
      method = "thread/start",
      paramsWriter = { generator ->
        generator.writeObject {
          cwd?.let { writeStringField("cwd", it) }
          writeStringField("approvalPolicy", "never")
          writeStringField("sandbox", "read-only")
          writeBooleanField("ephemeral", true)
          writeBooleanField("experimentalRawEvents", false)
          writeBooleanField("persistExtendedHistory", false)
        }
      },
      resultParser = { parser -> protocol.parseThreadStartResult(parser) },
      defaultResult = null,
    ) ?: throw CodexAppServerException("Codex app-server returned empty thread/start result")
  }

  private suspend fun <T> request(
    method: String,
    paramsWriter: ((JsonGenerator) -> Unit)? = null,
    ensureInitialized: Boolean = true,
    resultParser: (JsonParser) -> T,
    defaultResult: T,
  ): T {
    var id: String? = null
    onRequestStarted()
    try {
      if (ensureInitialized) ensureInitialized() else ensureProcess()
      id = requestCounter.incrementAndGet().toString()
      val deferred = CompletableDeferred<String>()
      pending[id] = deferred
      sendProtocolMessage(id = id, method = method, paramsWriter = paramsWriter)
      val response = withTimeout(REQUEST_TIMEOUT_MS.milliseconds) { deferred.await() }
      return protocol.parseResponse(response, resultParser, defaultResult)
    }
    catch (t: TimeoutCancellationException) {
      currentCoroutineContext().ensureActive()
      throw CodexAppServerException("Codex request timed out", t)
    }
    finally {
      id?.let { pending.remove(it) }
      onRequestCompleted()
    }
  }

  private suspend fun requestUnit(
    method: String,
    paramsWriter: ((JsonGenerator) -> Unit)? = null,
    ensureInitialized: Boolean = true,
  ) {
    request(
      method = method,
      paramsWriter = paramsWriter,
      ensureInitialized = ensureInitialized,
      resultParser = { parser ->
        parser.skipChildren()
        Unit
      },
      defaultResult = Unit,
    )
  }

  private suspend fun onRequestStarted() {
    startMutex.withLock {
      inFlightRequestCount += 1
      cancelIdleShutdownTimerLocked()
    }
  }

  private suspend fun onRequestCompleted() {
    startMutex.withLock {
      if (inFlightRequestCount > 0) {
        inFlightRequestCount -= 1
      }
      if (inFlightRequestCount == 0) {
        scheduleIdleShutdownLocked()
      }
    }
  }

  private fun scheduleIdleShutdownLocked() {
    cancelIdleShutdownTimerLocked()
    if (idleShutdownTimeoutMs <= 0) {
      stopProcess()
      return
    }
    idleShutdownJob = coroutineScope.launch(Dispatchers.IO) {
      delay(idleShutdownTimeoutMs.milliseconds)
      startMutex.withLock {
        if (inFlightRequestCount == 0) {
          stopProcess()
        }
      }
    }
  }

  private fun cancelIdleShutdownTimerLocked() {
    idleShutdownJob?.cancel()
    idleShutdownJob = null
  }

  private suspend fun sendProtocolMessage(
    id: String?,
    method: String,
    paramsWriter: ((JsonGenerator) -> Unit)? = null,
  ) {
    send { generator ->
      generator.writeProtocolMessage(id = id, method = method, paramsWriter = paramsWriter)
    }
  }

  private suspend fun send(payloadWriter: (JsonGenerator) -> Unit) {
    val activeProcess = ensureProcess()
    writeMutex.withLock {
      val out = writer ?: throw CodexAppServerException("Codex app-server output is not available")
      if (!activeProcess.isAlive) throw CodexAppServerException("Codex app-server is not running")
      protocol.writePayload(out, payloadWriter)
      out.newLine()
      out.flush()
    }
  }

  private suspend fun ensureInitialized() {
    ensureProcess()
    if (initialized) return
    initMutex.withLock {
      if (initialized) return
      requestUnit(
        method = "initialize",
        paramsWriter = { generator -> generator.writeInitializeParams() },
        ensureInitialized = false,
      )
      sendProtocolMessage(id = null, method = "initialized")
      initialized = true
    }
  }

  private suspend fun ensureProcess(): Process {
    val current = process
    if (current != null && current.isAlive) return current
    return startMutex.withLock {
      val existing = process
      if (existing != null && existing.isAlive) return existing
      startProcess()
    }
  }

  private suspend fun startProcess(): Process {
    val configuredExecutable = executablePathProvider()
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    val executable = configuredExecutable ?: CodexCliUtils.CODEX_COMMAND
    val requestedWorkingDirectory = workingDirectoryPath
    val effectiveWorkingDirectory = resolvePromptSuggestionAppServerWorkingDirectory(requestedWorkingDirectory)
    LOG.debug {
      "Starting Codex prompt-suggestion app-server(executable=$executable, executableSource=${if (configuredExecutable != null) "configured" else "default"}, requestedWorkingDirectory=${requestedWorkingDirectory ?: "<none>"}, effectiveWorkingDirectory=$effectiveWorkingDirectory, environmentOverrideCount=${environmentOverrides.size})"
    }
    val process = try {
      GeneralCommandLine(executable, "-c", CODEX_AUTO_UPDATE_CONFIG, "app-server")
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withEnvironment(environmentOverrides)
        .withWorkingDirectory(effectiveWorkingDirectory)
        .createProcess()
    }
    catch (t: Throwable) {
      if (configuredExecutable == null && t.isCodexExecutableNotFound()) {
        throw CodexCliNotFoundException()
      }
      throw CodexAppServerException("Failed to start Codex app-server from $executable", t)
    }
    this.process = process
    this.writer = BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
    startReader(process)
    startStderrReader(process)
    startWaiter(process)
    initialized = false
    return process
  }

  private fun startReader(process: Process) {
    readerJob?.cancel()
    readerJob = coroutineScope.launch(Dispatchers.IO) {
      val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
      try {
        while (isActive) {
          val line = runInterruptible { reader.readLine() } ?: break
          val payload = line.trim()
          if (payload.isEmpty()) continue
          handleMessage(payload)
        }
      }
      catch (e: Throwable) {
        if (!isActive || !process.isAlive) {
          return@launch
        }
        LOG.warn("Codex prompt-suggestion app-server stdout reader failed", e)
      }
    }
  }

  private fun startStderrReader(process: Process) {
    stderrJob?.cancel()
    stderrJob = coroutineScope.launch(Dispatchers.IO) {
      val reader = BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))
      try {
        while (isActive) {
          val line = runInterruptible { reader.readLine() } ?: break
          if (line.isNotBlank()) {
            LOG.debug { "Codex prompt-suggestion app-server stderr: $line" }
          }
        }
      }
      catch (e: Throwable) {
        if (!isActive || !process.isAlive) return@launch
        LOG.error("Codex prompt-suggestion app-server stderr reader failed", e)
      }
    }
  }

  private fun startWaiter(process: Process) {
    waitJob?.cancel()
    waitJob = coroutineScope.launch(Dispatchers.IO) {
      try {
        process.awaitExit()
      }
      catch (_: Throwable) {
        return@launch
      }
      handleProcessExit()
    }
  }

  private fun handleMessage(payload: String) {
    val id = try {
      protocol.parseMessageId(payload)
    }
    catch (e: Throwable) {
      LOG.error("Failed to parse Codex prompt-suggestion app-server payload: $payload", e)
      return
    }

    if (id != null) {
      pending.remove(id)?.complete(payload)
      return
    }

    val notification = try {
      protocol.parseNotification(payload)
    }
                       catch (e: Throwable) {
                         LOG.warn("Failed to parse Codex prompt-suggestion app-server notification: $payload", e)
                         null
                       } ?: return

    val result = notificationsChannel.trySend(notification)
    if (result.isFailure) {
      LOG.warn("Failed to enqueue Codex prompt-suggestion app-server notification: ${notification.method}")
    }
  }

  private suspend fun awaitReadOnlyEphemeralTurnCompletion(threadId: String, turnId: String): ReadOnlyEphemeralTurnCompletion {
    var agentMessageText: String? = null
    while (true) {
      val notification = try {
        withTimeout(REQUEST_TIMEOUT_MS.milliseconds) {
          notificationsChannel.receive()
        }
      }
      catch (t: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw CodexAppServerException("Codex read-only ephemeral turn timed out", t)
      }

      if (!notification.matchesTurn(threadId = threadId, turnId = turnId)) {
        continue
      }
      if (notification.method == "item/completed" && notification.agentMessageText != null) {
        agentMessageText = notification.agentMessageText
      }
      if (notification.method != "turn/completed") {
        continue
      }
      return ReadOnlyEphemeralTurnCompletion(
        turnStatus = notification.turnStatus,
        turnErrorMessage = notification.turnErrorMessage,
        agentMessageText = agentMessageText,
      )
    }
  }

  private suspend fun cleanupReadOnlyEphemeralTurn(threadId: String, turnId: String) {
    val cleanupConfirmed = withContext(NonCancellable) {
      try {
        sendInterruptTurnBestEffort(threadId = threadId, turnId = turnId)
        withTimeout(READ_ONLY_EPHEMERAL_TURN_CLEANUP_TIMEOUT_MS.milliseconds) {
          awaitReadOnlyEphemeralTurnCleanup(threadId = threadId, turnId = turnId)
        }
      }
      catch (_: TimeoutCancellationException) {
        LOG.warn("Failed to clean up Codex read-only ephemeral turn $threadId/$turnId; resetting client")
        false
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        LOG.warn("Failed to clean up Codex read-only ephemeral turn $threadId/$turnId; resetting client", t)
        false
      }
    }
    if (!cleanupConfirmed) {
      shutdown()
    }
  }

  private suspend fun sendInterruptTurnBestEffort(threadId: String, turnId: String) {
    val activeProcess = process ?: return
    writeMutex.withLock {
      val out = writer ?: return@withLock
      if (!activeProcess.isAlive) {
        return@withLock
      }
      val id = requestCounter.incrementAndGet().toString()
      protocol.writePayload(out) { generator ->
        generator.writeProtocolMessage(id = id, method = "turn/interrupt") { paramsGenerator ->
          paramsGenerator.writeObject {
            writeStringField("threadId", threadId)
            writeStringField("turnId", turnId)
          }
        }
      }
      out.newLine()
      out.flush()
    }
  }

  private suspend fun awaitReadOnlyEphemeralTurnCleanup(threadId: String, turnId: String): Boolean {
    while (true) {
      val notification = notificationsChannel.receive()
      if (!notification.matchesTurn(threadId = threadId, turnId = turnId)) {
        continue
      }
      if (notification.method != "turn/completed") {
        continue
      }
      return notification.turnStatus == "completed" ||
             notification.turnStatus == "interrupted" ||
             notification.turnStatus == "failed"
    }
  }

  private fun handleProcessExit() {
    val error = CodexAppServerException("Codex app-server terminated")
    pending.values.forEach { it.completeExceptionally(error) }
    pending.clear()
    clearProcessState()
  }

  private fun stopProcess() {
    val current = process ?: return
    clearProcessState()
    closePromptSuggestionAppServerProcessStreams(current)
    readerJob?.cancel()
    stderrJob?.cancel()
    waitJob?.cancel()
    stopPromptSuggestionAppServerProcess(current, coroutineScope)
  }

  private fun clearProcessState() {
    cancelIdleShutdownTimerLocked()
    clearNotificationsQueue()
    process = null
    writer = null
    initialized = false
    inFlightRequestCount = 0
  }

  private fun clearNotificationsQueue() {
    while (true) {
      if (notificationsChannel.tryReceive().isFailure) {
        return
      }
    }
  }
}

private class PromptSuggestionAppServerProtocol {
  private val jsonFactory = JsonFactory()

  fun writePayload(out: Writer, payloadWriter: (JsonGenerator) -> Unit) {
    val generator = jsonFactory.createJsonGenerator(out)
    payloadWriter(generator)
    generator.close()
  }

  @Suppress("DuplicatedCode")
  fun <T> parseResponse(payload: String, resultParser: (JsonParser) -> T, defaultResult: T): T {
    return parseObjectPayload(payload, defaultResult) { parser ->
      var result: T = defaultResult
      var hasResult = false
      var errorSeen = false
      var errorMessage: String? = null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "result" -> {
            result = resultParser(parser)
            hasResult = true
          }
          "error" -> {
            errorSeen = true
            errorMessage = readErrorMessage(parser)
          }
          else -> parser.skipChildren()
        }
        true
      }
      if (errorSeen) {
        throw CodexAppServerException(errorMessage ?: "Codex app-server error")
      }
      if (hasResult) result else defaultResult
    }
  }

  fun parseMessageId(payload: String): String? {
    return parseObjectPayload(payload, null) { parser ->
      var id: String? = null
      forEachObjectField(parser) { fieldName ->
        if (fieldName == "id") {
          id = readStringOrNull(parser)
          return@forEachObjectField false
        }
        parser.skipChildren()
        true
      }
      id
    }
  }

  private fun <T> parseObjectPayload(payload: String, defaultResult: T, objectParser: (JsonParser) -> T): T {
    return jsonFactory.createJsonParser(payload).use { parser ->
      if (parser.nextToken() == JsonToken.START_OBJECT) objectParser(parser) else defaultResult
    }
  }

  fun parseThreadStartResult(parser: JsonParser): PromptSuggestionStartedThreadSession? {
    if (parser.currentToken != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return null
    }
    val threadId = parseThreadIdFromObject(parser)
    return threadId?.let(::PromptSuggestionStartedThreadSession)
  }

  fun parseTurnStartResult(parser: JsonParser): PromptSuggestionTurnStartResult? {
    if (parser.currentToken != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return null
    }
    val turnId = parseTurnIdFromObject(parser)
    return turnId?.let(::PromptSuggestionTurnStartResult)
  }

  fun parseNotification(payload: String): PromptSuggestionAppServerNotification? {
    jsonFactory.createJsonParser(payload).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return null
      var hasResponseId = false
      var method: String? = null
      var params = PromptSuggestionNotificationParams()
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "id" -> {
            hasResponseId = parser.currentToken != JsonToken.VALUE_NULL
            parser.skipChildren()
          }
          "method" -> method = readStringOrNull(parser)
          "params" -> {
            val parsedMethod = method
            params = if (parser.currentToken == JsonToken.START_OBJECT) {
              parseNotificationParams(parser, parsedMethod)
            }
            else {
              parser.skipChildren()
              PromptSuggestionNotificationParams()
            }
          }
          else -> parser.skipChildren()
        }
        true
      }
      if (hasResponseId) return null
      val resolvedMethod = method?.trimToNull() ?: return null
      return PromptSuggestionAppServerNotification(
        method = resolvedMethod,
        threadId = params.threadId,
        turnId = params.turnId,
        turnStatus = params.turnStatus,
        turnErrorMessage = params.turnErrorMessage,
        agentMessageText = params.agentMessageText,
      )
    }
  }
}

private data class PromptSuggestionStartedThreadSession(@JvmField val threadId: String)

private data class PromptSuggestionTurnStartResult(@JvmField val turnId: String)

private data class PromptSuggestionAppServerNotification(
  @JvmField val method: String,
  @JvmField val threadId: String? = null,
  @JvmField val turnId: String? = null,
  @JvmField val turnStatus: String? = null,
  @JvmField val turnErrorMessage: String? = null,
  @JvmField val agentMessageText: String? = null,
)

private data class PromptSuggestionNotificationParams(
  @JvmField val threadId: String? = null,
  @JvmField val turnId: String? = null,
  @JvmField val turnStatus: String? = null,
  @JvmField val turnErrorMessage: String? = null,
  @JvmField val agentMessageText: String? = null,
)

private data class PromptSuggestionTurnObject(
  @JvmField val id: String?,
  @JvmField val status: String?,
  @JvmField val errorMessage: String?,
)

private data class PromptSuggestionItemObject(
  @JvmField val type: String?,
  @JvmField val text: String?,
)

private data class ReadOnlyEphemeralTurnCompletion(
  @JvmField val turnStatus: String?,
  @JvmField val turnErrorMessage: String?,
  @JvmField val agentMessageText: String?,
)

private fun PromptSuggestionAppServerNotification.matchesTurn(threadId: String, turnId: String): Boolean {
  return this.threadId == threadId && this.turnId == turnId
}

private fun ReadOnlyEphemeralTurnCompletion.toAgentMessageText(): String? {
  return when (turnStatus) {
    "completed", null -> agentMessageText
    "interrupted" -> null
    "failed" -> throw CodexAppServerException(turnErrorMessage ?: "Codex read-only ephemeral turn failed")
    else -> null
  }
}

private fun JsonGenerator.writeReadOnlyEphemeralTurnParams(
  threadId: String,
  inputText: String,
  model: String,
  reasoningEffort: String?,
  outputSchemaWriter: (JsonGenerator) -> Unit,
) {
  writeObject {
    writeStringField("threadId", threadId)
    writeArrayField("input") {
      writeObject {
        writeStringField("type", "text")
        writeStringField("text", inputText)
      }
    }
    writeStringField("model", model)
    reasoningEffort?.let { writeStringField("effort", it) }
    writeFieldName("outputSchema")
    outputSchemaWriter(this)
  }
}

private fun JsonGenerator.writeInitializeParams() {
  writeObject {
    writeObjectField("clientInfo") {
      writeStringField("name", "IntelliJ Agent Workbench")
      writeStringField("version", "1.0")
    }
    writeObjectField("capabilities") {
      writeBooleanField("experimentalApi", true)
    }
  }
}

private fun JsonGenerator.writeProtocolMessage(
  id: String?,
  method: String,
  paramsWriter: ((JsonGenerator) -> Unit)? = null,
) {
  writeObject {
    id?.let { writeStringField("id", it) }
    writeStringField("method", method)
    if (paramsWriter != null) {
      writeFieldName("params")
      paramsWriter(this)
    }
  }
}

private fun parseThreadIdFromObject(parser: JsonParser): String? {
  var threadId: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "threadId", "thread_id", "id" -> threadId = readNonBlankStringOrNull(parser) ?: threadId
      "thread", "data" -> threadId = parseIdObject(parser) ?: threadId
      else -> parser.skipChildren()
    }
    true
  }
  return threadId
}

private fun parseTurnIdFromObject(parser: JsonParser): String? {
  var turnId: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "turnId", "turn_id", "id" -> turnId = readNonBlankStringOrNull(parser) ?: turnId
      "turn", "data" -> turnId = parseIdObject(parser) ?: turnId
      else -> parser.skipChildren()
    }
    true
  }
  return turnId
}

private fun parseNotificationParams(parser: JsonParser, method: String?): PromptSuggestionNotificationParams {
  var threadId: String? = null
  var turnId: String? = null
  var turnStatus: String? = null
  var turnErrorMessage: String? = null
  var agentMessageText: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "threadId", "thread_id" -> threadId = readNonBlankStringOrNull(parser) ?: threadId
      "turnId", "turn_id" -> turnId = readNonBlankStringOrNull(parser) ?: turnId
      "thread" -> threadId = parseIdObject(parser) ?: threadId
      "turn" -> {
        val turn = parseTurnObject(parser)
        if (turn != null) {
          turnId = turn.id ?: turnId
          turnStatus = turn.status ?: turnStatus
          turnErrorMessage = turn.errorMessage ?: turnErrorMessage
        }
      }
      "status" -> {
        val status = parseStatusValue(parser)
        if (method?.startsWith("turn/") == true) {
          turnStatus = status ?: turnStatus
        }
      }
      "error" -> turnErrorMessage = parseErrorMessage(parser) ?: turnErrorMessage
      "item" -> {
        val item = parseItemObject(parser)
        if (item?.type == "agentMessage") {
          agentMessageText = item.text?.takeIf { it.isNotBlank() }
        }
      }
      else -> parser.skipChildren()
    }
    true
  }
  return PromptSuggestionNotificationParams(
    threadId = threadId,
    turnId = turnId,
    turnStatus = turnStatus,
    turnErrorMessage = turnErrorMessage,
    agentMessageText = agentMessageText,
  )
}

private fun parseIdObject(parser: JsonParser): String? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  var id: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "id", "threadId", "thread_id", "turnId", "turn_id" -> id = readNonBlankStringOrNull(parser) ?: id
      else -> parser.skipChildren()
    }
    true
  }
  return id
}

private fun parseTurnObject(parser: JsonParser): PromptSuggestionTurnObject? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  var turnId: String? = null
  var status: String? = null
  var errorMessage: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "id", "turnId", "turn_id" -> turnId = readNonBlankStringOrNull(parser) ?: turnId
      "status" -> status = parseStatusValue(parser) ?: status
      "error" -> errorMessage = parseErrorMessage(parser) ?: errorMessage
      else -> parser.skipChildren()
    }
    true
  }
  return PromptSuggestionTurnObject(id = turnId, status = status, errorMessage = errorMessage)
}

private fun parseItemObject(parser: JsonParser): PromptSuggestionItemObject? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  var type: String? = null
  var text: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "type" -> type = readNonBlankStringOrNull(parser)
      "text" -> text = readStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PromptSuggestionItemObject(type = type, text = text)
}

private fun parseStatusValue(parser: JsonParser): String? {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> readNonBlankStringOrNull(parser)
    JsonToken.START_OBJECT -> {
      var status: String? = null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "type", "status" -> status = readNonBlankStringOrNull(parser) ?: status
          else -> parser.skipChildren()
        }
        true
      }
      status
    }
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private fun readErrorMessage(parser: JsonParser): String? = parseErrorMessage(parser)

private fun parseErrorMessage(parser: JsonParser): String? {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> parser.string.trimToNull()
    JsonToken.START_OBJECT -> {
      var message: String? = null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "message" -> message = readNonBlankStringOrNull(parser) ?: message
          else -> parser.skipChildren()
        }
        true
      }
      message
    }
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private fun readNonBlankStringOrNull(parser: JsonParser): String? {
  return readStringOrNull(parser).trimToNull()
}

private fun String?.trimToNull(): String? {
  return this?.trim()?.takeIf { it.isNotEmpty() }
}

private fun resolvePromptSuggestionAppServerWorkingDirectory(requestedWorkingDirectory: Path?): Path {
  val effectiveWorkingDirectory = requestedWorkingDirectory?.takeIf(Files::isDirectory)
  if (effectiveWorkingDirectory != null) {
    return effectiveWorkingDirectory
  }

  val fallbackWorkingDirectory = PathManager.getSystemDir().resolve(CODEX_APP_SERVER_SYSTEM_DIR)
  Files.createDirectories(fallbackWorkingDirectory)
  return fallbackWorkingDirectory
}

private fun stopPromptSuggestionAppServerProcess(process: Process, coroutineScope: CoroutineScope) {
  if (EDT.isCurrentThreadEdt()) {
    coroutineScope.launch(CoroutineName("Codex prompt-suggestion app-server process shutdown") + Dispatchers.IO) {
      terminatePromptSuggestionAppServerProcess(process)
    }
  }
  else {
    terminatePromptSuggestionAppServerProcess(process)
  }
}

private fun terminatePromptSuggestionAppServerProcess(process: Process) {
  try {
    closePromptSuggestionAppServerProcessStream(process.outputStream)
    if (!waitForPromptSuggestionAppServerProcess(process)) {
      try {
        OSProcessUtil.killProcessTree(process)
      }
      catch (_: Throwable) {
      }
      if (process.isAlive) {
        process.destroyForcibly()
      }
      waitForPromptSuggestionAppServerProcess(process)
    }
  }
  catch (_: Throwable) {
    try {
      if (process.isAlive) {
        process.destroyForcibly()
      }
    }
    catch (_: Throwable) {
    }
  }
  finally {
    closePromptSuggestionAppServerProcessStreams(process)
  }
}

private fun waitForPromptSuggestionAppServerProcess(process: Process): Boolean {
  return try {
    process.waitFor(PROCESS_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }
  catch (_: InterruptedException) {
    Thread.currentThread().interrupt()
    !process.isAlive
  }
  catch (_: Throwable) {
    !process.isAlive
  }
}

private fun closePromptSuggestionAppServerProcessStreams(process: Process) {
  closePromptSuggestionAppServerProcessStream(process.outputStream)
  closePromptSuggestionAppServerProcessStream(process.inputStream)
  closePromptSuggestionAppServerProcessStream(process.errorStream)
}

private fun closePromptSuggestionAppServerProcessStream(stream: Closeable) {
  try {
    stream.close()
  }
  catch (_: Throwable) {
  }
}

private fun Throwable.isCodexExecutableNotFound(): Boolean {
  return anyCause { cause ->
    when (cause) {
      is NoSuchFileException -> true
      is IOException -> isExecutableNotFoundMessage(cause.message)
      else -> false
    }
  }
}

private inline fun Throwable.anyCause(predicate: (Throwable) -> Boolean): Boolean {
  return generateSequence(this) { it.cause }.any(predicate)
}

private fun isExecutableNotFoundMessage(message: String?): Boolean {
  return message != null &&
         (message.contains("error=2") ||
          message.contains("no such file or directory", ignoreCase = true) ||
          message.contains("cannot find the file", ignoreCase = true))
}
