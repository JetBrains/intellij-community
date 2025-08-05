// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
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
internal class MixedModeDotnetOnWinProcessTransitionStateMachine(
  var low: XDebugProcess,
  private val high: XDebugProcess,
  private val coroutineScope: CoroutineScope,
  private val highLevelDebuggerManagesStopEvents : Boolean = true
) {
  interface State
  open class WithHighLevelDebugSuspendContextState(val high: XSuspendContext) : State
  object OnlyLowStarted : State
  class BothRunning(val activeLowLevelStepping: Boolean = false) : State
  class ResumeStarted(high: XSuspendContext) : WithHighLevelDebugSuspendContextState(high)
  object PausingStarted : State
  object ResumeStartedHighResumed : State
  class WaitingForHighProcessPositionReached(val lowLevelSuspendContext : XSuspendContext) : State
  object LeaveHighRunningWaitingForLowStop : State
  class HighStoppedWaitingForLowProcessToStop(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStopped(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStoppedWaitingForLowStepToComplete(val highSuspendContext: XSuspendContext) : State
  class BothStopped(val low: XSuspendContext, val high: XSuspendContext) : State
  class MixedStepIntoStartedWaitingForHighDebuggerToBeResumed() : State
  class MixedStepIntoStartedHighDebuggerResumed() : State
  class LowLevelStepStarted(high: XSuspendContext) : WithHighLevelDebugSuspendContextState(high)
  class LowLevelRunToAddressStarted(high: XSuspendContext) : WithHighLevelDebugSuspendContextState(high)
  class HighLevelRunToAddressStarted(val sourcePosition: XSourcePosition, val high: XSuspendContext) : State
  class HighLevelRunToAddressStartedLowRun : State

  // Set of states for SetNextStatement feature
  // (we need so many states and transactions because low-level process has to refresh its state after high level set next statement completed):
  // BothStopped ---HighLevelSetNextStatementRequested---> HighLevelSetStatementPreparingLowLevelProcess ---LowRunning---> HighLevelSetStatementLowRunningHighRunRequested ---HighRunning--->  HighLevelSetStatementLowRunningHighRunning
  //             ---HighLevelPositionReached---> OnlyHighStopped ---LowLevelPositionReached---> BothStopped
  class HighLevelSetStatementPreparingLowLevelProcess(val high: XSuspendContext, val position: XSourcePosition) : State
  object HighLevelSetStatementLowRunningHighRunning : State
  object HighLevelSetStatementLowRunningHighRunRequested : State
  //

  class WaitingForBothDebuggersRunning(val lowLevelSteppingActive : Boolean = false) : State
  class WaitingForLowDebuggerRunning(val lowLevelSteppingActive : Boolean) : State
  class WaitingForHighDebuggerRunning(val lowLevelSteppingActive : Boolean) : State

  // Set of states that manage race conditions between low and high-level debuggers
  // in particular, we expect race conditions between events when a managed step is being performed, we may have such combinations(lowLevelSteppingActive == 1):
  //                                                                                                                            --LowRun--> BothRunning---...stopping as usual
  //                                                                                  --HighRun--> WaitingForLowDebuggerRunning
  //                                                                                                                            --HighPositionReached--> HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent --LowRun--> HighStoppedWaitingForLowProcessToStop --LowLevelPositionReached--> BothStopped.
  // 1. BothStopped --HighLevelDebuggerStepRequested--> WaitingForBothDebuggersRunning
  //                                                                                                                            --HighRun--> BothRunning ---...stopping as usual
  //                                                                                  --LowRun---> WaitingForHighDebuggerRunning
  //                                                                                         (the most frequent on my machine)  --LowLevelPositionReached--> LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent --HighRun-->  WaitingForHighProcessPositionReached --HighLevelPositionReached--> BothStopped.
  //
  // When native stepping we also move state machine into WaitingForBothDebuggersRunning state but lowLevelSteppingActive == 1. In this case we know that we don't have HighLevelPositionReached event from high-level debugger, that's why the graph is stripped comparing to the one above
  //
  //
  //                                                                 --HighRun--> WaitingForLowDebuggerRunning(lowLevelSteppingActive = true)--LowRun----------
  //                                                                                                                                                          |
  //                                                                                                                                                          |
  //                                                                                                                                                          |
  // 1. WaitingForBothDebuggersRunning(lowLevelSteppingActive = true)//                                                                                       |
  //                                                                                                                                                          |                                                                 (interrupted in the middle of native step, we resume low-level debugger)-->the same as shown below
  //                                                                                                                                         --HighRun--> BothRunning(lowLevelSteppingActive = true) -->LowLevelPositionReached
  //                                                                                                                                                                                                                            (normal low level step)-->the same as shown below
  //                                                                 --LowRun--> WaitingForHighDebuggerRunning(lowLevelSteppingActive = true)
  //                                                                                                                                                                                                                                                                                (interrupted in the middle of native step, we resume low-level debugger) --> WaitingForLowDebuggerRunning --Normal stopping due to high level debugger stepping logic as if we stop at a breakpoint-->BothStopped
  //                                                                                                                                         --LowLevelPositionReached--> LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent(lowLevelSteppingActive = true)--HighRun
  //                                                                                                                                                                                                                                                                                (normal low level step)--> WaitingForHighProcessPositionReached --HighProcessPositionReached-->BothStopped
  //

  class LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent(val lowLevelSuspendContext : XSuspendContext, val isLowLevelSteppingActive : Boolean) : State
  class HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent(val highLevelSuspendContext: XSuspendContext) : State
  //

  // We need ExitingInProgress state to not skip to HighRun/LowRun events on detaching
  object ExitingInProgress : State
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
  object ExitingStarted : Event
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
            lowExtension.pauseMixedModeSession(null)
            changeState(PausingStarted)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelPositionReached -> {
        when (currentState) {
          is WaitingForHighProcessPositionReached -> {
            changeState(BothStopped(currentState.lowLevelSuspendContext, event.suspendContext))
          }
          is PausingStarted, is BothRunning -> {
            //val stopThreadId = (currentState as? WaitingForHighProcessPositionReached)?.threadInitiatedStopId
            //                   ?: highExtension.getStoppedThreadId(event.suspendContext)
            //lowExtension.pauseMixedModeSession(stopThreadId)

            //logger.info("Low level process has been stopped")
            changeState(HighStoppedWaitingForLowProcessToStop(event.suspendContext))
            //changeState(BothStopped(event.suspendContext, event.suspendContext))
          }
          is HighLevelSetStatementLowRunningHighRunning -> {
            changeState(OnlyHighStopped(event.suspendContext))
            lowExtension.pauseMixedModeSession(null)
          }
          is WaitingForLowDebuggerRunning -> {
            assert(!currentState.lowLevelSteppingActive) { "When native stepping low level debugger stops first" }
            changeState(HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent(event.suspendContext))
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
          // TODO: delete MixedStepIntoStartedHighDebuggerResumed
          is BothRunning, is MixedStepIntoStartedHighDebuggerResumed -> {
            var stateChanged = false
            if (currentState is BothRunning && currentState.activeLowLevelStepping) {
              stateChanged = handleLowLevelStepWhenActiveStepping(event.suspendContext)
            }

            if (!stateChanged) {
              changeState(WaitingForHighProcessPositionReached(event.suspendContext))
            }
          }
          is OnlyHighStopped -> {
            changeState(BothStopped(event.suspendContext, requireNotNull(currentState.highSuspendContext)))
          }
          is OnlyHighStoppedWaitingForLowStepToComplete -> {
            val highSuspendCtx: XSuspendContext = run {

              //lowExtension.handleBreakpointDuringStep()

              checkNotNull(highExtension.refreshSuspendContextOnLowLevelStepFinish(currentState.highSuspendContext))
              // If we've set the null object instead of a real suspend context, we don't need to refresh it
              //if (currentState.highSuspendContext != nullObjectHighLevelSuspendContext && lowExtension.lowToHighTransitionDuringLastStepHappened())
              //  highExtension.refreshSuspendContextOnLowLevelStepFinish(currentState.highSuspendContext) ?: currentState.highSuspendContext
              //else
              //  currentState.highSuspendContext
            }
            val threadId = highExtension.getStoppedThreadId(highSuspendCtx)
            highExtension.afterLowLevelStepCompleted(threadId, false)
            // When mixed stepping u -> m, LLDB places a breakpoint in a ILStub when no symbols for frames below are loaded.
            // When we have them, it places a breakpoint in the highest native frame with symbols
            // in shouldContinueAfterNativeStepCompleted we check if debugger stopped in the ILStub frame and do continue to hit a breakpoint set by dotnet runtime (for stepping)
            // We only need it when there are no frames with symbols below
            if (highExtension.shouldContinueAfterNativeStepCompleted(threadId)) {
              changeState(WaitingForBothDebuggersRunning())
              low.resume(event.suspendContext)
            }
            else {
              val lowSuspendCtx = event.suspendContext
              changeState(BothStopped(lowSuspendCtx, requireNotNull(highSuspendCtx)))
            }
          }
          LeaveHighRunningWaitingForLowStop -> {
            changeState(BothStopped(event.suspendContext, nullObjectHighLevelSuspendContext))
          }
          is WaitingForHighDebuggerRunning -> {
            changeState(LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent(event.suspendContext, currentState.lowLevelSteppingActive))
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is ResumeRequested -> {
        when (currentState) {
          is BothStopped -> {
            changeState(WaitingForBothDebuggersRunning())
            low.resume(currentState.low)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is LowRun -> {
        when (currentState) {
          is WaitingForLowDebuggerRunning -> {
            changeState(BothRunning(currentState.lowLevelSteppingActive))
          }
          is ResumeStartedHighResumed -> {
            changeState(BothRunning())
          }
          is HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent -> {
            changeState(HighStoppedWaitingForLowProcessToStop(currentState.highLevelSuspendContext))
          }
          is WaitingForBothDebuggersRunning -> {
            changeState(WaitingForHighDebuggerRunning(currentState.lowLevelSteppingActive))
          }
          is LowLevelRunToAddressStarted -> {
            changeState(ResumeStartedHighResumed)
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
          is HighLevelSetStatementPreparingLowLevelProcess -> {
            highExtension.setNextStatement(currentState.high, currentState.position)
            changeState(HighLevelSetStatementLowRunningHighRunRequested)
          }
          is ExitingInProgress -> {
            logger.info("LowRun while exiting. Ignore")
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelDebuggerStepRequested -> {
        when (currentState) {
          is BothStopped -> {
            when (event.stepType) {
              StepType.Over -> {
                high.startStepOver(currentState.high)
              }
              StepType.Into -> {
                lowExtension.beforeManagedStepInto()
                high.startStepInto(currentState.high)
              }
              StepType.Out -> {
                high.startStepOut(currentState.high)
              }
            }

            low.resume(currentState.low)
            changeState(WaitingForBothDebuggersRunning())
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
            changeState(WaitingForBothDebuggersRunning(true))
            logger.info("Low level step has been started")
          }
        }
      }
      is HighRun -> {
        when (currentState) {
          is ResumeStarted -> {
            changeState(ResumeStartedHighResumed)
          }
          is WaitingForBothDebuggersRunning -> {
            changeState(WaitingForLowDebuggerRunning(currentState.lowLevelSteppingActive))
          }
          is LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent -> {
            var stateChanged = false
            if (currentState.isLowLevelSteppingActive) {
              stateChanged = handleLowLevelStepWhenActiveStepping(currentState.lowLevelSuspendContext)
            }
            if (!stateChanged) {
              changeState(WaitingForHighProcessPositionReached(currentState.lowLevelSuspendContext))
            }
          }
          is WaitingForHighDebuggerRunning -> {
            changeState(BothRunning(currentState.lowLevelSteppingActive))
          }
          is OnlyHighStopped, is ResumeStartedHighResumed, is HighLevelRunToAddressStartedLowRun -> {
            changeState(BothRunning(false))
          }
          is MixedStepIntoStartedWaitingForHighDebuggerToBeResumed -> {
            // Now run all threads letting the stepping thread reach its destination
            withContext(suspendContextCoroutine.coroutineContext) {
              lowExtension.continueAllThreads(exceptThreads = emptySet(), silent = true)
            }
            changeState(MixedStepIntoStartedHighDebuggerResumed())
          }
          is HighLevelSetStatementLowRunningHighRunRequested -> {
            // Technically thread may not run (it's not necessary for this operation),
            // but the state machine will be notified as if it became running
            changeState(HighLevelSetStatementLowRunningHighRunning)
          }
          is ExitingInProgress -> {
            logger.info("HighRun while exiting. Ignore")
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
            lowExtension.beforeHighLevelSetNextStatement()
            changeState(HighLevelSetStatementPreparingLowLevelProcess(currentState.high, event.position))
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }
      is ExitingStarted -> {
        changeState(ExitingInProgress)
      }
      is Stop -> {
        when (currentState) {
          is ExitingInProgress -> {
            changeState(Exited)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }
    }
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

  private suspend fun handleLowLevelStepWhenActiveStepping(lowLevelSuspendContext: XSuspendContext): Boolean {
    val threadId = lowExtension.getStoppedThreadId(lowLevelSuspendContext)

    if (highExtension.shouldContinueAfterNativeStepCompleted(threadId)) {
      highExtension.afterLowLevelStepCompleted(threadId, false)
      // State machine considers high-level debugger is already running
      changeState(WaitingForLowDebuggerRunning(false))
      low.resume(lowLevelSuspendContext)
      return true
    }

    highExtension.afterLowLevelStepCompleted(threadId, true)
    return false
  }

  private fun createStoppedStateWhenHighCantStop(lowSuspendContext: XSuspendContext): BothStopped {
    return BothStopped(lowSuspendContext, nullObjectHighLevelSuspendContext)
  }

  private fun throwTransitionIsNotImplemented(event: Event) {
    error("Transition from ${state::class.simpleName} by event ${event::class.simpleName} is not implemented")
  }
}