// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.min

private val PTY_LOG = logger<PtyClaudeThreadCommandRunner>()

internal interface ClaudeThreadCommandRunner {
  suspend fun run(path: String, launchSpec: AgentSessionTerminalLaunchSpec, timeoutMs: Int): ClaudeThreadCommandResult
}

internal data class ClaudeThreadCommandResult(
  @JvmField val successful: Boolean,
  @JvmField val outputTail: String,
  @JvmField val failureReason: String? = null,
)

internal class PtyClaudeThreadCommandRunner(
  private val environmentProvider: () -> MutableMap<String, String> = { HashMap(System.getenv()) },
  private val currentTimeMs: () -> Long = System::currentTimeMillis,
  private val sleepFn: (Long) -> Unit = Thread::sleep,
) : ClaudeThreadCommandRunner {
  override suspend fun run(path: String, launchSpec: AgentSessionTerminalLaunchSpec, timeoutMs: Int): ClaudeThreadCommandResult {
    val result = withContext(Dispatchers.IO) {
      val process = startProcess(path = path, launchSpec = launchSpec)
        ?: return@withContext ClaudeThreadCommandResult(
          successful = false,
          outputTail = "",
          failureReason = "Failed to start Claude PTY process",
        )
      val outputMonitor = ClaudePtyOutputMonitor(process = process, currentTimeMs = currentTimeMs)
      outputMonitor.start()
      try {
        val readinessFailure = awaitStartupReady(process = process, outputMonitor = outputMonitor, timeoutMs = timeoutMs)
        if (readinessFailure != null) {
          return@withContext ClaudeThreadCommandResult(
            successful = false,
            outputTail = outputMonitor.outputTail(),
            failureReason = readinessFailure,
          )
        }

        ClaudeThreadCommandResult(successful = true, outputTail = outputMonitor.outputTail())
      }
      finally {
        closeProcess(process)
        outputMonitor.awaitReaderTermination()
      }
    }
    if (result.successful) {
      PTY_LOG.debug { "Claude PTY command succeeded (outputTail=${result.outputTail.trim()})" }
    }
    else {
      PTY_LOG.warn(
        "Claude PTY command failed (reason=${result.failureReason}, outputTail=${result.outputTail.trim()})"
      )
    }
    return result
  }

  private fun startProcess(path: String, launchSpec: AgentSessionTerminalLaunchSpec): PtyProcess? {
    val environment = environmentProvider()
    environment.putAll(launchSpec.envVariables)
    environment.putIfAbsent(TERM_ENV, TERM_ENV_VALUE)
    return runCatching {
      val builder = PtyProcessBuilder(launchSpec.command.toTypedArray())
        .setConsole(true)
        .setEnvironment(environment)
        .setInitialColumns(DEFAULT_PTY_COLUMNS)
        .setInitialRows(DEFAULT_PTY_ROWS)
        .setRedirectErrorStream(true)
      resolveWorkingDirectory(path)?.let { builder.setDirectory(it.toString()) }
      builder.start()
    }
      .onFailure { PTY_LOG.warn("Failed to start Claude PTY process for $path", it) }
      .getOrNull()
  }

  private fun awaitStartupReady(process: PtyProcess, outputMonitor: ClaudePtyOutputMonitor, timeoutMs: Int): String? {
    val deadline = currentTimeMs() + min(timeoutMs.toLong(), STARTUP_TIMEOUT_MS)
    while (currentTimeMs() <= deadline) {
      detectStartupFailure(outputMonitor.outputTail())?.let { return it }
      if (!process.isAlive && !outputMonitor.hasOutput()) {
        return "Claude PTY process exited before producing terminal output"
      }
      val hasComposerMarker = outputMonitor.outputTail().contains(CLAUDE_COMPOSER_MARKER)
      if (hasComposerMarker && outputMonitor.isIdleFor(STARTUP_IDLE_MS)) {
        return null
      }
      if (outputMonitor.isIdleFor(STARTUP_IDLE_FALLBACK_MS)) {
        return null
      }
      sleepFn(POLL_INTERVAL_MS)
    }
    return "Timed out waiting for Claude PTY session readiness"
  }

}

