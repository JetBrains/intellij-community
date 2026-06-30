// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageProviderDispatchRequest
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
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
sealed interface AgentThreadViewInitialMessageRetryDecision {
  data object Proceed : AgentThreadViewInitialMessageRetryDecision

  data object ProceedAndResetReadiness : AgentThreadViewInitialMessageRetryDecision

  data class RetryWithoutReadiness(@JvmField val backoffMs: Long) : AgentThreadViewInitialMessageRetryDecision

  data class RetryTransientBusyWithoutReadiness(@JvmField val backoffMs: Long) : AgentThreadViewInitialMessageRetryDecision

  data class RetryTransientBusyAfterReadiness(@JvmField val backoffMs: Long) : AgentThreadViewInitialMessageRetryDecision

  data class RetryTransientBusyAfterRewindAndReadiness(@JvmField val backoffMs: Long) : AgentThreadViewInitialMessageRetryDecision

  data class AwaitMorePostSendOutput(@JvmField val backoffMs: Long) : AgentThreadViewInitialMessageRetryDecision

  data object Stop : AgentThreadViewInitialMessageRetryDecision

  companion object {
    val PROCEED: AgentThreadViewInitialMessageRetryDecision = Proceed
  }
}

private class AgentThreadViewInitialMessageDispatcherLog

private val LOG = logger<AgentThreadViewInitialMessageDispatcherLog>()

