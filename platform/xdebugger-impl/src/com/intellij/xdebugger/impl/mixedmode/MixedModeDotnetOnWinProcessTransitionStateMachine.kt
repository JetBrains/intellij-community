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
  private val low: XDebugProcess,
  private val high: XDebugProcess,
  private val coroutineScope: CoroutineScope,
) {
  interface State
  object OnlyLowStarted : State
  class BothRunning(val activeLowLevelStepping: Boolean = false, val highLevelSteppingActive: Boolean = false) : State
  object PausingStarted : State
  class WaitingForHighProcessPositionReached(val lowLevelSuspendContext : XSuspendContext, val highLevelSteppingActive: Boolean) : State
  class HighStoppedWaitingForLowProcessToStop(val highSuspendContext: XSuspendContext?, val highLevelSteppingActive: Boolean) : State
  class OnlyHighStopped(val highSuspendContext: XSuspendContext?) : State
  class BothStopped(val low: XSuspendContext, val high: XSuspendContext) : State

  // Set of states for SetNextStatement feature
  // (we need so many states and transactions because low-level process has to refresh its state after high level set next statement completed):
  // BothStopped ---HighLevelSetNextStatementRequested---> HighLevelSetStatementPreparingLowLevelProcess ---LowRunning---> HighLevelSetStatementLowRunningHighRunRequested ---HighRunning--->  HighLevelSetStatementLowRunningHighRunning
  //             ---HighLevelPositionReached---> OnlyHighStopped ---LowLevelPositionReached---> BothStopped
  class HighLevelSetStatementPreparingLowLevelProcess(val high: XSuspendContext, val position: XSourcePosition) : State
  object HighLevelSetStatementLowRunningHighRunning : State
  object HighLevelSetStatementLowRunningHighRunRequested : State
  //

  class WaitingForBothDebuggersRunning(val lowLevelSteppingActive: Boolean = false, val highLevelSteppingActive: Boolean = false) : State
  class WaitingForLowDebuggerRunning(val lowLevelSteppingActive: Boolean, val highLevelSteppingActive: Boolean) : State
  class WaitingForHighDebuggerRunning(val lowLevelSteppingActive: Boolean, val highLevelSteppingActive: Boolean) : State

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

  class LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent(val lowLevelSuspendContext : XSuspendContext, val isLowLevelSteppingActive : Boolean, val highLevelSteppingActive : Boolean) : State
  class HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent(val highLevelSuspendContext: XSuspendContext, val highLevelSteppingActive : Boolean) : State
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
            if (currentState.activeLowLevelStepping || currentState.highLevelSteppingActive)
              error("We had to ignore pause because we are in the middle of step")

            lowExtension.pauseMixedModeSession(null)
            changeState(PausingStarted)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelPositionReached -> {
        when (currentState) {
          is WaitingForHighProcessPositionReached -> {
            if (currentState.highLevelSteppingActive) {
              lowExtension.afterManagedStep()
            }
            changeState(BothStopped(currentState.lowLevelSuspendContext, event.suspendContext))
          }
          is PausingStarted, is BothRunning -> {
            changeState(HighStoppedWaitingForLowProcessToStop(event.suspendContext, (currentState as? BothRunning)?.highLevelSteppingActive == true))
          }
          is HighLevelSetStatementLowRunningHighRunning -> {
            changeState(OnlyHighStopped(event.suspendContext))
            lowExtension.pauseMixedModeSession(null)
          }
          is WaitingForLowDebuggerRunning -> {
            assert(!currentState.lowLevelSteppingActive) { "When native stepping low level debugger stops first" }
            changeState(HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent(event.suspendContext, currentState.highLevelSteppingActive))
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is LowLevelPositionReached -> {
        when (currentState) {
          is HighStoppedWaitingForLowProcessToStop -> {
            val highLevelSuspendContext = currentState.highSuspendContext!!
            val lowLevelContext = event.suspendContext
            if (currentState.highLevelSteppingActive) {
              lowExtension.afterManagedStep()
            }
            changeState(BothStopped(lowLevelContext, highLevelSuspendContext))
          }
          is BothRunning -> {
            val lowLevelStepHandled = currentState.activeLowLevelStepping && handleLowLevelActiveStep(event.suspendContext)
            if (!lowLevelStepHandled) {
              changeState(WaitingForHighProcessPositionReached(event.suspendContext, currentState.highLevelSteppingActive))
            }
          }
          is OnlyHighStopped -> {
            changeState(BothStopped(event.suspendContext, requireNotNull(currentState.highSuspendContext)))
          }
          is WaitingForHighDebuggerRunning -> {
            changeState(LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent(event.suspendContext, currentState.lowLevelSteppingActive, currentState.highLevelSteppingActive))
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
            changeState(BothRunning(currentState.lowLevelSteppingActive, currentState.highLevelSteppingActive))
          }
          is HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent -> {
            changeState(HighStoppedWaitingForLowProcessToStop(currentState.highLevelSuspendContext, currentState.highLevelSteppingActive))
          }
          is WaitingForBothDebuggersRunning -> {
            changeState(WaitingForHighDebuggerRunning(currentState.lowLevelSteppingActive, currentState.highLevelSteppingActive))
          }
          is BothStopped -> {
            changeState(OnlyHighStopped(currentState.high))
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
            val steppingThreadId = highExtension.getStoppedThreadId(currentState.high)
            lowExtension.beforeManagedStep(currentState.high, steppingThreadId, event.stepType == StepType.Into)

            when (event.stepType) {
              StepType.Over -> {
                high.startStepOver(currentState.high)
              }
              StepType.Into -> {
                high.startStepInto(currentState.high)
              }
              StepType.Out -> {
                high.startStepOut(currentState.high)
              }
            }

            low.resume(currentState.low)
            changeState(WaitingForBothDebuggersRunning(highLevelSteppingActive = true))
          }
          else -> throwTransitionIsNotImplemented(event)
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
          is WaitingForBothDebuggersRunning -> {
            changeState(WaitingForLowDebuggerRunning(currentState.lowLevelSteppingActive, currentState.highLevelSteppingActive))
          }
          is LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent -> {
            val lowLevelActiveStepHandled = currentState.isLowLevelSteppingActive && handleLowLevelActiveStep(currentState.lowLevelSuspendContext)
            if (!lowLevelActiveStepHandled) {
              changeState(WaitingForHighProcessPositionReached(currentState.lowLevelSuspendContext, currentState.highLevelSteppingActive))
            }
          }
          is WaitingForHighDebuggerRunning -> {
            changeState(BothRunning(currentState.lowLevelSteppingActive, currentState.highLevelSteppingActive))
          }
          is OnlyHighStopped -> {
            changeState(BothRunning(false))
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
            changeState(WaitingForBothDebuggersRunning(false))
          }
        }
      }
      is HighLevelRunToAddress -> {
        when (currentState) {
          is BothStopped -> {
            withContext(Dispatchers.EDT) { high.runToPosition(event.sourcePosition, currentState.high) }
            low.resume(currentState.low)
            changeState(WaitingForBothDebuggersRunning())
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

  private suspend fun handleLowLevelActiveStep(lowLevelSuspendContext: XSuspendContext): Boolean {
    val threadId = lowExtension.getStoppedThreadId(lowLevelSuspendContext)

    if (highExtension.shouldContinueAfterNativeStepCompleted(threadId)) {
      highExtension.afterLowLevelStepCompleted(threadId, false)
      // State machine considers high-level debugger is already running
      changeState(WaitingForLowDebuggerRunning(false, false))
      low.resume(lowLevelSuspendContext)
      return true
    }

    highExtension.afterLowLevelStepCompleted(threadId, true)
    return false
  }

  private fun throwTransitionIsNotImplemented(event: Event) {
    error("Transition from ${state::class.simpleName} by event ${event::class.simpleName} is not implemented")
  }
}