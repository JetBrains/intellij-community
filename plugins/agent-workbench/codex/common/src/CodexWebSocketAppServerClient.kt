// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private const val WEBSOCKET_REQUEST_TIMEOUT_MS = 30_000L
private const val WEBSOCKET_STARTUP_TIMEOUT_MS = 10_000L
private const val WEBSOCKET_READY_POLL_INTERVAL_MS = 100L
private const val PROCESS_TERMINATION_TIMEOUT_MS = 2_000L
private const val PAGE_LIMIT = 50

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

private val LOG = logger<CodexWebSocketAppServerClient>()

@ApiStatus.Internal
class CodexWebSocketAppServerClient(
  private val coroutineScope: CoroutineScope,
  private val executablePathProvider: () -> String? = { CodexCliUtils.findExecutable() },
  private val environmentOverrides: Map<String, String> = emptyMap(),
  workingDirectory: Path? = null,
) {
  private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
  private val requestCounter = AtomicLong(0)
  private val writeMutex = Mutex()
  private val startMutex = Mutex()
  private val initMutex = Mutex()
  private val protocol = CodexAppServerProtocol()
  private val workingDirectoryPath = workingDirectory
  private val httpClient = HttpClient.newBuilder().build()
  private val notificationsChannel = Channel<CodexAppServerNotification>(capacity = Channel.UNLIMITED)
  private val notificationsFlow = notificationsChannel.receiveAsFlow()

  @Volatile
  private var process: Process? = null

  @Volatile
  private var webSocket: WebSocket? = null

  @Volatile
  private var initialized = false

  @Volatile
  private var remoteUrl: String? = null

  private var stderrJob: Job? = null
  private var waitJob: Job? = null

  val notifications: Flow<CodexAppServerNotification>
    get() = notificationsFlow

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

    return try {
      request(
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
    catch (e: CodexAppServerException) {
      if (e.isWebSocketThreadReadIncludeTurnsFallback()) {
        LOG.debug { "thread/read includeTurns fallback for threadId=$normalizedThreadId: ${e.message}" }
        null
      }
      else {
        throw e
      }
    }
  }

  suspend fun createThreadSession(
    cwd: String? = null,
    approvalPolicy: String? = null,
    sandbox: String? = null,
    ephemeral: Boolean? = null,
  ): CodexStartedThreadSession {
    return startThread(
      cwd = cwd,
      approvalPolicy = approvalPolicy,
      sandbox = sandbox,
      ephemeral = ephemeral,
    )
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

  suspend fun setThreadName(threadId: String, name: String) {
    requestUnit(
      method = "thread/name/set",
      paramsWriter = { generator ->
        generator.writeStartObject()
        generator.writeStringField("threadId", threadId)
        generator.writeStringField("name", name)
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

  private suspend fun startThread(
    cwd: String? = null,
    approvalPolicy: String? = null,
    sandbox: String? = null,
    ephemeral: Boolean? = null,
  ): CodexStartedThreadSession {
    val thread = request(
      method = "thread/start",
      paramsWriter = { generator ->
        generator.writeStartObject()
        cwd?.let { generator.writeStringField("cwd", it) }
        approvalPolicy?.let { generator.writeStringField("approvalPolicy", it) }
        sandbox?.let { generator.writeStringField("sandbox", it) }
        ephemeral?.let { generator.writeBooleanField("ephemeral", it) }
        generator.writeEndObject()
      },
      resultParser = { parser -> protocol.parseThreadStartResult(parser) },
      defaultResult = null,
    )
    return thread ?: throw CodexAppServerException("Codex app-server returned empty thread/start result")
  }

  private suspend fun <T> request(
    method: String,
    paramsWriter: ((JsonGenerator) -> Unit)? = null,
    ensureInitialized: Boolean = true,
    resultParser: (JsonParser) -> T,
    defaultResult: T,
  ): T {
    var id: String? = null
    try {
      if (ensureInitialized) ensureInitialized() else ensureConnectedSocket()
      id = requestCounter.incrementAndGet().toString()
      val deferred = CompletableDeferred<String>()
      pending[id] = deferred
      sendRequest(id, method, paramsWriter)
      val response = withTimeout(WEBSOCKET_REQUEST_TIMEOUT_MS.milliseconds) { deferred.await() }
      return protocol.parseResponse(response, resultParser, defaultResult)
    }
    catch (t: TimeoutCancellationException) {
      currentCoroutineContext().ensureActive()
      throw CodexAppServerException("Codex request timed out", t)
    }
    finally {
      id?.let { pending.remove(it) }
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
    val activeSocket = ensureConnectedSocket()
    writeMutex.withLock {
      val payload = StringWriter()
      protocol.writePayload(payload, payloadWriter)
      try {
        runInterruptible {
          activeSocket.sendText(payload.toString(), true).get(WEBSOCKET_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
      }
      catch (t: Throwable) {
        throw CodexAppServerException("Failed to send websocket request to Codex app-server", t)
      }
    }
  }

  private suspend fun ensureInitialized() {
    ensureConnectedSocket()
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
          generator.writeFieldName("capabilities")
          generator.writeStartObject()
          generator.writeBooleanField("experimentalApi", true)
          generator.writeEndObject()
          generator.writeEndObject()
        },
        ensureInitialized = false,
      )
      sendNotification("initialized")
      initialized = true
    }
  }

  private suspend fun ensureConnectedSocket(): WebSocket {
    val currentProcess = process
    val currentSocket = webSocket
    if (currentProcess != null && currentProcess.isAlive && currentSocket != null) {
      return currentSocket
    }
    return startMutex.withLock {
      val existingProcess = process
      val existingSocket = webSocket
      if (existingProcess != null && existingProcess.isAlive && existingSocket != null) {
        existingSocket
      }
      else {
        if (existingProcess != null) {
          stopProcess()
        }
        startProcess()
      }
    }
  }

  private suspend fun startProcess(): WebSocket {
    val configuredExecutable = executablePathProvider()
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    val executable = configuredExecutable ?: CodexCliUtils.CODEX_COMMAND
    val requestedWorkingDirectory = workingDirectoryPath
    val effectiveWorkingDirectory = requestedWorkingDirectory?.takeIf(Files::isDirectory)
    LOG.debug {
      "Starting Codex websocket app-server(executable=$executable, executableSource=${if (configuredExecutable != null) "configured" else "default"}, requestedWorkingDirectory=${requestedWorkingDirectory ?: "<none>"}, effectiveWorkingDirectory=${effectiveWorkingDirectory ?: "<none>"}, environmentOverrideCount=${environmentOverrides.size})"
    }

    val bindUrlDeferred = CompletableDeferred<String>()
    val createdProcess = try {
      GeneralCommandLine(executable, "-c", CODEX_AUTO_UPDATE_CONFIG, "app-server", "--listen", "ws://127.0.0.1:0")
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
      if (configuredExecutable == null && isExecutableNotFoundForWebSocket(t)) {
        throw CodexCliNotFoundException()
      }
      throw CodexAppServerException("Failed to start Codex app-server from $executable", t)
    }

    process = createdProcess
    initialized = false
    remoteUrl = null
    startStderrReader(createdProcess, bindUrlDeferred)
    startWaiter(createdProcess)

    val boundUrl = try {
      withTimeout(WEBSOCKET_STARTUP_TIMEOUT_MS.milliseconds) { bindUrlDeferred.await() }
    }
    catch (t: Throwable) {
      stopProcess()
      throw CodexAppServerException("Timed out waiting for Codex websocket app-server to report its bind address", t)
    }

    try {
      awaitReady(boundUrl)
      val socket = connectWebSocket(boundUrl)
      remoteUrl = boundUrl
      webSocket = socket
      return socket
    }
    catch (t: Throwable) {
      stopProcess()
      throw if (t is CodexAppServerException) t else CodexAppServerException("Failed to connect to Codex websocket app-server", t)
    }
  }

  private fun startStderrReader(process: Process, bindUrlDeferred: CompletableDeferred<String>) {
    stderrJob?.cancel()
    stderrJob = coroutineScope.launch(Dispatchers.IO) {
      val reader = BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))
      try {
        while (isActive) {
          val line = runInterruptible { reader.readLine() } ?: break
          if (line.isNotBlank()) {
            LOG.debug { "Codex websocket app-server stderr: $line" }
            if (!bindUrlDeferred.isCompleted) {
              extractWebSocketUrl(line)?.let(bindUrlDeferred::complete)
            }
          }
        }
      }
      catch (e: Throwable) {
        if (!isActive || !process.isAlive) {
          return@launch
        }
        LOG.error("Codex websocket app-server stderr reader failed", e)
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

  private suspend fun awaitReady(boundUrl: String) {
    @Suppress("HttpUrlsUsage")
    val readyRequest = HttpRequest.newBuilder(URI.create(boundUrl.replaceFirst("ws://", "http://") + "/readyz"))
      .GET()
      .build()
    withTimeout(WEBSOCKET_STARTUP_TIMEOUT_MS.milliseconds) {
      while (true) {
        try {
          val response = runInterruptible {
            httpClient.send(readyRequest, HttpResponse.BodyHandlers.discarding())
          }
          if (response.statusCode() == 200) {
            return@withTimeout
          }
        }
        catch (_: Throwable) {
        }
        delay(WEBSOCKET_READY_POLL_INTERVAL_MS.milliseconds)
      }
    }
  }

  private suspend fun connectWebSocket(boundUrl: String): WebSocket {
    return try {
      runInterruptible {
        httpClient.newWebSocketBuilder()
          .buildAsync(URI.create(boundUrl), AppServerWebSocketListener())
          .get(WEBSOCKET_STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      }
    }
    catch (t: Throwable) {
      throw CodexAppServerException("Failed to connect websocket client to $boundUrl", t)
    }
  }

  private fun handleMessage(payload: String) {
    val id = try {
      protocol.parseMessageId(payload)
    }
    catch (e: Throwable) {
      LOG.error("Failed to parse Codex websocket app-server payload: $payload", e)
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
      LOG.warn("Failed to parse Codex websocket app-server notification: $payload", e)
      null
    } ?: return

    val result = notificationsChannel.trySend(notification.toPublicNotification())
    if (result.isFailure) {
      LOG.warn("Failed to enqueue Codex websocket app-server notification: ${notification.method}")
    }
  }

  private fun handleTransportFailure(message: String, cause: Throwable? = null) {
    if (process == null && webSocket == null) {
      return
    }
    if (cause == null) {
      LOG.warn(message)
    }
    else {
      LOG.warn(message, cause)
    }
    stopProcess(CodexAppServerException(message, cause))
  }

  private fun handleProcessExit() {
    stopProcess(CodexAppServerException("Codex app-server terminated"))
  }

  private fun stopProcess(error: CodexAppServerException? = null) {
    val currentProcess = process
    val currentSocket = webSocket
    process = null
    webSocket = null
    remoteUrl = null
    initialized = false

    if (error != null) {
      pending.values.forEach { it.completeExceptionally(error) }
    }
    pending.clear()

    stderrJob?.cancel()
    waitJob?.cancel()
    stderrJob = null
    waitJob = null

    try {
      currentSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "")
    }
    catch (_: Throwable) {
    }
    try {
      currentSocket?.abort()
    }
    catch (_: Throwable) {
    }

    if (currentProcess != null) {
      try {
        currentProcess.outputStream.close()
      }
      catch (_: Throwable) {
      }
      try {
        currentProcess.inputStream.close()
      }
      catch (_: Throwable) {
      }
      try {
        currentProcess.errorStream.close()
      }
      catch (_: Throwable) {
      }
      currentProcess.destroy()
      try {
        if (!currentProcess.waitFor(PROCESS_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          currentProcess.destroyForcibly()
          currentProcess.waitFor(PROCESS_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
      }
      catch (_: Throwable) {
      }
    }
  }

  private inner class AppServerWebSocketListener : WebSocket.Listener {
    private val textBuffer = StringBuilder()

    override fun onOpen(webSocket: WebSocket) {
      webSocket.request(1)
    }

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*> {
      textBuffer.append(data)
      if (last) {
        val payload = textBuffer.toString().trim()
        textBuffer.setLength(0)
        if (payload.isNotEmpty()) {
          handleMessage(payload)
        }
      }
      webSocket.request(1)
      return COMPLETED_FUTURE
    }

    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletableFuture<*> {
      handleTransportFailure("Codex websocket app-server closed the websocket connection (status=$statusCode, reason=$reason)")
      return COMPLETED_FUTURE
    }

    override fun onError(webSocket: WebSocket, error: Throwable) {
      handleTransportFailure("Codex websocket app-server connection failed", error)
    }
  }
}

private const val CODEX_AUTO_UPDATE_CONFIG: String = "check_for_update_on_startup=false"

private val COMPLETED_FUTURE: CompletableFuture<*> = CompletableFuture.completedFuture(null)

private fun extractWebSocketUrl(line: String): String? {
  val strippedLine = stripAnsiEscapes(line)
  return strippedLine
    .splitToSequence(' ', '\t')
    .map(String::trim)
    .firstOrNull { it.startsWith("ws://") }
}

private fun stripAnsiEscapes(text: String): String {
  val stripped = StringBuilder(text.length)
  val chars = text.iterator()
  while (chars.hasNext()) {
    val ch = chars.nextChar()
    if (ch == '\u001B' && chars.hasNext() && chars.nextChar() == '[') {
      while (chars.hasNext()) {
        val next = chars.nextChar()
        if (next in '@'..'~') {
          break
        }
      }
      continue
    }
    stripped.append(ch)
  }
  return stripped.toString()
}

private fun isExecutableNotFoundForWebSocket(error: Throwable): Boolean {
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

private fun Throwable.isWebSocketThreadReadIncludeTurnsFallback(): Boolean {
  return generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .any { message ->
      message.contains("includeTurns is unavailable before first user message") ||
      message.contains("ephemeral threads do not support includeTurns")
    }
}