private class ClaudePtyOutputMonitor(
  private val process: PtyProcess,
  private val currentTimeMs: () -> Long,
) {
  private val outputLock = ReentrantLock()
  private val output = StringBuilder()
  private val readerClosed = AtomicBoolean(false)
  private var trimmedChars = 0
  @Volatile private var lastOutputAtMs = currentTimeMs()
  @Volatile private var hasOutputValue = false
  private val readerThread = thread(start = false, isDaemon = true, name = "claude-pty-reader") {
    readLoop()
  }

  fun start() {
    readerThread.start()
  }

  fun awaitReaderTermination() {
    readerThread.join(READER_JOIN_TIMEOUT_MS)
  }

  fun hasOutput(): Boolean = hasOutputValue

  fun isIdleFor(idleMs: Long): Boolean {
    return hasOutputValue && currentTimeMs() - lastOutputAtMs >= idleMs
  }

  fun outputTail(): String = outputLock.withLock {
    if (output.length <= OUTPUT_TAIL_MAX_CHARS) output.toString() else output.substring(output.length - OUTPUT_TAIL_MAX_CHARS)
  }

  private fun readLoop() {
    val buffer = ByteArray(4_096)
    var pendingAnsi = ""
    while (true) {
      val readBytes = runCatching { process.inputStream.read(buffer) }
        .onFailure { PTY_LOG.debug("Claude PTY reader failed", it) }
        .getOrDefault(-1)
      if (readBytes <= 0) {
        readerClosed.set(true)
        return
      }
      val text = String(buffer, 0, readBytes, StandardCharsets.UTF_8)
      append(text)
      pendingAnsi = replyToCursorPositionRequests(pendingAnsi, text)
    }
  }

  private fun append(text: String) {
    if (text.isEmpty()) return
    outputLock.withLock {
      output.append(text)
      hasOutputValue = true
      lastOutputAtMs = currentTimeMs()
      if (output.length > OUTPUT_BUFFER_MAX_CHARS) {
        val charsToTrim = output.length - OUTPUT_BUFFER_MAX_CHARS
        output.delete(0, charsToTrim)
        trimmedChars += charsToTrim
      }
    }
  }

  private fun replyToCursorPositionRequests(prefix: String, text: String): String {
    val combined = prefix + text
    val queryCount = combined.windowed(CURSOR_POSITION_QUERY.length, 1)
      .count { it == CURSOR_POSITION_QUERY }
    if (queryCount > 0) {
      runCatching {
        repeat(queryCount) {
          process.outputStream.write(CURSOR_POSITION_RESPONSE_BYTES)
        }
        process.outputStream.flush()
      }
        .onFailure { PTY_LOG.debug("Failed to reply to Claude cursor-position query", it) }
    }
    return if (combined.length < CURSOR_POSITION_QUERY.length) combined else combined.takeLast(CURSOR_POSITION_QUERY.length - 1)
  }
}

private fun resolveWorkingDirectory(path: String): Path? {
  return try {
    Path.of(path).takeIf(Files::isDirectory)
  }
  catch (_: Throwable) {
    null
  }
}

private fun detectStartupFailure(output: String): String? {
  return when {
    output.contains(NO_CONVERSATION_FOUND_ERROR) -> NO_CONVERSATION_FOUND_ERROR
    else -> null
  }
}

private fun closeProcess(process: PtyProcess) {
  repeat(4) {
    if (!process.isAlive) {
      return
    }
    runCatching {
      process.outputStream.write(CTRL_C)
      process.outputStream.flush()
    }
    Thread.sleep(CTRL_C_INTERVAL_MS)
  }

  waitForExit(process)
  if (process.isAlive) {
    process.destroy()
    waitForExit(process)
  }
  if (process.isAlive) {
    process.destroyForcibly()
    waitForExit(process)
  }
}

private fun waitForExit(process: PtyProcess) {
  val deadline = System.nanoTime() + PROCESS_DESTROY_TIMEOUT_MS * 1_000_000L
  while (process.isAlive && System.nanoTime() < deadline) {
    Thread.sleep(POLL_INTERVAL_MS)
  }
}

private const val DEFAULT_PTY_COLUMNS = 120
private const val DEFAULT_PTY_ROWS = 40
private const val OUTPUT_BUFFER_MAX_CHARS = 64_000
private const val OUTPUT_TAIL_MAX_CHARS = 4_000
private const val STARTUP_TIMEOUT_MS = 20_000L
private const val STARTUP_IDLE_MS = 500L
private const val STARTUP_IDLE_FALLBACK_MS = 2_000L
private const val PROCESS_DESTROY_TIMEOUT_MS = 2_000L
private const val CTRL_C_INTERVAL_MS = 300L
private const val READER_JOIN_TIMEOUT_MS = 1_000L
private const val POLL_INTERVAL_MS = 50L
private const val TERM_ENV = "TERM"
private const val TERM_ENV_VALUE = "xterm-256color"
private const val CURSOR_POSITION_QUERY = "\u001B[6n"
private val CURSOR_POSITION_RESPONSE_BYTES = "\u001B[1;1R".toByteArray(StandardCharsets.UTF_8)
private val CTRL_C = byteArrayOf(3)
// Vertical bar + space + ">" is the composer input-box marker in Claude's TUI.
private const val CLAUDE_COMPOSER_MARKER = "│ >"
private const val NO_CONVERSATION_FOUND_ERROR = "No conversation found with session ID"
