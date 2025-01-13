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
  class ResumeLowStoppedAfterRunWhileHighResuming(val low: XSuspendContext) : State
  object WaitingForHighProcessPositionReached : State
  object LeaveHighRunningWaitingForLowStop : State
  class HighStoppedWaitingForLowProcessToStop(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStopped(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStoppedWaitingForLowStepToComplete(val highSuspendContext: XSuspendContext) : State
  object OnlyLowStopped : State
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

  private val nullObjectHighLevelSuspendContext: XSuspendContext = object : XSuspendContext() {}

  enum class StepType {
    Over, Into, Out
  }

  enum class MixedStepType {
    IntoLowFromHigh
  }

  class HighLevelDebuggerStepRequested(val highSuspendContext: XSuspendContext, val stepType: StepType) : Event
  class MixedStepRequested(val highSuspendContext: XSuspendContext, val stepType: MixedStepType) : Event
  class LowLevelStepRequested(val mixedSuspendContext: XMixedModeSuspendContext, val stepType: StepType) : Event

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Mixed mode state machine", 1)
  private val stateMachineHelperScope = coroutineScope.childScope("Helper coroutine scope", Dispatchers.Default)

  // we assume that low debugger started before we created this class
  private var state: State = OnlyLowStarted

  private fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean {
    return suspendContext.javaClass.name.contains("Cidr")
  }

  private var suspendContextCoroutine: CoroutineScope = coroutineScope.childScope("suspendContextCoroutine", supervisor = true)
  suspend fun onPositionReached(suspendContext: XSuspendContext): XSuspendContext? {
    // TODO: we should read state only from executor thread
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
          is WaitingForHighProcessPositionReached, BothRunning -> {
            runBlocking(stateMachineHelperScope.coroutineContext) {
              val stopThreadId = high.getStoppedThreadId(event.suspendContext)
              low.pauseMixedModeSession(stopThreadId)
            }

            logger.info("Low level process has been stopped")
            changeState(HighStoppedWaitingForLowProcessToStop(event.suspendContext))
          }
          is HighLevelDebuggerResumedForStepOnlyLowStopped -> {
            val stopThreadId = high.getStoppedThreadId(event.suspendContext)
            low.pauseMixedModeSession(stopThreadId)

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
            val newState = runBlocking(stateMachineHelperScope.coroutineContext) {
              if (currentState is MixedStepIntoStartedHighDebuggerResumed) {
                low.removeTempBreakpoint(currentState.nativeBreakpointId)
              }
              else {
                // The low-level debug process is stopped, we need to ensure that we will be able to stop the managed one at this position
                val canStopHere = high.canStopHere(event.suspendContext)
                if (!canStopHere)
                  return@runBlocking createStoppedStateWhenHighCantStop(event.suspendContext)
              }

              low.continueAllThreads(setOf(low.getStoppedThreadId(event.suspendContext)), silent = true)

              // please keep don't await it, it will break the status change logic
              high.pauseMixedModeSession()
              return@runBlocking WaitingForHighProcessPositionReached
            }

            changeState(newState)
          }
          is OnlyHighStopped, is OnlyHighStoppedWaitingForLowStepToComplete -> {
            var highSuspendCtx: XSuspendContext? = null

            if (currentState is OnlyHighStopped)
              highSuspendCtx = currentState.highSuspendContext
            else
                runBlocking(stateMachineHelperScope.coroutineContext) {
                  currentState as OnlyHighStoppedWaitingForLowStepToComplete
                  low.continueHighDebuggerServiceThreads()
                  highSuspendCtx = currentState.highSuspendContext

                  // If we've set the null object instead of a real suspend context, we don't need to refresh it
                  if (currentState.highSuspendContext != nullObjectHighLevelSuspendContext && low.lowToHighTransitionDuringLastStepHappened())
                    highSuspendCtx = high.refreshSuspendContextOnLowLevelStepFinish(currentState.highSuspendContext)
                                     ?: currentState.highSuspendContext
                }

            val lowSuspendCtx = event.suspendContext
            changeState(BothStopped(lowSuspendCtx, requireNotNull(highSuspendCtx)))
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

            runBlocking(stateMachineHelperScope.coroutineContext) {
              low.continueAllThreads(setOf(low.getStoppedThreadId(event.suspendContext)), silent = true)
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
            runBlocking(stateMachineHelperScope.coroutineContext) {
              low.continueAllThreads(exceptThreads = emptySet(), silent = false)
            }
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is LowRun -> {
        when (currentState) {
          is ResumeLowResumeStarted -> {
            if (currentState.high == nullObjectHighLevelSuspendContext) {
              // The high-level debug process was unable to stop there, and we used null object suspend context,
              // so the high process is still resumed, we don't need to do anything with it
              changeState(BothRunning)
            }
            else {
              runBlocking(stateMachineHelperScope.coroutineContext) {
                high.asXDebugProcess.resume(currentState.high)
              }
              changeState(ResumeLowRunHighResumeStarted(currentState.high))
            }
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
              low.continueAllThreads(exceptThreads = emptySet(), silent = true)
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
        when (currentState) {
          is BothStopped -> {
            when (event.stepType) {
              MixedStepType.IntoLowFromHigh -> {
                val steppingThread = high.getStoppedThreadId(event.highSuspendContext)
                val breakpointId = runBlocking(stateMachineHelperScope.coroutineContext) {
                  // after this call, the native breakpoint is set, but the managed thread is stopped in suspend_current method
                  suspendContextCoroutine.async { low.startMixedStepInto(steppingThread, event.highSuspendContext) }.await()
                }

                changeState(MixedStepIntoStartedWaitingForHighDebuggerToBeResumed(breakpointId))

                runBlocking(stateMachineHelperScope.coroutineContext) {
                  // at first resume high level process, note that even though its state become resumed, it's not actually run. It will run once we continue all threads
                  high.asXDebugProcess.resume(currentState.high)

                  // We've let the stepping thread run after the high-debug process is resumed
                  low.continueAllThreads(setOf(steppingThread), silent = true)
                }
              }
            }
          }
        }
      }
      is LowLevelStepRequested -> {
        when (currentState) {
          is BothStopped -> {
            runBlocking(stateMachineHelperScope.coroutineContext) {
              low.beforeStep(event.mixedSuspendContext)

              when (event.stepType) {
                StepType.Over -> {
                  low.asXDebugProcess.startStepOver(event.mixedSuspendContext.lowLevelDebugSuspendContext)
                }
                StepType.Into -> {
                  low.asXDebugProcess.startStepInto(event.mixedSuspendContext.lowLevelDebugSuspendContext)
                }
                StepType.Out -> {
                  low.asXDebugProcess.startStepOut(event.mixedSuspendContext.lowLevelDebugSuspendContext)
                }
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
            // Now run all threads letting the stepping thread reach its destination
            runBlocking {
              suspendContextCoroutine.async {
                low.continueAllThreads(exceptThreads = emptySet(), silent = true)
              }
            }
            changeState(MixedStepIntoStartedHighDebuggerResumed(currentState.nativeBreakpointId))
          }
          is ResumeLowStoppedAfterRunWhileHighResuming -> {
            logger.info("We've met a native stop (breakpoint or other kind) while resuming. Now managed resume is completed, " +
                        "but the event thread is stopped by a low level debugger. Need to pause the process completely if that's possible")

            val canStopHere = runBlocking(stateMachineHelperScope.coroutineContext) { high.canStopHere(currentState.low) }
            if(canStopHere)
              handlePauseEventWhenBothRunning()
            else {
              logger.info("High-level debug process can't stop here. Will leave it running and pause the low-level process")
              // we have recovered the high-level debug process from being blocked trying to resume,
              // now we need to stop the low-debug process
              low.pauseMixedModeSession(low.getStoppedThreadId(currentState.low))
              changeState(LeaveHighRunningWaitingForLowStop)
            }
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
      runBlocking { suspendContextCoroutine.coroutineContext.job.cancelAndJoin() }
      suspendContextCoroutine = coroutineScope.childScope("suspendContextCoroutine", supervisor = true)
    }
    val oldState = state
    state = newState
    logger.info("state change : (${oldState::class.simpleName} -> ${newState::class.simpleName})")
  }

  private fun createStoppedStateWhenHighCantStop(lowSuspendContext: XSuspendContext): BothStopped {
    return BothStopped(lowSuspendContext, nullObjectHighLevelSuspendContext)
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


  fun throwTransitionIsNotImplemented(event: Event) {
    TODO("Transition from ${state::class.simpleName} by event ${event::class.simpleName} is not implemented")
  }

  // TODO race can be here since state is written only from the executor (the race should not cause anything serious)
  fun isMixedModeHighProcessReady(): Boolean = state !is OnlyLowStarted
}