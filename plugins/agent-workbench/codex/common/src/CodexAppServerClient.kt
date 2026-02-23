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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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
private const val MAX_PAGES = 10
private const val PAGE_LIMIT = 50

private val LOG = logger<CodexAppServerClient>()

class CodexAppServerClient(
  private val coroutineScope: CoroutineScope,
  private val executablePathProvider: () -> String? = { CodexCliUtils.findExecutable() },
  private val environmentOverrides: Map<String, String> = emptyMap(),
  workingDirectory: Path? = null,
  idleShutdownTimeoutMs: Long = DEFAULT_IDLE_SHUTDOWN_TIMEOUT_MS,
) {
  private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
  private val requestCounter = AtomicLong(0)
  private val writeMutex = Mutex()
  private val startMutex = Mutex()
  private val initMutex = Mutex()
  private val workingDirectoryPath = workingDirectory
  private val idleShutdownTimeoutMs = idleShutdownTimeoutMs.coerceAtLeast(0)
  private val protocol = CodexAppServerProtocol()

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
    val response = request(
      method = "thread/list",
      paramsWriter = { generator ->
        generator.writeStartObject()
        generator.writeNumberField("limit", resolvedLimit)
        generator.writeStringField("order", "desc")
        generator.writeStringField("sortKey", "updated_at")
        generator.writeBooleanField("archived", archived)
        cursor?.let { generator.writeStringField("cursor", it) }
        generator.writeEndObject()
      },
      resultParser = { parser -> protocol.parseThreadListResult(parser, archived, cwdFilter) },
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
    var pages = 0
    do {
      val response = listThreadsPage(
        archived = archived,
        cursor = cursor,
        limit = PAGE_LIMIT,
        cwdFilter = cwdFilter,
      )
      threads.addAll(response.threads)
      cursor = response.nextCursor
      pages++
    } while (!cursor.isNullOrBlank() && pages < MAX_PAGES)
    return threads.sortedByDescending { it.updatedAt }
  }

  suspend fun createThread(
    cwd: String? = null,
    approvalPolicy: String? = null,
    sandbox: String? = null,
  ): CodexThread {
    val thread = request(
      method = "thread/start",
      paramsWriter = { generator ->
        generator.writeStartObject()
        cwd?.let { generator.writeStringField("cwd", it) }
        approvalPolicy?.let { generator.writeStringField("approvalPolicy", it) }
        sandbox?.let { generator.writeStringField("sandbox", it) }
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
    val process = try {
      GeneralCommandLine(executable, "app-server")
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withEnvironment(environmentOverrides)
        .apply {
          val directory = workingDirectoryPath
          if (directory != null && Files.isDirectory(directory)) {
            withWorkingDirectory(directory)
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

    if (id == null) {
      return
    }
    pending.remove(id)?.complete(payload)
  }

  private fun handleProcessExit() {
    val error = CodexAppServerException("Codex app-server terminated")
    pending.values.forEach { it.completeExceptionally(error) }
    pending.clear()
    cancelIdleShutdownTimerLocked()
    process = null
    writer = null
    initialized = false
    inFlightRequestCount = 0
  }

  private fun stopProcess() {
    cancelIdleShutdownTimerLocked()
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