internal class AgentThreadViewInitialMessageDispatcher(
  private val project: Project,
  private val file: AgentThreadViewVirtualFile,
  private val behavior: AgentThreadViewProviderBehavior,
  private val descriptor: AgentSessionProviderDescriptor?,
  private val tabSnapshotWriter: AgentThreadViewTabSnapshotWriter,
  private val planModeInitialPromptStopReporter: (Project, AgentThreadViewVirtualFile) -> Unit =
    AgentThreadViewRestoreNotificationService::reportInitialPromptPlanModeFailure,
) : AgentThreadViewDisposableController {
  private var pendingJob: Job? = null

  fun schedule(tab: AgentThreadViewTerminalTab) {
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
      var readinessCheckpoint: AgentThreadViewTerminalOutputCheckpoint? = null
      var retryCurrentStepWithoutReadiness = false
      var retryCurrentStepRequiresReadiness = false
      var beforeSendRetryAttempt = 0
      var afterSendObservationRetryAttempt = 0
      var transientBusyRetryStartedAtMs: Long? = null
      while (true) {
        if (stopTransientBusyDispatchIfTimedOut(transientBusyRetryStartedAtMs)) {
          return@launch
        }
        if (!retryCurrentStepWithoutReadiness) {
          when (tab.awaitInitialMessageReadiness(
            timeoutMs = INITIAL_MESSAGE_READINESS_TIMEOUT_MS,
            idleMs = INITIAL_MESSAGE_OUTPUT_IDLE_MS,
            checkpoint = readinessCheckpoint,
          )) {
            AgentThreadViewTerminalInputReadiness.READY -> Unit
            AgentThreadViewTerminalInputReadiness.TIMEOUT -> {
              if (retryCurrentStepRequiresReadiness || file.shouldDelayInitialMessageOnReadinessTimeout()) {
                yield()
                continue
              }
            }

            AgentThreadViewTerminalInputReadiness.TERMINATED -> return@launch
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
        retryCurrentStepWithoutReadiness = sendResult.retryStage != null && !sendResult.retryAfterReadiness
        retryCurrentStepRequiresReadiness = sendResult.retryAfterReadiness
        when (sendResult.retryStage) {
          AgentThreadViewInitialMessageRetryStage.BEFORE_SEND -> {
            beforeSendRetryAttempt++
            afterSendObservationRetryAttempt = 0
            transientBusyRetryStartedAtMs = null
          }
          AgentThreadViewInitialMessageRetryStage.AFTER_SEND_OBSERVATION -> {
            beforeSendRetryAttempt = 0
            afterSendObservationRetryAttempt++
            transientBusyRetryStartedAtMs = null
          }
          AgentThreadViewInitialMessageRetryStage.TRANSIENT_BUSY -> {
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
    tab: AgentThreadViewTerminalTab,
    beforeSendRetryAttempt: Int,
    afterSendObservationRetryAttempt: Int,
    transientBusyRetryStartedAtMs: Long?,
  ): AgentThreadViewInitialMessageSendResult {
    if (tab.sessionState.value != TerminalViewSessionState.Running) {
      return AgentThreadViewInitialMessageSendResult.NO_PROGRESS
    }
    val dispatch = file.acquireInitialMessageDispatch() ?: return AgentThreadViewInitialMessageSendResult.NO_PROGRESS
    when (val decision = behavior.beforeInitialMessageSend(file, tab, dispatch, beforeSendRetryAttempt)) {
      AgentThreadViewInitialMessageRetryDecision.Proceed,
      AgentThreadViewInitialMessageRetryDecision.ProceedAndResetReadiness,
        -> Unit
      AgentThreadViewInitialMessageRetryDecision.Stop -> {
        return stopInitialMessageDispatch(dispatch)
      }
      is AgentThreadViewInitialMessageRetryDecision.RetryWithoutReadiness -> {
        file.cancelInitialMessageDispatch(dispatch)
        delay(decision.backoffMs.milliseconds)
        return AgentThreadViewInitialMessageSendResult(
          progressed = false,
          retryStage = AgentThreadViewInitialMessageRetryStage.BEFORE_SEND,
        )
      }
      is AgentThreadViewInitialMessageRetryDecision.RetryTransientBusyWithoutReadiness -> {
        return retryAfterTransientBusy(
          dispatch = dispatch,
          backoffMs = decision.backoffMs,
          transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
          nextReadinessCheckpoint = null,
        )
      }
      is AgentThreadViewInitialMessageRetryDecision.RetryTransientBusyAfterReadiness -> {
        return retryAfterTransientBusy(
          dispatch = dispatch,
          backoffMs = decision.backoffMs,
          transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
          nextReadinessCheckpoint = tab.captureOutputCheckpoint(),
        )
      }
      is AgentThreadViewInitialMessageRetryDecision.RetryTransientBusyAfterRewindAndReadiness -> {
        return retryAfterTransientBusy(
          dispatch = dispatch,
          backoffMs = decision.backoffMs,
          transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
          nextReadinessCheckpoint = tab.captureOutputCheckpoint(),
          rewindDispatch = true,
        )
      }
      is AgentThreadViewInitialMessageRetryDecision.AwaitMorePostSendOutput -> {
        file.cancelInitialMessageDispatch(dispatch)
        delay(decision.backoffMs.milliseconds)
        return AgentThreadViewInitialMessageSendResult(
          progressed = false,
          retryStage = AgentThreadViewInitialMessageRetryStage.BEFORE_SEND,
        )
      }
    }
    if (behavior.isInitialMessageDispatchAlreadySatisfied(tab, dispatch)) {
      return completeInitialMessageDispatch(dispatch, readinessCheckpoint = null)
    }
    val readinessCheckpoint = tab.captureOutputCheckpoint()
    try {
      if (!sendInitialMessageDispatchAction(tab, dispatch)) {
        file.cancelInitialMessageDispatch(dispatch)
        return AgentThreadViewInitialMessageSendResult.NO_PROGRESS
      }
    }
    catch (e: CancellationException) {
      file.cancelInitialMessageDispatch(dispatch)
      throw e
    }
    catch (_: Throwable) {
      file.cancelInitialMessageDispatch(dispatch)
      return AgentThreadViewInitialMessageSendResult.NO_PROGRESS
    }
    if (behavior.requiresPostSendObservation(dispatch)) {
      var observationRetryAttempt = afterSendObservationRetryAttempt
      var observationStartedAtMs: Long? = null
      postSendObservationLoop@ while (true) {
        val observation = tab.awaitOutputObservation(
          checkpoint = readinessCheckpoint,
          timeoutMs = INITIAL_MESSAGE_POST_SEND_OBSERVATION_TIMEOUT_MS,
          idleMs = INITIAL_MESSAGE_POST_SEND_OUTPUT_IDLE_MS,
        )
        if (observation.readiness == AgentThreadViewTerminalInputReadiness.TERMINATED) {
          file.cancelInitialMessageDispatch(dispatch)
          return AgentThreadViewInitialMessageSendResult.NO_PROGRESS
        }
        val sendObservation = AgentThreadViewInitialMessageSendObservation(
          outputText = observation.text,
          recentOutputTail = tab.readRecentOutputTail(),
        )
        when (val decision = behavior.afterInitialMessageSendObservation(file, dispatch, sendObservation, observationRetryAttempt)) {
          AgentThreadViewInitialMessageRetryDecision.Proceed -> break@postSendObservationLoop
          AgentThreadViewInitialMessageRetryDecision.ProceedAndResetReadiness -> {
            return completeInitialMessageDispatch(dispatch, readinessCheckpoint = null, clearReadinessCheckpoint = true)
          }
          AgentThreadViewInitialMessageRetryDecision.Stop -> {
            return stopInitialMessageDispatch(dispatch)
          }
          is AgentThreadViewInitialMessageRetryDecision.RetryWithoutReadiness -> {
            file.cancelInitialMessageDispatch(dispatch)
            delay(decision.backoffMs.milliseconds)
            return AgentThreadViewInitialMessageSendResult(
              progressed = false,
              retryStage = AgentThreadViewInitialMessageRetryStage.AFTER_SEND_OBSERVATION,
            )
          }
          is AgentThreadViewInitialMessageRetryDecision.RetryTransientBusyWithoutReadiness -> {
            return retryAfterTransientBusy(
              dispatch = dispatch,
              backoffMs = decision.backoffMs,
              transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
              nextReadinessCheckpoint = null,
            )
          }
          is AgentThreadViewInitialMessageRetryDecision.RetryTransientBusyAfterReadiness -> {
            return retryAfterTransientBusy(
              dispatch = dispatch,
              backoffMs = decision.backoffMs,
              transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
              nextReadinessCheckpoint = tab.captureOutputCheckpoint(),
            )
          }
          is AgentThreadViewInitialMessageRetryDecision.RetryTransientBusyAfterRewindAndReadiness -> {
            return retryAfterTransientBusy(
              dispatch = dispatch,
              backoffMs = decision.backoffMs,
              transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
              nextReadinessCheckpoint = tab.captureOutputCheckpoint(),
              rewindDispatch = true,
            )
          }
          is AgentThreadViewInitialMessageRetryDecision.AwaitMorePostSendOutput -> {
            val nowMs = System.currentTimeMillis()
            val startedAtMs = observationStartedAtMs ?: nowMs
            observationStartedAtMs = startedAtMs
            if (nowMs - startedAtMs >= INITIAL_MESSAGE_TRANSIENT_BUSY_TIMEOUT_MS) {
              return stopInitialMessageDispatchAfterTransientBusyTimeout(dispatch = dispatch, elapsedMs = nowMs - startedAtMs)
            }
            delay(decision.backoffMs.milliseconds)
            observationRetryAttempt++
          }
        }
      }
    }
    return completeInitialMessageDispatch(dispatch, readinessCheckpoint)
  }

  private suspend fun sendInitialMessageDispatchAction(
    tab: AgentThreadViewTerminalTab,
    dispatch: AgentThreadViewInitialMessageDispatch,
  ): Boolean {
    return when (dispatch.action) {
      AgentInitialMessageDispatchAction.SEND_TEXT -> {
        if (dispatch.message.isEmpty()) {
          false
        }
        else {
          tab.sendInitialMessageText(
            dispatch.message,
            shouldExecute = true,
            useBracketedPasteMode = behavior.shouldUseBracketedPasteMode(dispatch.message) && descriptor?.isMenuCommandPrompt(dispatch.message) != true,
          )
          true
        }
      }
      AgentInitialMessageDispatchAction.PROVIDER -> {
        val providerDescriptor = descriptor ?: return false
        val threadId = file.threadId.ifBlank { file.sessionId }
        if (threadId.isBlank() || dispatch.message.isEmpty()) {
          return false
        }
        when (tab.awaitTerminalTitleThreadId(
          provider = providerDescriptor.provider,
          expectedThreadId = threadId,
          timeoutMs = PROVIDER_INITIAL_MESSAGE_ATTACH_TIMEOUT_MS,
        )) {
          AgentThreadViewTerminalInputReadiness.READY -> Unit
          AgentThreadViewTerminalInputReadiness.TIMEOUT -> LOG.warn(
            "Timed out waiting for terminal title thread id before provider initial message dispatch; dispatching via provider anyway"
          )
          AgentThreadViewTerminalInputReadiness.TERMINATED -> return false
        }
        providerDescriptor.dispatchInitialMessageToProvider(
          AgentInitialMessageProviderDispatchRequest(
            project = project,
            projectPath = file.projectPath,
            threadId = threadId,
            message = dispatch.message,
            mode = file.initialMessageMode ?: AgentInitialMessageMode.STANDARD,
            generationSettings = file.generationSettings,
          )
        )
      }
    }
  }

  private suspend fun retryAfterTransientBusy(
    dispatch: AgentThreadViewInitialMessageDispatch,
    backoffMs: Long,
    transientBusyRetryStartedAtMs: Long?,
    nextReadinessCheckpoint: AgentThreadViewTerminalOutputCheckpoint?,
    rewindDispatch: Boolean = false,
  ): AgentThreadViewInitialMessageSendResult {
    val nowMs = System.currentTimeMillis()
    val startedAtMs = transientBusyRetryStartedAtMs ?: nowMs
    if (nowMs - startedAtMs >= INITIAL_MESSAGE_TRANSIENT_BUSY_TIMEOUT_MS) {
      return stopInitialMessageDispatchAfterTransientBusyTimeout(dispatch = dispatch, elapsedMs = nowMs - startedAtMs)
    }
    if (rewindDispatch) {
      file.rewindInitialMessageDispatch(dispatch)
    }
    else {
      file.cancelInitialMessageDispatch(dispatch)
    }
    delay(backoffMs.milliseconds)
    return AgentThreadViewInitialMessageSendResult(
      progressed = false,
      nextReadinessCheckpoint = nextReadinessCheckpoint,
      retryStage = AgentThreadViewInitialMessageRetryStage.TRANSIENT_BUSY,
      transientBusyRetryStartedAtMs = startedAtMs,
      retryAfterReadiness = nextReadinessCheckpoint != null,
    )
  }

  private suspend fun stopTransientBusyDispatchIfTimedOut(transientBusyRetryStartedAtMs: Long?): Boolean {
    val startedAtMs = transientBusyRetryStartedAtMs ?: return false
    val elapsedMs = System.currentTimeMillis() - startedAtMs
    if (elapsedMs < INITIAL_MESSAGE_TRANSIENT_BUSY_TIMEOUT_MS) {
      return false
    }
    val dispatch = file.acquireInitialMessageDispatch() ?: return !file.hasPendingInitialMessageForDispatch()
    stopInitialMessageDispatchAfterTransientBusyTimeout(dispatch = dispatch, elapsedMs = elapsedMs)
    return true
  }

  private suspend fun stopInitialMessageDispatchAfterTransientBusyTimeout(
    dispatch: AgentThreadViewInitialMessageDispatch,
    elapsedMs: Long,
  ): AgentThreadViewInitialMessageSendResult {
    LOG.warn("Initial message dispatch stayed transient-busy for ${elapsedMs}ms; stopping at step ${dispatch.stepIndex}")
    return stopInitialMessageDispatch(dispatch)
  }

  private suspend fun stopInitialMessageDispatch(dispatch: AgentThreadViewInitialMessageDispatch): AgentThreadViewInitialMessageSendResult {
    LOG.debug("Stopped initial message dispatch at step ${dispatch.stepIndex}, action=${dispatch.action}")
    val shouldReportPlanModeInitialPromptStop = file.initialMessageMode == AgentInitialMessageMode.PLAN
    file.clearInitialMessageDispatchMetadata()
    tabSnapshotWriter.upsert(file.toSnapshot())
    if (shouldReportPlanModeInitialPromptStop) {
      planModeInitialPromptStopReporter(project, file)
    }
    return AgentThreadViewInitialMessageSendResult(progressed = false, stopDispatching = true)
  }

  private suspend fun completeInitialMessageDispatch(
    dispatch: AgentThreadViewInitialMessageDispatch,
    readinessCheckpoint: AgentThreadViewTerminalOutputCheckpoint?,
    clearReadinessCheckpoint: Boolean = false,
  ): AgentThreadViewInitialMessageSendResult {
    if (!file.completeInitialMessageDispatch(dispatch)) {
      return AgentThreadViewInitialMessageSendResult(
        progressed = false,
        nextReadinessCheckpoint = readinessCheckpoint.takeIf { file.hasPendingInitialMessageForDispatch() },
        clearReadinessCheckpoint = clearReadinessCheckpoint,
      )
    }
    tabSnapshotWriter.upsert(file.toSnapshot())
    return AgentThreadViewInitialMessageSendResult(
      progressed = true,
      nextReadinessCheckpoint = readinessCheckpoint.takeIf { file.hasPendingInitialMessageForDispatch() },
      clearReadinessCheckpoint = clearReadinessCheckpoint,
    )
  }
}

private enum class AgentThreadViewInitialMessageRetryStage {
  BEFORE_SEND,
  AFTER_SEND_OBSERVATION,
  TRANSIENT_BUSY,
}

private data class AgentThreadViewInitialMessageSendResult(
  @JvmField val progressed: Boolean,
  @JvmField val nextReadinessCheckpoint: AgentThreadViewTerminalOutputCheckpoint? = null,
  @JvmField val clearReadinessCheckpoint: Boolean = false,
  @JvmField val retryStage: AgentThreadViewInitialMessageRetryStage? = null,
  @JvmField val transientBusyRetryStartedAtMs: Long? = null,
  @JvmField val retryAfterReadiness: Boolean = false,
  @JvmField val stopDispatching: Boolean = false,
) {
  companion object {
    val NO_PROGRESS: AgentThreadViewInitialMessageSendResult = AgentThreadViewInitialMessageSendResult(progressed = false)
  }
}

private const val INITIAL_MESSAGE_TRANSIENT_BUSY_TIMEOUT_MS: Long = 120_000
private const val PROVIDER_INITIAL_MESSAGE_ATTACH_TIMEOUT_MS: Long = 10_000
