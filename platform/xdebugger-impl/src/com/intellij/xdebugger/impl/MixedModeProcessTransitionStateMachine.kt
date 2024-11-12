// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.EDT
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.frame.XMixedModeHighLevelDebugProcess
import com.intellij.xdebugger.frame.XMixedModeLowLevelDebugProcess
import com.intellij.xdebugger.frame.XMixedModeSuspendContext
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.*

private val logger = com.intellij.openapi.diagnostic.logger<MixedModeProcessTransitionStateMachine>()

class MixedModeProcessTransitionStateMachine(
  private val low: XMixedModeDebugProcess,
  private val high: XMixedModeDebugProcess,
  private val coroutineScope: CoroutineScope,
) {
  interface State
  object BeforeStart : State

  object BothRunning : State
  object WaitingForHighProcessPositionReached : State
  class HighStoppedWaitingForLowProcessToStop(val highSuspendContext: XSuspendContext?) : State
  class OnlyHighStopped(val highSuspendContext: XSuspendContext?) : State
  object OnlyLowStopped : State
  class WaitForHighProcessPositionReachLowProcessResumedExceptStoppedThread(val isStopEventOnMainThread : Boolean) : State
  class BothStopped(val low: XSuspendContext, val high: XSuspendContext) : State
  class ManagedStepStarted(val low: XSuspendContext) : State
  class HighLevelDebuggerResumedForStepOnlyLowStopped(val low: XSuspendContext) : State

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

  class HighLevelDebuggerStepRequested(val highSuspendContext: XSuspendContext, val stepType: StepType) : Event

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Mixed mode state machine", 1)

  private var state: State = BeforeStart

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
      return XMixedModeSuspendContext((high as XDebugProcess).session, newState.low, newState.high, high as XDebugProcess, coroutineScope)
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

  private suspend fun setInternal(event: Event) {
    val currentState = state
    when (event) {
      is StopRequested -> {
        when (currentState) {
          is BothRunning -> {
            withContext(Dispatchers.EDT) {
              (high as XMixedModeHighLevelDebugProcess).pauseMixedModeSession()
            }
            state = WaitingForHighProcessPositionReached
            //.await().also { logger.info("High level process has been stopped") }
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelPositionReached -> {
        when (currentState) {
          is WaitingForHighProcessPositionReached, is WaitForHighProcessPositionReachLowProcessResumedExceptStoppedThread, BothRunning -> {
            withContext(Dispatchers.EDT) {
              val unBlockMainThread = currentState !is WaitForHighProcessPositionReachLowProcessResumedExceptStoppedThread || !currentState.isStopEventOnMainThread
              if (unBlockMainThread) {
                (low as XMixedModeLowLevelDebugProcess).pauseMixedModeSessionUnBlockMainThread {
                  (high as XMixedModeHighLevelDebugProcess).triggerBringingManagedThreadsToUnBlockedState()
                }
              }
              else {
                (low as XMixedModeLowLevelDebugProcess).pauseMixedModeSession()
              }
            }

            logger.info("Low level process has been stopped")
            state = HighStoppedWaitingForLowProcessToStop(event.suspendContext)
          }
          is HighLevelDebuggerResumedForStepOnlyLowStopped -> {
            (low as XMixedModeLowLevelDebugProcess).pauseMixedModeSessionUnBlockMainThread {
              (high as XMixedModeHighLevelDebugProcess).triggerBringingManagedThreadsToUnBlockedState()
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
          is BothRunning -> {
            withContext(Dispatchers.EDT) {
              val isStopEventOnMainThread = (low as XMixedModeLowLevelDebugProcess).isStopEventOnMainThread()
              (low as XMixedModeLowLevelDebugProcess).continueAllThreads(exceptEventThread = true)

              // please keep don't await it, it will break the status change logic
              (high as XMixedModeHighLevelDebugProcess).pauseMixedModeSession()

              withContext(executor.asCoroutineDispatcher()) {
                state = WaitForHighProcessPositionReachLowProcessResumedExceptStoppedThread(isStopEventOnMainThread)
              }
            }
          }
          is OnlyHighStopped -> {
            val lowSuspendCtx = event.suspendContext
            val highSuspendCtx = currentState.highSuspendContext!!
            state = BothStopped(lowSuspendCtx, highSuspendCtx)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is ResumeRequested -> {
        when (currentState) {
          is BothStopped -> {
            withContext(Dispatchers.EDT) {
              low.resumeAndWait()
              high.resumeAndWait()
            }
            state = BothRunning
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is LowRun -> {
        when (currentState) {
          is BeforeStart -> {
            // now only low level debugger reports its start
            state = BothRunning
          }
          is BothStopped -> {
            state = OnlyHighStopped(currentState.high)
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelDebuggerStepRequested -> {
        when (currentState) {
          is BothStopped -> {
            (low as XMixedModeLowLevelDebugProcess).continueAllThreads(exceptEventThread = false)
            when (event.stepType) {
              StepType.Over -> (high as XDebugProcess).startStepOver(event.highSuspendContext)
              StepType.Into -> (high as XDebugProcess).startStepInto(event.highSuspendContext)
              StepType.Out -> (high as XDebugProcess).startStepOut(event.highSuspendContext)
            }

            state = ManagedStepStarted(currentState.low)
          }
        }
      }

      is HighRun -> {
        when (currentState) {
          is ManagedStepStarted -> {
            state = HighLevelDebuggerResumedForStepOnlyLowStopped(currentState.low)
          }
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
    TODO("Transition from ${get()} by event $event is not implemented");
  }
}