// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
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
sealed interface AgentChatInitialMessageRetryDecision {
  data object Proceed : AgentChatInitialMessageRetryDecision

  data object ProceedAndResetReadiness : AgentChatInitialMessageRetryDecision

  data class RetryWithoutReadiness(@JvmField val backoffMs: Long) : AgentChatInitialMessageRetryDecision

  data class RetryTransientBusyWithoutReadiness(@JvmField val backoffMs: Long) : AgentChatInitialMessageRetryDecision

  data class RetryTransientBusyAfterReadiness(@JvmField val backoffMs: Long) : AgentChatInitialMessageRetryDecision

  data class RetryTransientBusyAfterRewindAndReadiness(@JvmField val backoffMs: Long) : AgentChatInitialMessageRetryDecision

  data class AwaitMorePostSendOutput(@JvmField val backoffMs: Long) : AgentChatInitialMessageRetryDecision

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
  private val descriptor: AgentSessionProviderDescriptor?,
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
            AgentChatTerminalInputReadiness.READY -> Unit
            AgentChatTerminalInputReadiness.TIMEOUT -> {
              if (retryCurrentStepRequiresReadiness || file.shouldDelayInitialMessageOnReadinessTimeout()) {
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
        retryCurrentStepWithoutReadiness = sendResult.retryStage != null && !sendResult.retryAfterReadiness
        retryCurrentStepRequiresReadiness = sendResult.retryAfterReadiness
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
      AgentChatInitialMessageRetryDecision.ProceedAndResetReadiness,
        -> Unit
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
        return retryAfterTransientBusy(
          dispatch = dispatch,
          backoffMs = decision.backoffMs,
          transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
          nextReadinessCheckpoint = null,
        )
      }
      is AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterReadiness -> {
        return retryAfterTransientBusy(
          dispatch = dispatch,
          backoffMs = decision.backoffMs,
          transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
          nextReadinessCheckpoint = tab.captureOutputCheckpoint(),
        )
      }
      is AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterRewindAndReadiness -> {
        return retryAfterTransientBusy(
          dispatch = dispatch,
          backoffMs = decision.backoffMs,
          transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
          nextReadinessCheckpoint = tab.captureOutputCheckpoint(),
          rewindDispatch = true,
        )
      }
      is AgentChatInitialMessageRetryDecision.AwaitMorePostSendOutput -> {
        file.cancelInitialMessageDispatch(dispatch)
        delay(decision.backoffMs.milliseconds)
        return AgentChatInitialMessageSendResult(
          progressed = false,
          retryStage = AgentChatInitialMessageRetryStage.BEFORE_SEND,
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
      var observationRetryAttempt = afterSendObservationRetryAttempt
      var observationStartedAtMs: Long? = null
      postSendObservationLoop@ while (true) {
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
        when (val decision = behavior.afterInitialMessageSendObservation(file, dispatch, sendObservation, observationRetryAttempt)) {
          AgentChatInitialMessageRetryDecision.Proceed -> break@postSendObservationLoop
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
            return retryAfterTransientBusy(
              dispatch = dispatch,
              backoffMs = decision.backoffMs,
              transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
              nextReadinessCheckpoint = null,
            )
          }
          is AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterReadiness -> {
            return retryAfterTransientBusy(
              dispatch = dispatch,
              backoffMs = decision.backoffMs,
              transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
              nextReadinessCheckpoint = tab.captureOutputCheckpoint(),
            )
          }
          is AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterRewindAndReadiness -> {
            return retryAfterTransientBusy(
              dispatch = dispatch,
              backoffMs = decision.backoffMs,
              transientBusyRetryStartedAtMs = transientBusyRetryStartedAtMs,
              nextReadinessCheckpoint = tab.captureOutputCheckpoint(),
              rewindDispatch = true,
            )
          }
          is AgentChatInitialMessageRetryDecision.AwaitMorePostSendOutput -> {
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
    tab: AgentChatTerminalTab,
    dispatch: AgentChatInitialMessageDispatch,
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
            useBracketedPasteMode = behavior.shouldUseBracketedPasteMode(dispatch.message),
            terminalSendMode = behavior.initialMessageTerminalSendMode(dispatch),
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
          AgentChatTerminalInputReadiness.READY -> Unit
          AgentChatTerminalInputReadiness.TIMEOUT -> LOG.warn(
            "Timed out waiting for terminal title thread id before provider initial message dispatch; dispatching via provider anyway"
          )
          AgentChatTerminalInputReadiness.TERMINATED -> return false
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
    dispatch: AgentChatInitialMessageDispatch,
    backoffMs: Long,
    transientBusyRetryStartedAtMs: Long?,
    nextReadinessCheckpoint: AgentChatTerminalOutputCheckpoint?,
    rewindDispatch: Boolean = false,
  ): AgentChatInitialMessageSendResult {
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
    return AgentChatInitialMessageSendResult(
      progressed = false,
      nextReadinessCheckpoint = nextReadinessCheckpoint,
      retryStage = AgentChatInitialMessageRetryStage.TRANSIENT_BUSY,
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
    dispatch: AgentChatInitialMessageDispatch,
    elapsedMs: Long,
  ): AgentChatInitialMessageSendResult {
    LOG.warn("Initial message dispatch stayed transient-busy for ${elapsedMs}ms; stopping at step ${dispatch.stepIndex}")
    return stopInitialMessageDispatch(dispatch)
  }

  private suspend fun stopInitialMessageDispatch(dispatch: AgentChatInitialMessageDispatch): AgentChatInitialMessageSendResult {
    LOG.debug("Stopped initial message dispatch at step ${dispatch.stepIndex}, action=${dispatch.action}")
    val shouldReportPlanModeInitialPromptStop =
      dispatch.completionPolicy == AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY ||
      file.initialMessageMode == AgentInitialMessageMode.PLAN
    file.clearInitialMessageDispatchMetadata()
    tabSnapshotWriter.upsert(file.toSnapshot())
    if (shouldReportPlanModeInitialPromptStop) {
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
  @JvmField val retryAfterReadiness: Boolean = false,
  @JvmField val stopDispatching: Boolean = false,
) {
  companion object {
    val NO_PROGRESS: AgentChatInitialMessageSendResult = AgentChatInitialMessageSendResult(progressed = false)
  }
}

private const val INITIAL_MESSAGE_TRANSIENT_BUSY_TIMEOUT_MS: Long = 120_000
private const val PROVIDER_INITIAL_MESSAGE_ATTACH_TIMEOUT_MS: Long = 10_000
