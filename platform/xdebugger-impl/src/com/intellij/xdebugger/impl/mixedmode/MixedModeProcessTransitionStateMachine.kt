// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcessExtension
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcessExtension
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow

private val logger = logger<MixedModeProcessTransitionStateMachine>()

/**
 * A state machine that is intended to handle debugger events from both debug processes
 * It takes the work of synchronizing debuggers and solving conflicts (like low-level breakpoint is hit while a managed step over is active).
 * Start state is OnlyLowStarted, finish state is Exited
 */
internal class MixedModeProcessTransitionStateMachine(
  private val low: XDebugProcess,
  private val high: XDebugProcess,
  private val coroutineScope: CoroutineScope,
) {
  interface State
  open class WithHighLevelDebugSuspendContextState(val high: XSuspendContext) : State
  object OnlyLowStarted : State
  class BothRunning(val activeManagedStepping: Boolean = false) : State
  class ResumeLowResumeStarted(high: XSuspendContext) : WithHighLevelDebugSuspendContextState(high)
  object ResumeLowRunHighResumeStarted : State
  class ResumeLowStoppedAfterRunWhileHighResuming(val low: XSuspendContext) : State
  object WaitingForHighProcessPositionReached : State
  object LeaveHighRunningWaitingForLowStop : State
  class HighStoppedWaitingForLowProcessToStop(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStopped(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStoppedWaitingForLowStepToComplete(val highSuspendContext: XSuspendContext) : State
  class BothStopped(val low: XSuspendContext, val high: XSuspendContext) : State
  class ManagedStepStarted(val low: XSuspendContext) : State
  class MixedStepIntoStartedWaitingForHighDebuggerToBeResumed() : State
  class MixedStepIntoStartedHighDebuggerResumed() : State
  class LowLevelStepStarted(high: XSuspendContext) : WithHighLevelDebugSuspendContextState(high)
  class LowLevelRunToAddressStarted(high: XSuspendContext) : WithHighLevelDebugSuspendContextState(high)
  class HighLevelRunToAddressStarted(val sourcePosition: XSourcePosition, val high: XSuspendContext) : State
  class HighLevelRunToAddressStartedLowRun : State
  class HighLevelSetStatementStarted(val low : XSuspendContext) : State
  class HighLevelSetStatementHighRunning(val low : XSuspendContext) : State
  object Exited : State

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
  enum class StepType {
    Over, Into, Out
  }

  enum class MixedStepType {
    IntoLowFromHigh
  }

  private val nullObjectHighLevelSuspendContext: XSuspendContext = object : XSuspendContext() {}
  private val mainDispatcher = AppExecutorUtil.createBoundedApplicationPoolExecutor("Mixed mode state machine", 1).asCoroutineDispatcher()
  private val lowExtension get() = low.mixedModeDebugProcessExtension as XMixedModeLowLevelDebugProcessExtension
  private val highExtension get() = high.mixedModeDebugProcessExtension as XMixedModeHighLevelDebugProcessExtension

  // we assume that low debugger started before we created this class
  private var state: State = OnlyLowStarted
  val stateChannel: Channel<State> = Channel<State>()
  var suspendContextCoroutine: CoroutineScope = coroutineScope.childScope("suspendContextCoroutine", supervisor = true)
    private set

  private val eventFlow = MutableSharedFlow<Event>(extraBufferCapacity = Int.MAX_VALUE)

  init {
    coroutineScope.launch {
      eventFlow.collect { event ->
        withContext(mainDispatcher) {
          logger.runAndLogException { setInternal(event) }
        }
      }
    }
  }

  fun set(event: Event) {
    eventFlow.tryEmit(event).also { if (!it) logger.error("Failed to emit event $event") }
  }

  // to be called from the executor
  private suspend fun setInternal(event: Event) {
    logger.info("setInternal: state = ${state::class.simpleName}, event = ${event::class.simpleName}")
    val currentState = state
    when (event) {
      is HighStarted -> {
        when (currentState) {
          is OnlyLowStarted -> changeState(BothRunning())
          else -> throwTransitionIsNotImplemented(event)
        }
      }
      is PauseRequested -> {
        when (currentState) {
          is BothRunning -> {
            handlePauseEventWhenBothRunning()
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelPositionReached -> {
        when (currentState) {
          is WaitingForHighProcessPositionReached, is BothRunning -> {
            val stopThreadId = highExtension.getStoppedThreadId(event.suspendContext)
            lowExtension.pauseMixedModeSession(stopThreadId)

            logger.info("Low level process has been stopped")
            changeState(HighStoppedWaitingForLowProcessToStop(event.suspendContext))
          }
          is HighLevelSetStatementHighRunning -> {
            changeState(BothStopped(currentState.low, event.suspendContext))
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is LowLevelPositionReached -> {
        when (currentState) {
          is HighStoppedWaitingForLowProcessToStop -> {
            val highLevelSuspendContext = currentState.highSuspendContext!!
            val lowLevelContext = event.suspendContext
            changeState(BothStopped(lowLevelContext, highLevelSuspendContext))
          }
          is BothRunning, is MixedStepIntoStartedHighDebuggerResumed -> {
            val newState = run {
              if (currentState is MixedStepIntoStartedHighDebuggerResumed) {
                lowExtension.finishMixedStepInto()
              }
              else {
                // The low-level debug process is stopped, we need to ensure that we will be able to stop the managed one at this position
                val canStopHere = highExtension.canStopHere(event.suspendContext)
                if (!canStopHere)
                  return@run createStoppedStateWhenHighCantStop(event.suspendContext)
              }

              lowExtension.continueAllThreads(setOf(lowExtension.getStoppedThreadId(event.suspendContext)), silent = true)

              if (currentState is BothRunning && currentState.activeManagedStepping) {
                logger.info("Aborting the active managed step when we're in BothRunning state with an active breakpoint")
                highExtension.abortHighLevelStepping()
              }

              // please keep don't await it, it will break the status change logic
              highExtension.pauseMixedModeSession()
              return@run WaitingForHighProcessPositionReached
            }

            changeState(newState)
          }
          is OnlyHighStopped -> {
            changeState(BothStopped(event.suspendContext, requireNotNull(currentState.highSuspendContext)))
          }
          is OnlyHighStoppedWaitingForLowStepToComplete -> {
            val highSuspendCtx: XSuspendContext? = run {
              lowExtension.handleBreakpointDuringStep()

              // If we've set the null object instead of a real suspend context, we don't need to refresh it
              if (currentState.highSuspendContext != nullObjectHighLevelSuspendContext && lowExtension.lowToHighTransitionDuringLastStepHappened())
                highExtension.refreshSuspendContextOnLowLevelStepFinish(currentState.highSuspendContext) ?: currentState.highSuspendContext
              else
                currentState.highSuspendContext
            }

            val lowSuspendCtx = event.suspendContext
            changeState(BothStopped(lowSuspendCtx, requireNotNull(highSuspendCtx)))
          }
          is ResumeLowRunHighResumeStarted, is HighLevelRunToAddressStartedLowRun, is ManagedStepStarted -> {
            // if we are here one of the threads hit a stop event while we were waiting for a mono process to be resumed by managed debugger
            // if the stop event happened on a native, not mono thread, we can just wait for the managed debugger to resume the process and suspend it
            // if it's a mono thread, the mono thread either was resumed and stopped right or was executing native code
            // So, in all situations we need to:
            // 1. resume all threads except stopped one
            // 2. wait until managed resume is finished
            // 3. pause the process
            // TODO: need to handle new breakpoints hits, that may happen while we have the threads resumed
            lowExtension.continueAllThreads(setOf(lowExtension.getStoppedThreadId(event.suspendContext)), silent = true)
            if (currentState is ManagedStepStarted) {
              logger.info("Aborting the active managed step when the managed step just has been started")
              highExtension.abortHighLevelStepping()
            }

            changeState(ResumeLowStoppedAfterRunWhileHighResuming(event.suspendContext))
          }
          LeaveHighRunningWaitingForLowStop -> {
            changeState(BothStopped(event.suspendContext, nullObjectHighLevelSuspendContext))
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is ResumeRequested -> {
        when (currentState) {
          is BothStopped -> {
            changeState(ResumeLowResumeStarted(currentState.high))
            lowExtension.continueAllThreads(exceptThreads = emptySet(), silent = false)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is LowRun -> {
        when (currentState) {
          is ResumeLowResumeStarted, is LowLevelRunToAddressStarted -> {
            if (currentState.high == nullObjectHighLevelSuspendContext) {
              // The high-level debug process was unable to stop there, and we used null object suspend context,
              // so the high process is still resumed, we don't need to do anything with it
              changeState(BothRunning())
            }
            else {
              high.resume(currentState.high)
              changeState(ResumeLowRunHighResumeStarted)
            }
          }
          is BothStopped -> {
            changeState(OnlyHighStopped(currentState.high))
          }
          is LowLevelStepStarted -> {
            changeState(OnlyHighStoppedWaitingForLowStepToComplete(currentState.high))
          }
          is HighLevelRunToAddressStarted -> {
            withContext(Dispatchers.EDT) {
              high.runToPosition(currentState.sourcePosition, currentState.high)
            }
            changeState(HighLevelRunToAddressStartedLowRun())
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelDebuggerStepRequested -> {
        when (currentState) {
          is BothStopped -> {
            changeState(ManagedStepStarted(currentState.low))
            lowExtension.continueAllThreads(exceptThreads = emptySet(), silent = true)
            when (event.stepType) {
              StepType.Over -> {
                high.startStepOver(event.highSuspendContext)
              }
              StepType.Into -> {
                high.startStepInto(event.highSuspendContext)
              }
              StepType.Out -> {
                high.startStepOut(event.highSuspendContext)
              }
            }
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is MixedStepRequested -> {
        when (currentState) {
          is BothStopped -> {
            when (event.stepType) {
              MixedStepType.IntoLowFromHigh -> {
                val steppingThread = highExtension.getStoppedThreadId(event.highSuspendContext)
                // after this call, the native breakpoint is set, but the managed thread is stopped in suspend_current method
                suspendContextCoroutine.async { lowExtension.startMixedStepInto(steppingThread, event.highSuspendContext) }.await()

                changeState(MixedStepIntoStartedWaitingForHighDebuggerToBeResumed())

                // at first resume high level process, note that even though its state become resumed, it's not actually run. It will run once we continue all threads
                high.resume(currentState.high)

                // We've let the stepping thread run after the high-debug process is resumed
                lowExtension.continueAllThreads(setOf(steppingThread), silent = true)
              }
            }
          }
        }
      }
      is LowLevelStepRequested -> {
        when (currentState) {
          is BothStopped -> {
            lowExtension.beforeStep(event.mixedSuspendContext)

            when (event.stepType) {
              StepType.Over -> {
                low.startStepOver(event.mixedSuspendContext.lowLevelDebugSuspendContext)
              }
              StepType.Into -> {
                low.startStepInto(event.mixedSuspendContext.lowLevelDebugSuspendContext)
              }
              StepType.Out -> {
                low.startStepOut(event.mixedSuspendContext.lowLevelDebugSuspendContext)
              }
            }
            changeState(LowLevelStepStarted(currentState.high))
            logger.info("Low level step has been started")
          }
        }
      }
      is HighRun -> {
        when (currentState) {
          is OnlyHighStopped, is ResumeLowRunHighResumeStarted, is HighLevelRunToAddressStartedLowRun, is ManagedStepStarted -> {
            changeState(BothRunning(currentState is ManagedStepStarted))
          }
          is MixedStepIntoStartedWaitingForHighDebuggerToBeResumed -> {
            // Now run all threads letting the stepping thread reach its destination
            withContext(suspendContextCoroutine.coroutineContext) {
              lowExtension.continueAllThreads(exceptThreads = emptySet(), silent = true)
            }
            changeState(MixedStepIntoStartedHighDebuggerResumed())
          }
          is ResumeLowStoppedAfterRunWhileHighResuming -> {
            logger.info("We've met a native stop (breakpoint or other kind) while resuming. Now managed resume is completed, " +
                        "but the event thread is stopped by a low level debugger. Need to pause the process completely if that's possible")

            val canStopHere = highExtension.canStopHere(currentState.low)
            if (canStopHere)
              handlePauseEventWhenBothRunning()
            else {
              logger.info("High-level debug process can't stop here. Will leave it running and pause the low-level process")
              // we have recovered the high-level debug process from being blocked trying to resume,
              // now we need to stop the low-debug process
              lowExtension.pauseMixedModeSession(lowExtension.getStoppedThreadId(currentState.low))
              changeState(LeaveHighRunningWaitingForLowStop)
            }
          }
          is HighLevelSetStatementStarted -> {
            // Technically thread may not run (it's not necessary for this operation),
            // but the state machine will be notified as if it became running
            changeState(HighLevelSetStatementHighRunning(currentState.low))
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }
      is LowLevelRunToAddress -> {
        when (currentState) {
          is BothStopped -> {
            // Can firstly run to position in a low-level debug process and resume the high-level process afterward
            low.runToPosition(event.sourcePosition, event.low)
            changeState(LowLevelRunToAddressStarted(currentState.high))
          }
        }
      }
      is HighLevelRunToAddress -> {
        when (currentState) {
          is BothStopped -> {
            // As we do for resume, first let the low-level debug process run and secondly do runToPosition for a high-debug process
            changeState(HighLevelRunToAddressStarted(event.sourcePosition, event.high))
            lowExtension.continueAllThreads(emptySet(), silent = false)
          }
        }
      }
      is HighLevelSetNextStatementRequested -> {
        when(currentState) {
          is BothStopped -> {
            changeState(HighLevelSetStatementStarted(currentState.low))
            highExtension.setNextStatement(currentState.high, event.position)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }
      is Stop -> {
        changeState(Exited)
      }
    }
  }

  private suspend fun handlePauseEventWhenBothRunning() {
    highExtension.pauseMixedModeSession()
    changeState(WaitingForHighProcessPositionReached)
  }

  private suspend fun changeState(newState: State) {
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

  private fun createStoppedStateWhenHighCantStop(lowSuspendContext: XSuspendContext): BothStopped {
    return BothStopped(lowSuspendContext, nullObjectHighLevelSuspendContext)
  }

  private fun throwTransitionIsNotImplemented(event: Event) {
    error("Transition from ${state::class.simpleName} by event ${event::class.simpleName} is not implemented")
  }
}