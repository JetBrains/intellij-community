// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.*
import com.intellij.xdebugger.impl.mixedmode.MixedModeDotnetOnWinProcessTransitionStateMachine.States.*

private val logger = logger<MixedModeDotnetOnWinProcessTransitionStateMachine>()

/**
 * Mixed mode state machine for Dotnet process
 * This machine is different from @see MixedModeProcessTransitionStateMachine, because low-level debugger depends on high-level one in
 * this scenario. High-level debugger attaches to a process via Windows API and forwards Windows debug events to the low-level debugger.
 * It also exposes functions that are Windows debug API counterparts. Low-level debugger calls there functions instead of direct Windows API calls
 *
 * The main difference from @see MixedModeProcessTransitionStateMachine is that interaction between debuggers is more complex here
 */
internal class MixedModeDotnetOnWinProcessTransitionStateMachine(
  low: XDebugProcess,
  high: XDebugProcess,
  coroutineScope: CoroutineScope,
) : MixedModeStateMachineBase(low, high, coroutineScope) {

  private class States {
    class BothRunning(val lowLevelSteppingState: LowLevelSteppingState = LowLevelSteppingState.NotActive, highLevelSteppingActive: Boolean = false) : BothRunningBase(highLevelSteppingActive)
    object PausingStarted : State
    class WaitingForHighProcessPositionReached(val lowLevelSuspendContext: XSuspendContext, val highLevelSteppingActive: Boolean) : State
    class HighStoppedWaitingForLowProcessToStop(val highSuspendContext: XSuspendContext?, val highLevelSteppingActive: Boolean, val lowSteppingRunsToBeFinishedByManagedStepper: Boolean) : State

    // Set of states for SetNextStatement feature
    // (we need so many states and transactions because low-level process has to refresh its state after high level set next statement completed):
    // BothStopped ---HighLevelSetNextStatementRequested---> HighLevelSetStatementPreparingLowLevelProcess ---LowRunning---> HighLevelSetStatementLowRunningHighRunRequested ---HighRunning--->  HighLevelSetStatementLowRunningHighRunning
    //             ---HighLevelPositionReached---> OnlyHighStopped ---LowLevelPositionReached---> BothStopped
    class HighLevelSetStatementPreparingLowLevelProcess(val high: XSuspendContext, val position: XSourcePosition) : State
    object HighLevelSetStatementLowRunningHighRunning : State
    object HighLevelSetStatementLowRunningHighRunRequested : State
    //

    enum class LowLevelSteppingState {
      NotActive,
      Active,
      ActiveRunsToBeFinishedByManagedStepper
    }

    class WaitingForBothDebuggersRunning(val lowLevelSteppingState: LowLevelSteppingState = LowLevelSteppingState.NotActive, val highLevelSteppingActive: Boolean = false) : State
    class WaitingForLowDebuggerRunning(val lowLevelSteppingState: LowLevelSteppingState, val highLevelSteppingActive: Boolean) : State
    class WaitingForHighDebuggerRunning(val lowLevelSteppingState: LowLevelSteppingState, val highLevelSteppingActive: Boolean) : State

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
    // When native stepping we also move state machine into WaitingForBothDebuggersRunning state but lowLevelSteppingState == Active. In this case we know that we don't have HighLevelPositionReached event from high-level debugger, that's why the graph is stripped comparing to the one above
    //
    //
    //                                                                   --HighRun--> WaitingForLowDebuggerRunning(lowLevelSteppingState == Active)--LowRun-----
    //                                                                                                                                                          |
    //                                                                                                                                                          |
    //                                                                                                                                                          |
    // 1. WaitingForBothDebuggersRunning(lowLevelSteppingState == Active)                                                                                       |
    //                                                                                                                                                          |                                                                         (interrupted in the middle of native step, we resume low-level debugger)-->the same as shown below
    //                                                                                                                                             --HighRun-----> BothRunning(lowLevelSteppingState == Active) -->LowLevelPositionReached
    //                                                                                                                                                                                                                                    (normal low level step)-->the same as shown below
    //                                                                   --LowRun--> WaitingForHighDebuggerRunning(lowLevelSteppingState == Active)
    //                                                                                                                                                                                                                                                                                     (interrupted in the middle of native step, we resume low-level debugger) --> WaitingForLowDebuggerRunning --Normal stopping due to high level debugger stepping logic as if we stop at a breakpoint-->BothStopped
    //                                                                                                                                             --LowLevelPositionReached--> LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent(lowLevelSteppingState == Active)--HighRun
    //                                                                                                                                                                                                                                                                                     (normal low level step)--> WaitingForHighProcessPositionReached --HighProcessPositionReached-->BothStopped
    //

    class LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent(val lowLevelSuspendContext: XSuspendContext, val lowLevelSteppingState: LowLevelSteppingState, val highLevelSteppingActive: Boolean) : State
    class HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent(val highLevelSuspendContext: XSuspendContext, val highLevelSteppingActive: Boolean, val lowSteppingRunsToBeFinishedByManagedStepper: Boolean) : State
    //

    // We need ExitingInProgress state to not skip to HighRun/LowRun events on detaching
    object ExitingInProgress : State
  }

  // to be called from the executor
  override suspend fun setInternal(event: Event) {
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
            if (currentState.lowLevelSteppingState != LowLevelSteppingState.NotActive || currentState.highLevelSteppingActive)
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
            changeState(HighStoppedWaitingForLowProcessToStop(event.suspendContext, (currentState as? BothRunning)?.highLevelSteppingActive == true, (currentState as? BothRunning)?.lowLevelSteppingState == LowLevelSteppingState.ActiveRunsToBeFinishedByManagedStepper))
          }
          is HighLevelSetStatementLowRunningHighRunning -> {
            changeState(OnlyHighStopped(event.suspendContext))
            lowExtension.pauseMixedModeSession(null)
          }
          is WaitingForLowDebuggerRunning -> {
            assert(currentState.lowLevelSteppingState != LowLevelSteppingState.Active) { "When native stepping low level debugger stops first" }
            changeState(HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent(event.suspendContext, currentState.highLevelSteppingActive, currentState.lowLevelSteppingState == LowLevelSteppingState.ActiveRunsToBeFinishedByManagedStepper))
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
            if (currentState.lowSteppingRunsToBeFinishedByManagedStepper) {
              val handled = handleLowLevelActiveStep(lowLevelContext, LowLevelSteppingState.ActiveRunsToBeFinishedByManagedStepper)
              assert(!handled)
            }
            changeState(BothStopped(lowLevelContext, highLevelSuspendContext))
          }
          is BothRunning -> {
            val lowLevelStepHandled = handleLowLevelActiveStep(event.suspendContext, currentState.lowLevelSteppingState)
            if (!lowLevelStepHandled) {
              changeState(WaitingForHighProcessPositionReached(event.suspendContext, currentState.highLevelSteppingActive))
            }
          }
          is OnlyHighStopped -> {
            changeState(BothStopped(event.suspendContext, requireNotNull(currentState.highSuspendContext)))
          }
          is WaitingForHighDebuggerRunning -> {
            changeState(LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent(event.suspendContext, currentState.lowLevelSteppingState, currentState.highLevelSteppingActive))
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
            changeState(BothRunning(currentState.lowLevelSteppingState, currentState.highLevelSteppingActive))
          }
          is HighDebuggerAlreadyStoppedWaitingForDelayedLowDebuggerRunningEvent -> {
            changeState(HighStoppedWaitingForLowProcessToStop(currentState.highLevelSuspendContext, currentState.highLevelSteppingActive, currentState.lowSteppingRunsToBeFinishedByManagedStepper))
          }
          is WaitingForBothDebuggersRunning -> {
            changeState(WaitingForHighDebuggerRunning(currentState.lowLevelSteppingState, currentState.highLevelSteppingActive))
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
            changeState(WaitingForBothDebuggersRunning(LowLevelSteppingState.Active))
            logger.info("Low level step has been started")
          }
        }
      }
      is HighRun -> {
        when (currentState) {
          is WaitingForBothDebuggersRunning -> {
            changeState(WaitingForLowDebuggerRunning(currentState.lowLevelSteppingState, currentState.highLevelSteppingActive))
          }
          is LowDebuggerAlreadyStoppedWaitingForDelayedHighDebuggerRunningEvent -> {
            val lowLevelActiveStepHandled = handleLowLevelActiveStep(currentState.lowLevelSuspendContext, currentState.lowLevelSteppingState)
            if (!lowLevelActiveStepHandled) {
              changeState(WaitingForHighProcessPositionReached(currentState.lowLevelSuspendContext, currentState.highLevelSteppingActive))
            }
          }
          is WaitingForHighDebuggerRunning -> {
            changeState(BothRunning(currentState.lowLevelSteppingState, currentState.highLevelSteppingActive))
          }
          is OnlyHighStopped -> {
            changeState(BothRunning(LowLevelSteppingState.NotActive))
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
            changeState(WaitingForBothDebuggersRunning(LowLevelSteppingState.NotActive))
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

  private suspend fun handleLowLevelActiveStep(lowLevelSuspendContext: XSuspendContext, activeLowLevelStepping: LowLevelSteppingState): Boolean =
    when (activeLowLevelStepping) {
      LowLevelSteppingState.NotActive -> false
      LowLevelSteppingState.Active -> {
        val threadId = lowExtension.getStoppedThreadId(lowLevelSuspendContext)
        if (highExtension.needToContinueAfterNativeStepCompleted(threadId)) {
          highExtension.onLowLevelStepContinueAsItIsGoingToBeMixedStepOut(threadId)
          // State machine considers high-level debugger is already running
          changeState(WaitingForLowDebuggerRunning(LowLevelSteppingState.ActiveRunsToBeFinishedByManagedStepper, false))
          low.resume(lowLevelSuspendContext)
          true
        }
        else {
          highExtension.afterLowLevelStepCompleted(threadId, false)
          false
        }
      }
      LowLevelSteppingState.ActiveRunsToBeFinishedByManagedStepper -> {
        val threadId = lowExtension.getStoppedThreadId(lowLevelSuspendContext)
        highExtension.afterLowLevelStepCompleted(threadId, true)
        false
      }
    }
}