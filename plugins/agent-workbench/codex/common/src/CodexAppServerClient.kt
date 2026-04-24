// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private const val REQUEST_TIMEOUT_MS = 30_000L
private const val PROCESS_TERMINATION_TIMEOUT_MS = 2_000L
private const val DEFAULT_IDLE_SHUTDOWN_TIMEOUT_MS = 60_000L
private const val PAGE_LIMIT = 50
private const val PROMPT_SUGGESTION_CLEANUP_TIMEOUT_MS = 500L

private val THREAD_LIST_SOURCE_KINDS: List<String> = listOf(
  "cli",
  "vscode",
  "exec",
  "appServer",
  "subAgent",
  "subAgentReview",
  "subAgentCompact",
  "subAgentThreadSpawn",
  "subAgentOther",
  "unknown",
)

private val LOG = logger<CodexAppServerClient>()

@ApiStatus.Internal
enum class CodexAppServerNotificationRouting {
  PUBLIC_ONLY,
  PARSED_ONLY,
  BOTH,
}

class CodexAppServerClient(
  private val coroutineScope: CoroutineScope,
  private val executablePathProvider: () -> String? = { CodexCliUtils.findExecutable() },
  private val environmentOverrides: Map<String, String> = emptyMap(),
  workingDirectory: Path? = null,
  idleShutdownTimeoutMs: Long = DEFAULT_IDLE_SHUTDOWN_TIMEOUT_MS,
  notificationRouting: CodexAppServerNotificationRouting = CodexAppServerNotificationRouting.BOTH,
) {
  private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
  private val requestCounter = AtomicLong(0)
  private val writeMutex = Mutex()
  private val startMutex = Mutex()
  private val initMutex = Mutex()
  private val workingDirectoryPath = workingDirectory
  private val idleShutdownTimeoutMs = idleShutdownTimeoutMs.coerceAtLeast(0)
  private val protocol = CodexAppServerProtocol()
  // Queue notifications until the refresh pipeline is ready to consume them.
  // SharedFlow with replay=0 drops events when no collector is active, which leaves
  // stale working states behind until some unrelated refresh occurs.
  private val notificationsChannel = if (notificationRouting.includesPublicNotifications()) {
    Channel<CodexAppServerNotification>(capacity = Channel.UNLIMITED)
  }
  else {
    null
  }
  private val parsedNotificationsChannel = if (notificationRouting.includesParsedNotifications()) {
    Channel<ParsedCodexAppServerNotification>(capacity = Channel.UNLIMITED)
  }
  else {
    null
  }
  private val notificationsFlow = notificationsChannel?.receiveAsFlow() ?: emptyFlow()

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

  suspend fun listThreadsPage(
    archived: Boolean,
    cursor: String? = null,
    limit: Int = PAGE_LIMIT,
    cwdFilter: String? = null,
  ): CodexThreadPage {
    val resolvedLimit = limit.coerceAtLeast(1)
    val normalizedCwdFilter = cwdFilter
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?.let(::normalizeRootPath)
    val response = request(
      method = "thread/list",
      paramsWriter = { generator ->
        generator.writeStartObject()
        generator.writeNumberField("limit", resolvedLimit)
        generator.writeStringField("order", "desc")
        generator.writeStringField("sortKey", "updated_at")
        generator.writeBooleanField("archived", archived)
        cursor?.let { generator.writeStringField("cursor", it) }
        normalizedCwdFilter?.let { generator.writeStringField("cwd", it) }
        generator.writeFieldName("sourceKinds")
        generator.writeStartArray()
        THREAD_LIST_SOURCE_KINDS.forEach(generator::writeString)
        generator.writeEndArray()
        generator.writeEndObject()
      },
      resultParser = { parser -> protocol.parseThreadListResult(parser, archived, normalizedCwdFilter) },
      defaultResult = ThreadListResult(emptyList(), null),
    )
    return CodexThreadPage(
      threads = response.threads.sortedByDescending { it.updatedAt },
      nextCursor = response.nextCursor,
    )
  }

  val notifications: Flow<CodexAppServerNotification>
    get() = notificationsFlow

  suspend fun listThreads(archived: Boolean, cwdFilter: String? = null): List<CodexThread> {
    val threads = mutableListOf<CodexThread>()
    var cursor: String? = null
    val seenCursors = LinkedHashSet<String>()
    while (true) {
      val response = listThreadsPage(
        archived = archived,
        cursor = cursor,
        limit = PAGE_LIMIT,
        cwdFilter = cwdFilter,
      )
      threads.addAll(response.threads)
      val nextCursor = response.nextCursor
      if (nextCursor.isNullOrBlank()) {
        break
      }
      if (!seenCursors.add(nextCursor)) {
        LOG.warn("thread/list returned a repeating cursor '$nextCursor'; stopping pagination")
        break
      }
      cursor = nextCursor
    }
    return threads.sortedByDescending { it.updatedAt }
  }

  suspend fun readThreadActivitySnapshot(threadId: String): CodexThreadActivitySnapshot? {
    val normalizedThreadId = threadId.trim()
    if (normalizedThreadId.isEmpty()) {
      return null
    }

    return request(
      method = "thread/read",
      paramsWriter = { generator ->
        generator.writeStartObject()
        generator.writeStringField("threadId", normalizedThreadId)
        generator.writeBooleanField("includeTurns", true)
        generator.writeEndObject()
      },
      resultParser = { parser -> protocol.parseThreadReadActivityResult(parser) },
      defaultResult = null,
    )
  }

  suspend fun suggestPrompt(request: CodexPromptSuggestionRequest): CodexPromptSuggestionResult? {
    check(parsedNotificationsChannel != null) {
      "Codex prompt suggestions require parsed notification routing"
    }

    val thread = startThread(
      cwd = request.cwd,
      approvalPolicy = "never",
      sandbox = "read-only",
      ephemeral = true,
      experimentalRawEvents = false,
      persistExtendedHistory = false,
    )
    var turnId: String? = null
    var terminalObserved = false
    try {
      val turn = request(
        method = "turn/start",
        paramsWriter = { generator ->
          generator.writeStartObject()
          generator.writeStringField("threadId", thread.id)
          generator.writeFieldName("input")
          generator.writeStartArray()
          generator.writeStartObject()
          generator.writeStringField("type", "text")
          generator.writeStringField("text", buildPromptSuggestionTurnInput(request))
          generator.writeEndObject()
          generator.writeEndArray()
          generator.writeStringField("model", request.model)
          request.reasoningEffort?.let { generator.writeStringField("effort", it) }
          generator.writeFieldName("outputSchema")
          writePromptSuggestionOutputSchema(generator, request)
          generator.writeEndObject()
        },
        resultParser = { parser -> protocol.parseTurnStartResult(parser) },
        defaultResult = null,
      ) ?: throw CodexAppServerException("Codex app-server returned empty turn/start result")
      turnId = turn.turnId
      val completion = awaitPromptSuggestionTurnCompletion(threadId = thread.id, turnId = turn.turnId)
      terminalObserved = true
      return completion.toPromptSuggestionResult()
    }
    catch (t: Throwable) {
      if (turnId != null && !terminalObserved) {
        cleanupPromptSuggestionTurn(threadId = thread.id, turnId = turnId)
      }
      throw t
    }
  }

  suspend fun createThread(
    cwd: String? = null,
    approvalPolicy: String? = null,
    sandbox: String? = null,
    ephemeral: Boolean? = null,
  ): CodexThread {
    return startThread(
      cwd = cwd,
      approvalPolicy = approvalPolicy,
      sandbox = sandbox,
      ephemeral = ephemeral,
    )
  }

  private suspend fun startThread(
    cwd: String? = null,
    approvalPolicy: String? = null,
    sandbox: String? = null,
    ephemeral: Boolean? = null,
    experimentalRawEvents: Boolean? = null,
    persistExtendedHistory: Boolean? = null,
  ): CodexThread {
    val thread = request(
      method = "thread/start",
      paramsWriter = { generator ->
        generator.writeStartObject()
        cwd?.let { generator.writeStringField("cwd", it) }
        approvalPolicy?.let { generator.writeStringField("approvalPolicy", it) }
        sandbox?.let { generator.writeStringField("sandbox", it) }
        ephemeral?.let { generator.writeBooleanField("ephemeral", it) }
        experimentalRawEvents?.let { generator.writeBooleanField("experimentalRawEvents", it) }
        persistExtendedHistory?.let { generator.writeBooleanField("persistExtendedHistory", it) }
        generator.writeEndObject()
      },
      resultParser = { parser -> protocol.parseThreadStartResult(parser) },
      defaultResult = null,
    )
    return thread ?: throw CodexAppServerException("Codex app-server returned empty thread/start result")
  }

  suspend fun archiveThread(threadId: String) {
    requestUnit(
      method = "thread/archive",
      paramsWriter = { generator ->
        generator.writeStartObject()
        generator.writeStringField("threadId", threadId)
        generator.writeEndObject()
      },
    )
  }

  suspend fun unarchiveThread(threadId: String) {
    requestUnit(
      method = "thread/unarchive",
      paramsWriter = { generator ->
        generator.writeStartObject()
        generator.writeStringField("threadId", threadId)
        generator.writeEndObject()
      },
    )
  }

  @Suppress("unused")
  suspend fun interruptTurn(threadId: String, turnId: String) {
    requestUnit(
      method = "turn/interrupt",
      paramsWriter = { generator ->
        generator.writeStartObject()
        generator.writeStringField("threadId", threadId)
        generator.writeStringField("turnId", turnId)
        generator.writeEndObject()
      },
    )
  }

  /**
   * Sends a minimal [turn/start] for the given thread to force persistence so
   * that `codex resume <id>` can discover it.
   */
  suspend fun persistThread(threadId: String) {
    requestUnit(
      method = "turn/start",
      paramsWriter = { generator ->
        generator.writeStartObject()
        generator.writeStringField("threadId", threadId)
        generator.writeFieldName("input")
        generator.writeStartArray()
        generator.writeStartObject()
        generator.writeStringField("type", "text")
        generator.writeStringField("text", "")
        generator.writeEndObject()
        generator.writeEndArray()
        generator.writeEndObject()
      },
    )
  }

  fun shutdown() {
    stopProcess()
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
      sendRequest(id, method, paramsWriter)
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

  private fun clearParsedNotificationsQueue() {
    val channel = parsedNotificationsChannel ?: return
    while (true) {
      if (channel.tryReceive().isFailure) {
        return
      }
    }
  }

  private fun cancelIdleShutdownTimerLocked() {
    idleShutdownJob?.cancel()
    idleShutdownJob = null
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

  private suspend fun sendRequest(id: String, method: String, paramsWriter: ((JsonGenerator) -> Unit)?) {
    send { generator ->
      generator.writeStartObject()
      generator.writeStringField("id", id)
      generator.writeStringField("method", method)
      if (paramsWriter != null) {
        generator.writeFieldName("params")
        paramsWriter(generator)
      }
      generator.writeEndObject()
    }
  }

  private suspend fun sendNotification(method: String, paramsWriter: ((JsonGenerator) -> Unit)? = null) {
    send { generator ->
      generator.writeStartObject()
      generator.writeStringField("method", method)
      if (paramsWriter != null) {
        generator.writeFieldName("params")
        paramsWriter(generator)
      }
      generator.writeEndObject()
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
        paramsWriter = { generator ->
          generator.writeStartObject()
          generator.writeFieldName("clientInfo")
          generator.writeStartObject()
          generator.writeStringField("name", "IntelliJ Agent Workbench")
          generator.writeStringField("version", "1.0")
          generator.writeEndObject()
          generator.writeEndObject()
        },
        ensureInitialized = false,
      )
      sendNotification("initialized")
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

  private fun startProcess(): Process {
    val configuredExecutable = executablePathProvider()
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    val executable = configuredExecutable ?: CodexCliUtils.CODEX_COMMAND
    val requestedWorkingDirectory = workingDirectoryPath
    val effectiveWorkingDirectory = requestedWorkingDirectory?.takeIf(Files::isDirectory)
    LOG.debug {
      "Starting Codex app-server(executable=$executable, executableSource=${if (configuredExecutable != null) "configured" else "default"}, requestedWorkingDirectory=${requestedWorkingDirectory ?: "<none>"}, effectiveWorkingDirectory=${effectiveWorkingDirectory ?: "<none>"}, environmentOverrideCount=${environmentOverrides.size})"
    }
    val process = try {
      GeneralCommandLine(executable, "-c", CODEX_AUTO_UPDATE_CONFIG, "app-server")
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withEnvironment(environmentOverrides)
        .apply {
          if (effectiveWorkingDirectory != null) {
            withWorkingDirectory(effectiveWorkingDirectory)
          }
        }
        .createProcess()
    }
    catch (t: Throwable) {
      if (configuredExecutable == null && isExecutableNotFound(t)) {
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
        LOG.warn("Codex app-server stdout reader failed", e)
      }
      finally {
        try {
          reader.close()
        }
        catch (_: Throwable) {
        }
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
            LOG.debug { "Codex app-server stderr: $line" }
          }
        }
      }
      catch (e: Throwable) {
        if (!isActive || !process.isAlive) return@launch
        LOG.error("Codex app-server stderr reader failed", e)
      }
      finally {
        try {
          reader.close()
        }
        catch (_: Throwable) {
        }
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
      LOG.error("Failed to parse Codex app-server payload: $payload", e)
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
      LOG.warn("Failed to parse Codex app-server notification: $payload", e)
      null
    } ?: return

    val parsedChannel = parsedNotificationsChannel
    if (parsedChannel != null) {
      val parsedResult = parsedChannel.trySend(notification)
      if (parsedResult.isFailure) {
        LOG.warn("Failed to enqueue Codex app-server notification for prompt suggestions: ${notification.method}")
      }
    }

    val publicChannel = notificationsChannel
    if (publicChannel != null) {
      val result = publicChannel.trySend(notification.toPublicNotification())
      if (result.isFailure) {
        LOG.warn("Failed to enqueue Codex app-server notification: ${notification.method}")
      }
    }
  }

  private suspend fun awaitPromptSuggestionTurnCompletion(threadId: String, turnId: String): PromptSuggestionTurnCompletion {
    var agentMessageText: String? = null
    while (true) {
      val notification = try {
        withTimeout(REQUEST_TIMEOUT_MS.milliseconds) {
          receiveParsedNotification()
        }
      }
      catch (t: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw CodexAppServerException("Codex prompt suggestion turn timed out", t)
      }

      if (!notification.matchesPromptSuggestionTurn(threadId = threadId, turnId = turnId)) {
        continue
      }

      if (notification.method == "item/completed" && notification.agentMessageText != null) {
        agentMessageText = notification.agentMessageText
      }

      if (notification.method != "turn/completed") {
        continue
      }

      return PromptSuggestionTurnCompletion(
        turnStatus = notification.turnStatus,
        turnErrorMessage = notification.turnErrorMessage,
        agentMessageText = agentMessageText,
      )
    }
  }

  private suspend fun cleanupPromptSuggestionTurn(threadId: String, turnId: String) {
    val cleanupConfirmed = withContext(NonCancellable) {
      try {
        sendInterruptTurnBestEffort(threadId = threadId, turnId = turnId)
        withTimeout(PROMPT_SUGGESTION_CLEANUP_TIMEOUT_MS.milliseconds) {
          awaitPromptSuggestionTurnCleanup(threadId = threadId, turnId = turnId)
        }
      }
      catch (_: TimeoutCancellationException) {
        LOG.warn("Failed to clean up Codex prompt suggestion turn $threadId/$turnId; resetting client")
        false
      }
      catch (t: Throwable) {
        if (t is CancellationException && t !is TimeoutCancellationException) {
          throw t
        }
        LOG.warn("Failed to clean up Codex prompt suggestion turn $threadId/$turnId; resetting client", t)
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
        generator.writeStartObject()
        generator.writeStringField("id", id)
        generator.writeStringField("method", "turn/interrupt")
        generator.writeFieldName("params")
        generator.writeStartObject()
        generator.writeStringField("threadId", threadId)
        generator.writeStringField("turnId", turnId)
        generator.writeEndObject()
        generator.writeEndObject()
      }
      out.newLine()
      out.flush()
    }
  }

  private suspend fun awaitPromptSuggestionTurnCleanup(threadId: String, turnId: String): Boolean {
    while (true) {
      val notification = receiveParsedNotification()
      if (!notification.matchesPromptSuggestionTurn(threadId = threadId, turnId = turnId)) {
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

  private suspend fun receiveParsedNotification(): ParsedCodexAppServerNotification {
    val channel = parsedNotificationsChannel
      ?: throw CodexAppServerException("Codex parsed notifications are not available")
    return channel.receive()
  }

  private fun handleProcessExit() {
    val error = CodexAppServerException("Codex app-server terminated")
    pending.values.forEach { it.completeExceptionally(error) }
    pending.clear()
    cancelIdleShutdownTimerLocked()
    clearParsedNotificationsQueue()
    process = null
    writer = null
    initialized = false
    inFlightRequestCount = 0
  }

  private fun stopProcess() {
    cancelIdleShutdownTimerLocked()
    clearParsedNotificationsQueue()
    val current = process ?: return
    process = null
    initialized = false
    inFlightRequestCount = 0
    try {
      writer?.close()
    }
    catch (_: Throwable) {
    }
    writer = null
    try {
      current.outputStream.close()
    }
    catch (_: Throwable) {
    }
    try {
      current.inputStream.close()
    }
    catch (_: Throwable) {
    }
    try {
      current.errorStream.close()
    }
    catch (_: Throwable) {
    }
    readerJob?.cancel()
    stderrJob?.cancel()
    waitJob?.cancel()
    current.destroy()
    try {
      if (!current.waitFor(PROCESS_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        current.destroyForcibly()
        current.waitFor(PROCESS_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      }
    }
    catch (_: Throwable) {
    }
  }
}

private const val CODEX_AUTO_UPDATE_CONFIG: String = "check_for_update_on_startup=false"

private fun isExecutableNotFound(error: Throwable): Boolean {
  return generateSequence(error) { it.cause }
    .any { cause ->
      when (cause) {
        is NoSuchFileException -> true
        is IOException -> {
          val message = cause.message ?: return@any false
          message.contains("error=2") ||
          message.contains("no such file or directory", ignoreCase = true) ||
          message.contains("cannot find the file", ignoreCase = true)
        }
        else -> false
      }
    }
}

open class CodexAppServerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class CodexCliNotFoundException : CodexAppServerException("Codex CLI not found")

private data class PromptSuggestionTurnCompletion(
  @JvmField val turnStatus: String?,
  @JvmField val turnErrorMessage: String?,
  @JvmField val agentMessageText: String?,
)

private fun PromptSuggestionTurnCompletion.toPromptSuggestionResult(): CodexPromptSuggestionResult? {
  return when (turnStatus) {
    "completed", null -> agentMessageText?.let(::parseCodexPromptSuggestionResult)
    "interrupted" -> null
    "failed" -> throw CodexAppServerException(turnErrorMessage ?: "Codex prompt suggestion turn failed")
    else -> null
  }
}

private fun ParsedCodexAppServerNotification.matchesPromptSuggestionTurn(threadId: String, turnId: String): Boolean {
  return this.threadId == threadId && this.turnId == turnId
}

private fun CodexAppServerNotificationRouting.includesPublicNotifications(): Boolean {
  return this == CodexAppServerNotificationRouting.PUBLIC_ONLY || this == CodexAppServerNotificationRouting.BOTH
}

private fun CodexAppServerNotificationRouting.includesParsedNotifications(): Boolean {
  return this == CodexAppServerNotificationRouting.PARSED_ONLY || this == CodexAppServerNotificationRouting.BOTH
}
