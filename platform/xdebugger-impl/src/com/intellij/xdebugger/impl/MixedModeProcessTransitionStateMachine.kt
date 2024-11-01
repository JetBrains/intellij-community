// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.EDT
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebugProcess
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
  object WaitForHighProcessPositionReachLowProcessResumedExceptEventThread : State
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
  class HighLevelDebuggerStepOverRequested(val highSuspendContext: XSuspendContext) : Event

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
              high.pauseMixedModeSession()
            }
            state = WaitingForHighProcessPositionReached
            //.await().also { logger.info("High level process has been stopped") }
          }
          else -> throwTransitionIsNotImplemented(event)
        }
      }

      is HighLevelPositionReached -> {
        when (currentState) {
          is WaitingForHighProcessPositionReached, WaitForHighProcessPositionReachLowProcessResumedExceptEventThread, BothRunning -> {
            withContext(Dispatchers.EDT) {
              low.pauseMixedModeSession()
            }
            state = HighStoppedWaitingForLowProcessToStop(event.suspendContext)
            //.await().also { logger.info("Low level process has been stopped") }
          }
          is HighLevelDebuggerResumedForStepOnlyLowStopped -> {
            (low as XMixedModeLowLevelDebugProcess).suspendAllExceptServiceThreads()
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
              (low as XMixedModeLowLevelDebugProcess).continueAllThreads(exceptEventThread = true)

              high.pauseMixedModeSession()

              withContext(executor.asCoroutineDispatcher()) {
                state = WaitForHighProcessPositionReachLowProcessResumedExceptEventThread
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

      is HighLevelDebuggerStepOverRequested -> {
        when (currentState) {
          is BothStopped -> {
            (low as XMixedModeLowLevelDebugProcess).continueAllThreads(exceptEventThread = false)
            (high as XDebugProcess).startStepOver(event.highSuspendContext)
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