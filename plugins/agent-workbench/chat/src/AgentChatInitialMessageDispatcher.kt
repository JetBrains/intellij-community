// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.milliseconds

internal sealed interface AgentChatInitialMessageRetryDecision {
  data object Proceed : AgentChatInitialMessageRetryDecision

  data class RetryWithoutReadiness(@JvmField val backoffMs: Long) : AgentChatInitialMessageRetryDecision

  companion object {
    val PROCEED: AgentChatInitialMessageRetryDecision = Proceed
  }
}

internal class AgentChatInitialMessageDispatcher(
  private val file: AgentChatVirtualFile,
  private val behavior: AgentChatProviderBehavior,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter,
) : AgentChatDisposableController {
  private var pendingJob: Job? = null

  fun schedule(tab: AgentChatTerminalTab) {
    if (!file.hasPendingInitialMessageForDispatch()) {
      return
    }
    if (tab.sessionState.value == TerminalViewSessionState.Terminated) {
      return
    }
    if (pendingJob?.isActive == true) {
      return
    }
    pendingJob = tab.coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      val state = tab.sessionState.first { it != TerminalViewSessionState.NotStarted }
      if (state != TerminalViewSessionState.Running) {
        return@launch
      }
      var readinessCheckpoint: AgentChatTerminalOutputCheckpoint? = null
      var retryCurrentStepWithoutReadiness = false
      var retryAttempt = 0
      while (true) {
        if (!retryCurrentStepWithoutReadiness) {
          when (tab.awaitInitialMessageReadiness(
            timeoutMs = INITIAL_MESSAGE_READINESS_TIMEOUT_MS,
            idleMs = INITIAL_MESSAGE_OUTPUT_IDLE_MS,
            checkpoint = readinessCheckpoint,
          )) {
            AgentChatTerminalInputReadiness.READY -> Unit
            AgentChatTerminalInputReadiness.TIMEOUT -> {
              if (file.shouldDelayInitialMessageOnReadinessTimeout()) {
                yield()
                continue
              }
            }

            AgentChatTerminalInputReadiness.TERMINATED -> return@launch
          }
        }

        val sendResult = sendInitialMessageIfReady(tab, retryAttempt)
        if (sendResult.nextReadinessCheckpoint != null) {
          readinessCheckpoint = sendResult.nextReadinessCheckpoint
        }
        retryCurrentStepWithoutReadiness = sendResult.retryCurrentStepWithoutReadiness
        retryAttempt = if (sendResult.retryCurrentStepWithoutReadiness) retryAttempt + 1 else 0
        if (!sendResult.progressed) {
          if (tab.sessionState.value != TerminalViewSessionState.Running || !file.hasPendingInitialMessageForDispatch()) {
            return@launch
          }
          yield()
          continue
        }
        if (!file.hasPendingInitialMessageForDispatch()) {
          return@launch
        }
        yield()
      }
    }.also { job ->
      job.invokeOnCompletion {
        if (pendingJob === job) {
          pendingJob = null
        }
      }
    }
  }

  override fun dispose() {
    pendingJob?.cancel()
    pendingJob = null
  }

  private suspend fun sendInitialMessageIfReady(
    tab: AgentChatTerminalTab,
    retryAttempt: Int,
  ): AgentChatInitialMessageSendResult {
    if (tab.sessionState.value != TerminalViewSessionState.Running) {
      return AgentChatInitialMessageSendResult.NO_PROGRESS
    }
    val dispatch = file.acquireInitialMessageDispatch() ?: return AgentChatInitialMessageSendResult.NO_PROGRESS
    when (val decision = behavior.beforeInitialMessageSend(file, tab, dispatch, retryAttempt)) {
      AgentChatInitialMessageRetryDecision.Proceed -> Unit
      is AgentChatInitialMessageRetryDecision.RetryWithoutReadiness -> {
        file.cancelInitialMessageDispatch(dispatch)
        delay(decision.backoffMs.milliseconds)
        return AgentChatInitialMessageSendResult(
          progressed = false,
          retryCurrentStepWithoutReadiness = true,
        )
      }
    }
    val readinessCheckpoint = tab.captureOutputCheckpoint()
    try {
      tab.sendText(
        dispatch.message,
        shouldExecute = true,
        useBracketedPasteMode = behavior.shouldUseBracketedPasteMode(dispatch.message),
      )
    }
    catch (e: CancellationException) {
      file.cancelInitialMessageDispatch(dispatch)
      throw e
    }
    catch (_: Throwable) {
      file.cancelInitialMessageDispatch(dispatch)
      return AgentChatInitialMessageSendResult.NO_PROGRESS
    }
    if (behavior.requiresPostSendObservation(dispatch)) {
      val observation = tab.awaitOutputObservation(
        checkpoint = readinessCheckpoint,
        timeoutMs = INITIAL_MESSAGE_POST_SEND_OBSERVATION_TIMEOUT_MS,
        idleMs = INITIAL_MESSAGE_POST_SEND_OUTPUT_IDLE_MS,
      )
      if (observation.readiness == AgentChatTerminalInputReadiness.TERMINATED) {
        file.cancelInitialMessageDispatch(dispatch)
        return AgentChatInitialMessageSendResult.NO_PROGRESS
      }
      when (val decision = behavior.afterInitialMessageSendObservation(file, dispatch, observation.text, retryAttempt)) {
        AgentChatInitialMessageRetryDecision.Proceed -> Unit
        is AgentChatInitialMessageRetryDecision.RetryWithoutReadiness -> {
          file.cancelInitialMessageDispatch(dispatch)
          delay(decision.backoffMs.milliseconds)
          return AgentChatInitialMessageSendResult(
            progressed = false,
            retryCurrentStepWithoutReadiness = true,
          )
        }
      }
    }
    if (!file.completeInitialMessageDispatch(dispatch)) {
      return AgentChatInitialMessageSendResult(
        progressed = false,
        nextReadinessCheckpoint = readinessCheckpoint.takeIf { file.hasPendingInitialMessageForDispatch() },
      )
    }
    tabSnapshotWriter.upsert(file.toSnapshot())
    return AgentChatInitialMessageSendResult(
      progressed = true,
      nextReadinessCheckpoint = readinessCheckpoint.takeIf { file.hasPendingInitialMessageForDispatch() },
    )
  }
}

private data class AgentChatInitialMessageSendResult(
  @JvmField val progressed: Boolean,
  @JvmField val nextReadinessCheckpoint: AgentChatTerminalOutputCheckpoint? = null,
  @JvmField val retryCurrentStepWithoutReadiness: Boolean = false,
) {
  companion object {
    val NO_PROGRESS: AgentChatInitialMessageSendResult = AgentChatInitialMessageSendResult(progressed = false)
  }
}
