// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
sealed interface AgentChatInitialMessageRetryDecision {
  data object Proceed : AgentChatInitialMessageRetryDecision

  data object ProceedAndResetReadiness : AgentChatInitialMessageRetryDecision

  data class RetryWithoutReadiness(@JvmField val backoffMs: Long) : AgentChatInitialMessageRetryDecision

  data class RetryTransientBusyWithoutReadiness(@JvmField val backoffMs: Long) : AgentChatInitialMessageRetryDecision

  data object Stop : AgentChatInitialMessageRetryDecision

  companion object {
    val PROCEED: AgentChatInitialMessageRetryDecision = Proceed
  }
}

private class AgentChatInitialMessageDispatcherLog

private val LOG = logger<AgentChatInitialMessageDispatcherLog>()

internal class AgentChatInitialMessageDispatcher(
  private val project: Project,
  private val file: AgentChatVirtualFile,
  private val behavior: AgentChatProviderBehavior,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter,
  private val planModeInitialPromptStopReporter: (Project, AgentChatVirtualFile) -> Unit =
    AgentChatRestoreNotificationService::reportInitialPromptPlanModeFailure,
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
      var beforeSendRetryAttempt = 0
      var afterSendObservationRetryAttempt = 0
      var transientBusyRetryStartedAtMs: Long? = null
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

        val sendResult = sendInitialMessageIfReady(
          tab = tab,
          beforeSendRetryAttempt = beforeSendRetryAttempt,
          afterSendObservationRetryAttempt = afterSendObservationRetryAttempt,
          transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
        )
        if (sendResult.stopDispatching) {
          return@launch
        }
        if (sendResult.clearReadinessCheckpoint) {
          readinessCheckpoint = null
        }
        else if (sendResult.nextReadinessCheckpoint != null) {
          readinessCheckpoint = sendResult.nextReadinessCheckpoint
        }
        retryCurrentStepWithoutReadiness = sendResult.retryStage != null
        when (sendResult.retryStage) {
          AgentChatInitialMessageRetryStage.BEFORE_SEND -> {
            beforeSendRetryAttempt++
            afterSendObservationRetryAttempt = 0
            transientBusyRetryStartedAtMs = null
          }
          AgentChatInitialMessageRetryStage.AFTER_SEND_OBSERVATION -> {
            beforeSendRetryAttempt = 0
            afterSendObservationRetryAttempt++
            transientBusyRetryStartedAtMs = null
          }
          AgentChatInitialMessageRetryStage.TRANSIENT_BUSY -> {
            beforeSendRetryAttempt = 0
            afterSendObservationRetryAttempt = 0
            transientBusyRetryStartedAtMs = sendResult.transientBusyRetryStartedAtMs
          }
          null -> {
            beforeSendRetryAttempt = 0
            afterSendObservationRetryAttempt = 0
            transientBusyRetryStartedAtMs = null
          }
        }
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
    beforeSendRetryAttempt: Int,
    afterSendObservationRetryAttempt: Int,
    transientBusyRetryStartedAtMs: Long?,
  ): AgentChatInitialMessageSendResult {
    if (tab.sessionState.value != TerminalViewSessionState.Running) {
      return AgentChatInitialMessageSendResult.NO_PROGRESS
    }
    val dispatch = file.acquireInitialMessageDispatch() ?: return AgentChatInitialMessageSendResult.NO_PROGRESS
    when (val decision = behavior.beforeInitialMessageSend(file, tab, dispatch, beforeSendRetryAttempt)) {
      AgentChatInitialMessageRetryDecision.Proceed,
      AgentChatInitialMessageRetryDecision.ProceedAndResetReadiness -> Unit
      AgentChatInitialMessageRetryDecision.Stop -> {
        return stopInitialMessageDispatch(dispatch)
      }
      is AgentChatInitialMessageRetryDecision.RetryWithoutReadiness -> {
        file.cancelInitialMessageDispatch(dispatch)
        delay(decision.backoffMs.milliseconds)
        return AgentChatInitialMessageSendResult(
          progressed = false,
          retryStage = AgentChatInitialMessageRetryStage.BEFORE_SEND,
        )
      }
      is AgentChatInitialMessageRetryDecision.RetryTransientBusyWithoutReadiness -> {
        return retryAfterTransientBusy(dispatch, decision.backoffMs, transientBusyRetryStartedAtMs)
      }
    }
    if (behavior.isInitialMessageDispatchAlreadySatisfied(tab, dispatch)) {
      return completeInitialMessageDispatch(dispatch, readinessCheckpoint = null)
    }
    val readinessCheckpoint = tab.captureOutputCheckpoint()
    try {
      if (!sendInitialMessageDispatchAction(tab, dispatch)) {
        file.cancelInitialMessageDispatch(dispatch)
        return AgentChatInitialMessageSendResult.NO_PROGRESS
      }
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
      val sendObservation = AgentChatInitialMessageSendObservation(
        outputText = observation.text,
        recentOutputTail = tab.readRecentOutputTail(),
      )
      when (val decision = behavior.afterInitialMessageSendObservation(file, dispatch, sendObservation, afterSendObservationRetryAttempt)) {
        AgentChatInitialMessageRetryDecision.Proceed -> Unit
        AgentChatInitialMessageRetryDecision.ProceedAndResetReadiness -> {
          return completeInitialMessageDispatch(dispatch, readinessCheckpoint = null, clearReadinessCheckpoint = true)
        }
        AgentChatInitialMessageRetryDecision.Stop -> {
          return stopInitialMessageDispatch(dispatch)
        }
        is AgentChatInitialMessageRetryDecision.RetryWithoutReadiness -> {
          file.cancelInitialMessageDispatch(dispatch)
          delay(decision.backoffMs.milliseconds)
          return AgentChatInitialMessageSendResult(
            progressed = false,
            retryStage = AgentChatInitialMessageRetryStage.AFTER_SEND_OBSERVATION,
          )
        }
        is AgentChatInitialMessageRetryDecision.RetryTransientBusyWithoutReadiness -> {
          return retryAfterTransientBusy(dispatch, decision.backoffMs, transientBusyRetryStartedAtMs)
        }
      }
    }
    return completeInitialMessageDispatch(dispatch, readinessCheckpoint)
  }

  private fun sendInitialMessageDispatchAction(
    tab: AgentChatTerminalTab,
    dispatch: AgentChatInitialMessageDispatch,
  ): Boolean {
    return when (dispatch.action) {
      AgentInitialMessageDispatchAction.SEND_TEXT -> {
        if (dispatch.message.isEmpty()) {
          false
        }
        else {
          tab.sendText(
            dispatch.message,
            shouldExecute = true,
            useBracketedPasteMode = behavior.shouldUseBracketedPasteMode(dispatch.message),
          )
          true
        }
      }

      AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE -> tab.sendBackTab()
    }
  }

  private suspend fun retryAfterTransientBusy(
    dispatch: AgentChatInitialMessageDispatch,
    backoffMs: Long,
    transientBusyRetryStartedAtMs: Long?,
  ): AgentChatInitialMessageSendResult {
    val nowMs = System.currentTimeMillis()
    val startedAtMs = transientBusyRetryStartedAtMs ?: nowMs
    if (nowMs - startedAtMs >= INITIAL_MESSAGE_TRANSIENT_BUSY_TIMEOUT_MS) {
      LOG.warn("Initial message dispatch stayed transient-busy for ${nowMs - startedAtMs}ms; stopping at step ${dispatch.stepIndex}")
      return stopInitialMessageDispatch(dispatch)
    }
    file.cancelInitialMessageDispatch(dispatch)
    delay(backoffMs.milliseconds)
    return AgentChatInitialMessageSendResult(
      progressed = false,
      retryStage = AgentChatInitialMessageRetryStage.TRANSIENT_BUSY,
      transientBusyRetryStartedAtMs = startedAtMs,
    )
  }

  private suspend fun stopInitialMessageDispatch(dispatch: AgentChatInitialMessageDispatch): AgentChatInitialMessageSendResult {
    LOG.debug("Stopped initial message dispatch at step ${dispatch.stepIndex}, action=${dispatch.action}")
    file.clearInitialMessageDispatchMetadata()
    tabSnapshotWriter.upsert(file.toSnapshot())
    if (dispatch.action == AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE ||
        dispatch.completionPolicy == AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY) {
      planModeInitialPromptStopReporter(project, file)
    }
    return AgentChatInitialMessageSendResult(progressed = false, stopDispatching = true)
  }

  private suspend fun completeInitialMessageDispatch(
    dispatch: AgentChatInitialMessageDispatch,
    readinessCheckpoint: AgentChatTerminalOutputCheckpoint?,
    clearReadinessCheckpoint: Boolean = false,
  ): AgentChatInitialMessageSendResult {
    if (!file.completeInitialMessageDispatch(dispatch)) {
      return AgentChatInitialMessageSendResult(
        progressed = false,
        nextReadinessCheckpoint = readinessCheckpoint.takeIf { file.hasPendingInitialMessageForDispatch() },
        clearReadinessCheckpoint = clearReadinessCheckpoint,
      )
    }
    tabSnapshotWriter.upsert(file.toSnapshot())
    return AgentChatInitialMessageSendResult(
      progressed = true,
      nextReadinessCheckpoint = readinessCheckpoint.takeIf { file.hasPendingInitialMessageForDispatch() },
      clearReadinessCheckpoint = clearReadinessCheckpoint,
    )
  }
}

private enum class AgentChatInitialMessageRetryStage {
  BEFORE_SEND,
  AFTER_SEND_OBSERVATION,
  TRANSIENT_BUSY,
}

private data class AgentChatInitialMessageSendResult(
  @JvmField val progressed: Boolean,
  @JvmField val nextReadinessCheckpoint: AgentChatTerminalOutputCheckpoint? = null,
  @JvmField val clearReadinessCheckpoint: Boolean = false,
  @JvmField val retryStage: AgentChatInitialMessageRetryStage? = null,
  @JvmField val transientBusyRetryStartedAtMs: Long? = null,
  @JvmField val stopDispatching: Boolean = false,
) {
  companion object {
    val NO_PROGRESS: AgentChatInitialMessageSendResult = AgentChatInitialMessageSendResult(progressed = false)
  }
}

private const val INITIAL_MESSAGE_TRANSIENT_BUSY_TIMEOUT_MS: Long = 120_000
