// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.buildClaudeArchivedThreadTitle
import com.intellij.agent.workbench.claude.common.normalizeClaudeStoredThreadTitle
import com.intellij.agent.workbench.claude.common.resolveClaudeThreadTitleState
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<PtyClaudeThreadRenameEngine>()

internal interface ClaudeThreadRenameEngine {
  suspend fun rename(path: String, threadId: String, newTitle: String): Boolean

  suspend fun archiveThread(path: String, threadId: String): Boolean

  suspend fun unarchiveThread(path: String, threadId: String): Boolean
}

internal class PtyClaudeThreadRenameEngine(
  private val backend: ClaudeSessionBackend,
  private val commandRunner: ClaudeThreadCommandRunner = PtyClaudeThreadCommandRunner(),
  private val commandTimeoutMs: Int = DEFAULT_CLAUDE_THREAD_COMMAND_TIMEOUT_MS,
  private val waitTimeoutMs: Long = DEFAULT_CLAUDE_THREAD_RENAME_WAIT_TIMEOUT_MS,
  private val pollIntervalMs: Long = DEFAULT_CLAUDE_THREAD_RENAME_POLL_INTERVAL_MS,
  private val currentTimeMs: () -> Long = System::currentTimeMillis,
  private val delayFn: suspend (Duration) -> Unit = { delay(it) },
) : ClaudeThreadRenameEngine {
  override suspend fun rename(path: String, threadId: String, newTitle: String): Boolean {
    val normalizedTitle = normalizeClaudeStoredThreadTitle(newTitle) ?: return false
    val expectedState = resolveClaudeThreadTitleState(normalizedTitle, threadId)
    val currentThread = findThread(path, threadId) ?: return false
    if (currentThread.archived == expectedState.archived && currentThread.title == expectedState.title) {
      return true
    }
    val resumeLaunchSpec = buildClaudeResumeLaunchSpec(threadId)
    val launchSpec = resumeLaunchSpec.copy(
      command = replaceOrAddPermissionMode(resumeLaunchSpec.command, PERMISSION_MODE_DEFAULT) +
                listOf(CLAUDE_PRINT_FLAG, CLAUDE_NAME_FLAG, normalizedTitle, CLAUDE_PROMPT_SEPARATOR, CLAUDE_RENAME_PROMPT),
    )
    val commandResult = commandRunner.run(
      path = path,
      launchSpec = launchSpec,
      timeoutMs = commandTimeoutMs,
    )
    if (!commandResult.successful) {
      LOG.warn(
        "Claude thread rename command failed for $threadId (reason=${commandResult.failureReason}, output=${commandResult.outputTail.trim()})"
      )
      return false
    }
    return waitForObservedState(
      path = path,
      threadId = threadId,
      archived = expectedState.archived,
      expectedTitle = expectedState.title,
      commandOutputTail = commandResult.outputTail,
    )
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    val current = findThread(path, threadId) ?: return false
    return rename(path, threadId, buildClaudeArchivedThreadTitle(current.title, threadId))
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    val current = findThread(path, threadId) ?: return false
    return rename(path, threadId, resolveClaudeThreadTitleState(current.title, threadId).title)
  }

  private suspend fun findThread(path: String, threadId: String): ClaudeBackendThread? =
    backend.listThreads(path = path, openProject = null).firstOrNull { it.id == threadId }

  private suspend fun waitForObservedState(
    path: String,
    threadId: String,
    archived: Boolean,
    expectedTitle: String,
    commandOutputTail: String,
  ): Boolean {
    val deadline = currentTimeMs() + waitTimeoutMs
    var lastObservedThread: ClaudeBackendThread? = null
    while (currentTimeMs() <= deadline) {
      val currentThread = findThread(path, threadId)
      lastObservedThread = currentThread
      if (currentThread != null && currentThread.archived == archived && currentThread.title == expectedTitle) {
        return true
      }
      delayFn(pollIntervalMs.milliseconds)
    }

    LOG.warn(
      "Timed out waiting for Claude rename state for $threadId " +
      "(archived=$archived, title=$expectedTitle, observed=${describeObservedState(lastObservedThread)}, " +
      "commandOutputTail=${commandOutputTail.trim()})"
    )
    return false
  }
}

private fun describeObservedState(thread: ClaudeBackendThread?): String {
  return if (thread == null) {
    "missing"
  }
  else {
    "archived=${thread.archived}, title=${thread.title}"
  }
}

private const val DEFAULT_CLAUDE_THREAD_COMMAND_TIMEOUT_MS = 30_000
private const val DEFAULT_CLAUDE_THREAD_RENAME_WAIT_TIMEOUT_MS = 10_000L
private const val DEFAULT_CLAUDE_THREAD_RENAME_POLL_INTERVAL_MS = 250L
private const val CLAUDE_PRINT_FLAG = "--print"
private const val CLAUDE_NAME_FLAG = "--name"
private const val CLAUDE_PROMPT_SEPARATOR = "--"
private const val CLAUDE_RENAME_PROMPT = "Acknowledge the session title update only. Do not inspect or modify files."
