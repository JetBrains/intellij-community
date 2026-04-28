// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.buildClaudeArchivedThreadTitle
import com.intellij.agent.workbench.claude.common.normalizeClaudeStoredThreadTitle
import com.intellij.agent.workbench.claude.common.resolveClaudeThreadTitleState
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentOpenTopLevelThreadDispatchService
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.application.ApplicationManager
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

internal fun interface ClaudeOpenTabRenameDispatcher {
  suspend fun dispatch(
    path: String,
    threadId: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    dispatchPlan: AgentInitialMessageDispatchPlan,
  ): Boolean
}

internal object ClaudeChatOpenTabRenameDispatcher : ClaudeOpenTabRenameDispatcher {
  override suspend fun dispatch(
    path: String,
    threadId: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    dispatchPlan: AgentInitialMessageDispatchPlan,
  ): Boolean {
    val dispatchService = ApplicationManager.getApplication().getService(AgentOpenTopLevelThreadDispatchService::class.java)
                          ?: return false
    return dispatchService.dispatchIfPresent(
      projectPath = path,
      provider = AgentSessionProvider.CLAUDE,
      threadId = threadId,
      launchSpec = launchSpec,
      initialMessageDispatchPlan = dispatchPlan,
    )
  }
}

internal class ClaudeOpenTabAwareThreadRenameEngine(
  private val backend: ClaudeSessionBackend,
  private val fallbackEngine: ClaudeThreadRenameEngine,
  private val openTabDispatcher: ClaudeOpenTabRenameDispatcher = ClaudeChatOpenTabRenameDispatcher,
  private val waitTimeoutMs: Long = DEFAULT_CLAUDE_THREAD_RENAME_WAIT_TIMEOUT_MS,
  private val pollIntervalMs: Long = DEFAULT_CLAUDE_THREAD_RENAME_POLL_INTERVAL_MS,
  private val currentTimeMs: () -> Long = System::currentTimeMillis,
  private val delayFn: suspend (Duration) -> Unit = { delay(it) },
  /**
   * Resolves the `claude` executable for the rename PTY launch. Defaults to the bare command name so unit tests
   * stay deterministic regardless of what is installed on the host. Production callers (notably
   * [ClaudeAgentSessionProviderDescriptor]) should forward
   * [ClaudeCliSupport.resolveExecutableOrDefaultViaTerminalResolver] so the launch uses an absolute path when
   * one can be located.
   */
  private val executableResolver: suspend () -> String = { ClaudeCliSupport.CLAUDE_COMMAND },
) : ClaudeThreadRenameEngine {
  private val stateObserver = ClaudeThreadRenameStateObserver(
    backend = backend,
    waitTimeoutMs = waitTimeoutMs,
    pollIntervalMs = pollIntervalMs,
    currentTimeMs = currentTimeMs,
    delayFn = delayFn,
  )

  override suspend fun rename(path: String, threadId: String, newTitle: String): Boolean {
    val normalizedTitle = normalizeClaudeStoredThreadTitle(newTitle) ?: return false
    val expectedState = resolveClaudeThreadTitleState(normalizedTitle, threadId)
    val currentThread = stateObserver.findThread(path, threadId) ?: return false
    if (currentThread.archived == expectedState.archived && currentThread.title == expectedState.title) {
      return true
    }

    val resumeLaunchSpec = buildClaudeResumeLaunchSpec(threadId, executableResolver())
    val dispatchedToOpenTab = try {
      openTabDispatcher.dispatch(
        path = path,
        threadId = threadId,
        launchSpec = resumeLaunchSpec,
        dispatchPlan = buildClaudeRenameDispatchPlan(normalizedTitle),
      )
    }
    catch (t: Throwable) {
      LOG.warn("Failed to dispatch Claude rename through open tab for $threadId", t)
      return false
    }

    if (!dispatchedToOpenTab) {
      return fallbackEngine.rename(path = path, threadId = threadId, newTitle = normalizedTitle)
    }

    // The `/rename` command was delivered to the open Claude tab's terminal. Claude CLI
    // acknowledges the rename interactively (the user sees "session renamed to ..."),
    // but the new title is not always persisted to the JSONL transcript synchronously:
    // it can land later as an `agentName` field or via `sessions-index.json` updates.
    // Treating that asynchrony as a rename failure would suppress the subsequent
    // `refreshProviderForPath`, leaving the editor tab stuck on the old title even
    // after the rename actually took effect. Observe the state on a best-effort basis
    // for logging only, and trust the dispatch so the refresh pipeline + file watcher
    // can update the tab title once the backend catches up.
    val observed = stateObserver.waitForObservedState(
      path = path,
      threadId = threadId,
      archived = expectedState.archived,
      expectedTitle = expectedState.title,
      commandOutputTail = OPEN_TAB_RENAME_COMMAND_OUTPUT,
    )
    if (!observed) {
      LOG.info(
        "Claude rename dispatched to open tab for $threadId but backend state did not reflect the new title within the observation window; " +
        "relying on follow-up refresh to update the tab presentation"
      )
    }
    return true
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    val current = stateObserver.findThread(path, threadId) ?: return false
    return rename(path, threadId, buildClaudeArchivedThreadTitle(current.title, threadId))
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    val current = stateObserver.findThread(path, threadId) ?: return false
    return rename(path, threadId, resolveClaudeThreadTitleState(current.title, threadId).title)
  }
}

