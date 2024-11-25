// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.frame.*
import kotlinx.coroutines.*

private val logger = com.intellij.openapi.diagnostic.logger<MixedModeProcessTransitionStateMachine>()

@Suppress("SSBasedInspection")
class MixedModeProcessTransitionStateMachine(
  private val low: XMixedModeLowLevelDebugProcess,
  private val high: XMixedModeHighLevelDebugProcess,
  private val coroutineScope: CoroutineScope,
) {
  interface State

  object BothRunning : State
  class ResumeStarted(val low: XSuspendContext, val high: XSuspendContext) : State
  object WaitingForHighProcessPositionReached : State
  class HighStoppedWaitingForLowProcessToStop(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStopped(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStoppedWaitingForLowStepToComplete(val highSuspendContext: XSuspendContext) : State
  object OnlyLowStopped : State
  class WaitForHighProcessPositionReachLowProcessOnStopEventAndResumedExceptStoppedThread : State
  class BothStopped(val low: XSuspendContext, val high: XSuspendContext) : State
  class ManagedStepStarted(val low: XSuspendContext) : State
  class HighLevelDebuggerResumedForStepOnlyLowStopped(val low: XSuspendContext) : State
  class MixedStepIntoStartedWaitingForHighDebuggerToBeResumed(val nativeBreakpointId: Int) : State
  class MixedStepIntoStartedHighDebuggerResumed(val nativeBreakpointId: Int) : State
  class LowLevelStepStarted(val high: XSuspendContext) : State

  interface Event
  object StopRequested : Event
  object ResumeRequested : Event
  class HighLevelPositionReached(val suspendContext: XSuspendContext) : Event
  class LowLevelPositionReached(val suspendContext: XSuspendContext) : Event
  object LowStop : Event
  object HighRun : Event
  object LowRun : Event
  class HighStop(val highSuspendContext: XSuspendContext?) : Event

  enum class StepType {
    Over, Into, Out
  }

  enum class MixedStepType {
    IntoLowFromHigh
  }

  class HighLevelDebuggerStepRequested(val highSuspendContext: XSuspendContext, val stepType: StepType) : Event
  class MixedStepRequested(val highSuspendContext: XSuspendContext, val stepType: MixedStepType) : Event
  class LowLevelStepRequested(val lowSuspendContext: XSuspendContext, val stepType: StepType) : Event

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Mixed mode state machine", 1)
  private val stateMachineHelperScope = coroutineScope.childScope("Helper coroutine scope", Dispatchers.Default)

  private var state: State = BothRunning

  private fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean {
    return suspendContext.javaClass.name.contains("Cidr")
  }

  suspend fun onPositionReached(suspendContext: XSuspendContext): XSuspendContext? {
    val currentState = state
    val isHighSuspendContext = !isLowSuspendContext(suspendContext)
    if (isHighSuspendContext) {
      set(HighLevelPositionReached(suspendContext))
    }
    else {
      set(LowLevelPositionReached(suspendContext))
    }

    val newState = waitForNotNull { newState ->
      when (newState) {
        currentState -> null
        else -> newState
      }
    }

    if (newState is BothStopped) {
      return XMixedModeSuspendContext(high.asXDebugProcess.session, newState.low, newState.high, high, coroutineScope)
    }
    return null
  }

  suspend fun onSessionResumed(isLowLevel: Boolean): Boolean {
    val event = if (isLowLevel) LowRun else HighRun
    set(event)

    val handled = waitForNotNull { newState ->
      when (newState) {
        BothRunning -> false
        OnlyLowStopped, OnlyLowStopped -> true
        else -> null
      }
    }

    return handled
  }

  fun set(event: Event) {
    coroutineScope.launch(executor.asCoroutineDispatcher()) {
      setInternal(event)
    }
  }

  private fun setInternal(event: Event) {
    logger.info("setInternal: state = ${state::class.simpleName}, event = ${event::class.simpleName}")
    val currentState = state
    when (event) {
      is StopRequested -> {
        when (currentState) {
          is BothRunning -> {
            runBlocking(stateMachineHelperScope.coroutineContext) {
              high.pauseMixedModeSession()
            }
            state = WaitingForHighProcessPositionReached
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelPositionReached -> {
        when (currentState) {
          is WaitingForHighProcessPositionReached, is WaitForHighProcessPositionReachLowProcessOnStopEventAndResumedExceptStoppedThread, BothRunning -> {
            runBlocking(stateMachineHelperScope.coroutineContext) {
              val stopThreadId = high.getStoppedThreadId(event.suspendContext)
              if (currentState is WaitForHighProcessPositionReachLowProcessOnStopEventAndResumedExceptStoppedThread) {
                // Low level breakpoint has been triggered on the stopped thread, so this thread is not blocked
                low.pauseMixedModeSession(stopThreadId)
              }
              else {
                low.pauseMixedModeSessionUnBlockStopEventThread(stopThreadId) {
                  high.triggerBringingManagedThreadsToUnBlockedState()
                }
              }
            }

            logger.info("Low level process has been stopped")
            state = HighStoppedWaitingForLowProcessToStop(event.suspendContext)
          }
          is HighLevelDebuggerResumedForStepOnlyLowStopped -> {
            val stopThreadId = high.getStoppedThreadId(event.suspendContext)
            runBlocking(stateMachineHelperScope.coroutineContext) {
              low.pauseMixedModeSessionUnBlockStopEventThread(stopThreadId) {
                high.triggerBringingManagedThreadsToUnBlockedState()
              }
            }

            state = BothStopped(currentState.low, event.suspendContext)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is LowLevelPositionReached -> {
        when (currentState) {
          is HighStoppedWaitingForLowProcessToStop -> {
            val highLevelSuspendContext = currentState.highSuspendContext!!
            val lowLevelContext = event.suspendContext
            state = BothStopped(lowLevelContext, highLevelSuspendContext)
          }
          is MixedStepIntoStartedWaitingForHighDebuggerToBeResumed -> {
            error("TODO: low breakpoint was hit before the high session was resumed. We need to finish high session resuming " +
                  "and only after deal with this breakpoint ")
          }
          is BothRunning, is MixedStepIntoStartedHighDebuggerResumed -> {
            runBlocking(stateMachineHelperScope.coroutineContext) {
              if (currentState is MixedStepIntoStartedHighDebuggerResumed) {
                low.removeTempBreakpoint(currentState.nativeBreakpointId)
              }

              low.continueAllThreads(exceptEventThread = true)

              // please keep don't await it, it will break the status change logic
              high.pauseMixedModeSession()
            }
            state = WaitForHighProcessPositionReachLowProcessOnStopEventAndResumedExceptStoppedThread()
          }
          is OnlyHighStopped, is OnlyHighStoppedWaitingForLowStepToComplete -> {
            if (currentState is OnlyHighStoppedWaitingForLowStepToComplete)
              runBlocking(stateMachineHelperScope.coroutineContext) { low.continueHighDebuggerServiceThreads() }

            val highSuspendCtx = if (currentState is OnlyHighStopped) currentState.highSuspendContext!! else (currentState as OnlyHighStoppedWaitingForLowStepToComplete).highSuspendContext

            val lowSuspendCtx = event.suspendContext
            state = BothStopped(lowSuspendCtx, highSuspendCtx)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is ResumeRequested -> {
        when (currentState) {
          is BothStopped -> {
            state = ResumeStarted(currentState.low, currentState.high)
            runBlocking(stateMachineHelperScope.coroutineContext) {
              low.resumeAndWait()
              high.resumeAndWait()
            }
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is LowRun -> {
        when (currentState) {
          is ResumeStarted -> {
            state = OnlyHighStopped(currentState.high)
          }
          is BothStopped -> {
            state = OnlyHighStopped(currentState.high)
          }
          is LowLevelStepStarted -> {
            state = OnlyHighStoppedWaitingForLowStepToComplete(currentState.high)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelDebuggerStepRequested -> {
        when (currentState) {
          is BothStopped -> {
            state = ManagedStepStarted(currentState.low)
            runBlocking(stateMachineHelperScope.coroutineContext) {
              low.continueAllThreads(exceptEventThread = false)
              when (event.stepType) {
                StepType.Over -> {
                  high.asXDebugProcess.startStepOver(event.highSuspendContext)
                }
                StepType.Into -> {
                  low.continueAllThreads(exceptEventThread = false)
                  high.asXDebugProcess.startStepInto(event.highSuspendContext)
                }
                StepType.Out -> {
                  low.continueAllThreads(exceptEventThread = false)
                  high.asXDebugProcess.startStepOut(event.highSuspendContext)
                }
              }
            }
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is MixedStepRequested -> {
        val steppingThread = high.getStoppedThreadId(event.highSuspendContext)
        when (currentState) {
          is BothStopped -> {
            when (event.stepType) {
              MixedStepType.IntoLowFromHigh -> {
                val breakpointId = runBlocking(stateMachineHelperScope.coroutineContext) {

                  // after this call, the native breakpoint is set, but the managed thread is stopped in suspend_current method
                  val breakpointId = low.findAndSetBreakpointInNativeFunction(steppingThread) {
                    withContext(Dispatchers.EDT) {
                      high.triggerMonoMethodCommandsInternalMethodCallForExternMethodWeStepIn(event.highSuspendContext)
                    }
                  }

                  low.continueAllThreads(exceptEventThread = false)
                  breakpointId
                }

                state = MixedStepIntoStartedWaitingForHighDebuggerToBeResumed(breakpointId)

                runBlocking(stateMachineHelperScope.coroutineContext) {
                  high.resumeAndWait()
                }
              }
            }
          }
        }
      }
      is LowLevelStepRequested -> {
        when (currentState) {
          is BothStopped -> {
            when (event.stepType) {
              StepType.Over -> {
                this.low.asXDebugProcess.startStepOver(event.lowSuspendContext)
              }
              StepType.Into -> {
                this.low.asXDebugProcess.startStepInto(event.lowSuspendContext)
              }
              StepType.Out -> {
                this.low.asXDebugProcess.startStepOut(event.lowSuspendContext)
              }
            }
            state = LowLevelStepStarted(currentState.high)
          }
        }
      }
      is HighRun -> {
        when (currentState) {
          is OnlyHighStopped -> {
            state = BothRunning
          }
          is ManagedStepStarted -> {
            state = HighLevelDebuggerResumedForStepOnlyLowStopped(currentState.low)
          }
          is MixedStepIntoStartedWaitingForHighDebuggerToBeResumed -> {
            state = MixedStepIntoStartedHighDebuggerResumed(currentState.nativeBreakpointId)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }
    }
  }

  suspend fun waitFor(c: kotlin.reflect.KClass<*>) {
    waitFor { it::class == c }
  }

  suspend fun waitFor(stopIf: (State) -> Boolean) {
    waitForNotNull { newState -> if (stopIf(newState)) true else null }
  }

  suspend fun <T> waitForNotNull(func: (State) -> T?): T {
    return withContext(executor.asCoroutineDispatcher()) {
      withTimeout(50_000) {
        while (true) {
          val result = func(state)
          if (result != null) {
            return@withTimeout result
          }

          /*TODO: merge stopper*/
          delay(10)
        }

        error("unreachable")
      }
    }
  }

  fun get(): State = state

  fun throwTransitionIsNotImplemented(event: Event) {
    TODO("Transition from ${get()::class.simpleName} by event ${event::class.simpleName} is not implemented")
  }
}