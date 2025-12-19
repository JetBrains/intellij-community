// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private val logger = logger<MixedModeStateMachineBase>()

/**
 * A state machine that is intended to handle debugger events from both debug processes
 * It takes the work of synchronizing debuggers and solving conflicts (like low-level breakpoint is hit while a managed step over is active).
 * Start state is OnlyLowStarted, finish state is Exited
 */
@ApiStatus.Internal
abstract class MixedModeStateMachineBase(
  protected val low: XDebugProcess,
  protected val high: XDebugProcess,
  protected val coroutineScope: CoroutineScope,
) {
  interface Event
  object HighStarted : Event
  object PauseRequested : Event
  object ResumeRequested : Event
  class HighLevelPositionReached(val suspendContext: XSuspendContext) : Event
  class LowLevelPositionReached(val suspendContext: XSuspendContext) : Event
  object HighRun : Event
  object LowRun : Event
  class LowLevelRunToAddress(val sourcePosition: XSourcePosition, val low: XSuspendContext) : Event
  class HighLevelRunToAddress(val sourcePosition: XSourcePosition, val high: XSuspendContext) : Event
  object Stop : Event
  class HighLevelDebuggerStepRequested(val highSuspendContext: XSuspendContext, val stepType: StepType) : Event
  class MixedStepRequested(val highSuspendContext: XSuspendContext, val stepType: MixedStepType) : Event
  class LowLevelStepRequested(val mixedSuspendContext: XMixedModeSuspendContext, val stepType: StepType) : Event
  class HighLevelSetNextStatementRequested(val position: XSourcePosition) : Event
  object ExitingStarted : Event
  enum class StepType {
    Over, Into, Out
  }

  enum class MixedStepType {
    IntoLowFromHigh
  }

  interface State
  object OnlyLowStarted : State
  class BothStopped(val low: XSuspendContext, val high: XSuspendContext) : State
  object Exited : State
  abstract class BothRunningBase(val highLevelSteppingActive: Boolean = false) : State
  class OnlyHighStopped(val highSuspendContext: XSuspendContext?) : State
  // We need ExitingInProgress state to not skip to HighRun/LowRun events on detaching
  object ExitingInProgress : State

  protected val mainDispatcher = AppExecutorUtil.createBoundedApplicationPoolExecutor("Mixed mode state machine", 1).asCoroutineDispatcher()

  // we assume that low debugger started before we created this class
  protected var state: State = OnlyLowStarted
  val stateChannel: Channel<State> = Channel<State>()
  var suspendContextCoroutine: CoroutineScope = coroutineScope.childScope("suspendContextCoroutine", supervisor = true)
    protected set

  private val eventFlow = MutableSharedFlow<Event>(extraBufferCapacity = Int.MAX_VALUE)

  init {
    coroutineScope.launch {
      eventFlow.collect { event ->
        withContext(mainDispatcher) {
          // I want OperationCancelled also be logged
          try {
            setInternal(event)
          }
          catch (e: Throwable) {
            logger.error("Failed to process event $event", e)
          }
        }
      }
    }
  }

  fun set(event: Event) {
    eventFlow.tryEmit(event).also { if (!it) logger.error("Failed to emit event $event") }
  }

  protected abstract suspend fun setInternal(event: Event)

  protected suspend fun changeState(newState: State) {
    if (newState is Exited)
      suspendContextCoroutine.coroutineContext.job.cancelAndJoin()
    else if (state is BothStopped) {
      suspendContextCoroutine.coroutineContext.job.cancelAndJoin()
      suspendContextCoroutine = coroutineScope.childScope("suspendContextCoroutine", supervisor = true)
    }
    val oldState = state
    state = newState

    stateChannel.send(newState)
    logger.info("state change (${oldState::class.simpleName} -> ${newState::class.simpleName})")
  }

  protected fun throwTransitionIsNotImplemented(event: Event) {
    error("Transition from ${state::class.simpleName} by event ${event::class.simpleName} is not implemented")
  }
}