internal class PtyClaudeThreadRenameEngine(
  private val backend: ClaudeSessionBackend,
  private val commandRunner: ClaudeThreadCommandRunner = PtyClaudeThreadCommandRunner(),
  private val commandTimeoutMs: Int = DEFAULT_CLAUDE_THREAD_COMMAND_TIMEOUT_MS,
  private val waitTimeoutMs: Long = DEFAULT_CLAUDE_THREAD_RENAME_WAIT_TIMEOUT_MS,
  private val pollIntervalMs: Long = DEFAULT_CLAUDE_THREAD_RENAME_POLL_INTERVAL_MS,
  private val currentTimeMs: () -> Long = System::currentTimeMillis,
  private val delayFn: suspend (Duration) -> Unit = { delay(it) },
  /** See the matching parameter on [ClaudeOpenTabAwareThreadRenameEngine] for the test/production contract. */
  private val executableResolver: suspend () -> String = { ClaudeCliSupport.CLAUDE_COMMAND },
) : ClaudeThreadRenameEngine {
  private val stateObserver = ClaudeThreadRenameStateObserver(
    backend = backend,
    waitTimeoutMs = waitTimeoutMs,
    pollIntervalMs = pollIntervalMs,
    currentTimeMs = currentTimeMs,
    delayFn = delayFn,
  )

  override suspend fun rename(path: String, threadId: String, newTitle: String): Boolean {
    val normalizedTitle = normalizeClaudeStoredThreadTitle(newTitle) ?: return false
    val expectedState = resolveClaudeThreadTitleState(normalizedTitle, threadId)
    val currentThread = stateObserver.findThread(path, threadId) ?: return false
    if (currentThread.archived == expectedState.archived && currentThread.title == expectedState.title) {
      return true
    }
    val resumeLaunchSpec = buildClaudeResumeLaunchSpec(threadId, executableResolver())
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
    return stateObserver.waitForObservedState(
      path = path,
      threadId = threadId,
      archived = expectedState.archived,
      expectedTitle = expectedState.title,
      commandOutputTail = commandResult.outputTail,
    )
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    val current = stateObserver.findThread(path, threadId) ?: return false
    return rename(path, threadId, buildClaudeArchivedThreadTitle(current.title, threadId))
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    val current = stateObserver.findThread(path, threadId) ?: return false
    return rename(path, threadId, resolveClaudeThreadTitleState(current.title, threadId).title)
  }
}

internal class ClaudeThreadRenameStateObserver(
  private val backend: ClaudeSessionBackend,
  private val waitTimeoutMs: Long = DEFAULT_CLAUDE_THREAD_RENAME_WAIT_TIMEOUT_MS,
  private val pollIntervalMs: Long = DEFAULT_CLAUDE_THREAD_RENAME_POLL_INTERVAL_MS,
  private val currentTimeMs: () -> Long = System::currentTimeMillis,
  private val delayFn: suspend (Duration) -> Unit = { delay(it) },
) {
  suspend fun findThread(path: String, threadId: String): ClaudeBackendThread? =
    backend.listThreads(path = path, openProject = null).firstOrNull { it.id == threadId }

  suspend fun waitForObservedState(
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

private fun buildClaudeRenameDispatchPlan(normalizedTitle: String): AgentInitialMessageDispatchPlan {
  return AgentInitialMessageDispatchPlan(
    postStartDispatchSteps = listOf(AgentInitialMessageDispatchStep(text = "/rename $normalizedTitle")),
  )
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
private const val OPEN_TAB_RENAME_COMMAND_OUTPUT = "<open-tab-dispatch>"
