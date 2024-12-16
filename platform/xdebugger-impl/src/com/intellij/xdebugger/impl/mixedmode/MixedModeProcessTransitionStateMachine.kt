// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcess
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcess
import com.intellij.xdebugger.mixedMode.asXDebugProcess
import kotlinx.coroutines.*
import kotlin.reflect.KClass

private val logger = logger<MixedModeProcessTransitionStateMachine>()

@Suppress("SSBasedInspection")
class MixedModeProcessTransitionStateMachine(
  private val low: XMixedModeLowLevelDebugProcess,
  private val high: XMixedModeHighLevelDebugProcess,
  private val coroutineScope: CoroutineScope,
) {
  interface State

  object OnlyLowStarted : State
  object BothRunning : State
  class ResumeLowResumeStarted(val high: XSuspendContext) : State
  class ResumeLowRunHighResumeStarted(val high: XSuspendContext) : State
  class ResumeLowStoppedAfterRunWhileHighResuming() : State
  object WaitingForHighProcessPositionReached : State
  class HighStoppedWaitingForLowProcessToStop(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStopped(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStoppedWaitingForLowStepToComplete(val highSuspendContext: XSuspendContext) : State
  object OnlyLowStopped : State
  class WaitForHighProcessPositionReachLowProcessOnStopEventAndResumedExceptStoppedThread : State
  class HighLevelDebuggerStoppedAfterStepWaitingForLowStop(val highLevelSuspendContext : XSuspendContext) : State
  class BothStopped(val low: XSuspendContext, val high: XSuspendContext) : State
  class ManagedStepStarted(val low: XSuspendContext) : State
  class HighLevelDebuggerResumedForStepOnlyLowStopped(val low: XSuspendContext) : State
  class MixedStepIntoStartedWaitingForHighDebuggerToBeResumed(val nativeBreakpointId: Int) : State
  class MixedStepIntoStartedHighDebuggerResumed(val nativeBreakpointId: Int) : State
  class LowLevelStepStarted(val high: XSuspendContext) : State

  interface Event
  object HighStarted : Event
  object PauseRequested : Event
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

  // we assume that low debugger started before we created this class
  private var state: State = OnlyLowStarted

  private fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean {
    return suspendContext.javaClass.name.contains("Cidr")
  }

  private var suspendContextCoroutine: CoroutineScope = coroutineScope.childScope("suspendContextCoroutine")
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
      return XMixedModeSuspendContext(high.asXDebugProcess.session, newState.low, newState.high, high, suspendContextCoroutine)
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
      is HighStarted -> {
        when (currentState) {
          is OnlyLowStarted -> changeState(BothRunning)
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
          is WaitingForHighProcessPositionReached, is WaitForHighProcessPositionReachLowProcessOnStopEventAndResumedExceptStoppedThread, BothRunning -> {
            runBlocking(stateMachineHelperScope.coroutineContext) {
              val stopThreadId = high.getStoppedThreadId(event.suspendContext)
              if (currentState is WaitForHighProcessPositionReachLowProcessOnStopEventAndResumedExceptStoppedThread) {
                // Low level breakpoint has been triggered on the stopped thread, so this thread is not blocked
                low.pauseMixedModeSession(stopThreadId)
              }
              else {
                // No native evaluation is possible on blocked in kernel thread
                low.pauseMixedModeSessionUnBlockStopEventThread(stopThreadId)
              }
            }

            logger.info("Low level process has been stopped")
            changeState(HighStoppedWaitingForLowProcessToStop(event.suspendContext))
          }
          is HighLevelDebuggerResumedForStepOnlyLowStopped -> {
            val stopThreadId = high.getStoppedThreadId(event.suspendContext)
            runBlocking(stateMachineHelperScope.coroutineContext) {
              low.pauseMixedModeSessionUnBlockStopEventThread(stopThreadId)
            }

            // Resume low level and stop it, it's made to have low level stack
            changeState(HighLevelDebuggerStoppedAfterStepWaitingForLowStop(event.suspendContext))
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
            runBlocking(stateMachineHelperScope.coroutineContext) {
              if (currentState is MixedStepIntoStartedHighDebuggerResumed) {
                low.removeTempBreakpoint(currentState.nativeBreakpointId)
              }

              low.continueAllThreads(exceptEventThread = true, silent = true)

              // please keep don't await it, it will break the status change logic
              high.pauseMixedModeSession()
            }
            changeState(WaitForHighProcessPositionReachLowProcessOnStopEventAndResumedExceptStoppedThread())
          }
          is OnlyHighStopped, is OnlyHighStoppedWaitingForLowStepToComplete -> {
            if (currentState is OnlyHighStoppedWaitingForLowStepToComplete)
              runBlocking(stateMachineHelperScope.coroutineContext) { low.continueHighDebuggerServiceThreads() }

            val highSuspendCtx = if (currentState is OnlyHighStopped) currentState.highSuspendContext!! else (currentState as OnlyHighStoppedWaitingForLowStepToComplete).highSuspendContext

            val lowSuspendCtx = event.suspendContext
            changeState(BothStopped(lowSuspendCtx, highSuspendCtx))
          }
          is HighLevelDebuggerStoppedAfterStepWaitingForLowStop -> {
            changeState(BothStopped(event.suspendContext, currentState.highLevelSuspendContext))
          }
          is ResumeLowRunHighResumeStarted -> {
            // if we are here one of the threads hit a stop event while we were waiting for a mono process to be resumed by managed debugger
            // if the stop event happened on a native, not mono thread, we can just wait for the managed debugger to resume the process and suspend it
            // if it's a mono thread, the mono thread either was resumed and stopped right or was executing native code
            // So, in all situations we need to:
            // 1. resume all threads except stopped one
            // 2. wait until managed resume is finished
            // 3. pause the process
            runBlocking(stateMachineHelperScope.coroutineContext) { low.continueAllThreads(exceptEventThread = true, silent = true) }

            changeState(ResumeLowStoppedAfterRunWhileHighResuming())
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is ResumeRequested -> {
        when (currentState) {
          is BothStopped -> {
            changeState(ResumeLowResumeStarted(currentState.high))
            runBlocking(stateMachineHelperScope.coroutineContext) {
              low.continueAllThreads(exceptEventThread = false, silent = false)
            }
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is LowRun -> {
        when (currentState) {
          is ResumeLowResumeStarted -> {
            runBlocking(stateMachineHelperScope.coroutineContext) {
              high.asXDebugProcess.resume(currentState.high)
            }
            changeState(ResumeLowRunHighResumeStarted(currentState.high))
          }
          is BothStopped -> {
            changeState(OnlyHighStopped(currentState.high))
          }
          is LowLevelStepStarted -> {
            changeState(OnlyHighStoppedWaitingForLowStepToComplete(currentState.high))
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelDebuggerStepRequested -> {
        when (currentState) {
          is BothStopped -> {
            changeState(ManagedStepStarted(currentState.low))
            runBlocking(stateMachineHelperScope.coroutineContext) {
              low.continueAllThreads(exceptEventThread = false, silent = true)
              when (event.stepType) {
                StepType.Over -> {
                  high.asXDebugProcess.startStepOver(event.highSuspendContext)
                }
                StepType.Into -> {
                  high.asXDebugProcess.startStepInto(event.highSuspendContext)
                }
                StepType.Out -> {
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
                  checkNotNull(suspendContextCoroutine).async {
                    low.startMixedStepInto(steppingThread, event.highSuspendContext)
                  }.await()
                }

                changeState(MixedStepIntoStartedWaitingForHighDebuggerToBeResumed(breakpointId))

                runBlocking(stateMachineHelperScope.coroutineContext) {
                  // at first resume high level process, note that even though its state become resumed, it's not actually run. It will run once we continue all threads
                  high.asXDebugProcess.resume(currentState.high)
                  low.continueAllThreads(exceptEventThread = false, silent = true)
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
            changeState(LowLevelStepStarted(currentState.high))
            logger.info("Low level step has been started")
          }
        }
      }
      is HighRun -> {
        when (currentState) {
          is OnlyHighStopped, is ResumeLowRunHighResumeStarted -> {
            changeState(BothRunning)
          }
          is ManagedStepStarted -> {
            changeState(HighLevelDebuggerResumedForStepOnlyLowStopped(currentState.low))
          }
          is MixedStepIntoStartedWaitingForHighDebuggerToBeResumed -> {
            changeState(MixedStepIntoStartedHighDebuggerResumed(currentState.nativeBreakpointId))
          }
          is ResumeLowStoppedAfterRunWhileHighResuming -> {
            logger.info("We've met a native stop (breakpoint or other kind) while resuming. Now managed resume is completed, " +
                        "but the event thread is stopped by a low level debugger. Need to pause the process completely")
            handlePauseEventWhenBothRunning()
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }
    }
  }

  private fun handlePauseEventWhenBothRunning() {
    runBlocking(stateMachineHelperScope.coroutineContext) { high.pauseMixedModeSession() }
    changeState(WaitingForHighProcessPositionReached)
  }

  private fun changeState(newState: State) {
    if (state is BothStopped) {
      suspendContextCoroutine.cancel()
      suspendContextCoroutine = coroutineScope.childScope("suspendContextCoroutine")
    }
    val oldState = state
    state = newState
    logger.info("state change : (${oldState::class.simpleName} -> ${newState::class.simpleName})")
  }

  suspend fun waitFor(c: KClass<*>) {
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

  fun isMixedModeHighProcessReady(): Boolean = state !is OnlyLowStarted
}