// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Progressive
import com.intellij.openapi.util.EmptyRunnable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * An executor that can run exactly one task. The task can be re-started many times, but there is at most one running task at any moment.
 * [tryStartProcess] should be invoked to start execution.
 *
 * It is safe to invoke [tryStartProcess] many times, and it is guaranteed that no more than one task is executing at any moment.
 *
 * It is guaranteed that after [tryStartProcess] the task will be executed at least one more time. Task can reset scheduled
 * executions by invoking [clearScheduledFlag]
 */
internal class SingleTaskExecutor(private val task: Progressive) {
  internal interface AutoclosableProgressive : AutoCloseable, Progressive {
    override fun close()
  }

  private inner class StateAwareTask(private val task: Progressive) : AutoclosableProgressive {
    private val used = AtomicBoolean(false)
    override fun close() {
      if (used.compareAndSet(false, true)) {
        runWithStateHandling(EmptyRunnable.getInstance())
      }
    }

    override fun run(indicator: ProgressIndicator) {
      if (used.compareAndSet(false, true)) {
        runWithStateHandling { task.run(indicator) }
      }
      else {
        LOG.error("StateAwareTask cannot be reused")
      }
    }
  }

  private enum class RunState { STOPPED, STARTING, RUNNING, STOPPING }

  private val runState = MutableStateFlow(RunState.STOPPED)
  private val shouldContinueBackgroundProcessing = AtomicBoolean(false)
  private val modificationCount = MutableStateFlow<Long>(0)

  private fun runWithStateHandling(runnable: Runnable) {
    try {
      do {
        try {
          runState.value.let { currentStateForAssert ->
            LOG.assertTrue(currentStateForAssert == RunState.STARTING, "Old state should be STARTING, but was $currentStateForAssert")
          }
          runState.value = RunState.RUNNING

          // shouldContinueBackgroundProcessing is normally cleared before reading next item from the queue.
          // Here we clear the flag just in case, if runnable fail to clear the flag (e.g. during cancellation)
          shouldContinueBackgroundProcessing.set(false)
          runnable.run()
        }
        finally {
          runState.value.let { currentStateForAssert ->
            LOG.assertTrue(currentStateForAssert == RunState.RUNNING, "Old state should be RUNNING, but was $currentStateForAssert")
          }
          runState.value = RunState.STOPPING
        }
      }
      while (shouldContinueBackgroundProcessing.get() && runState.compareAndSet(RunState.STOPPING, RunState.STARTING))
    }
    finally {
      if (runState.compareAndSet(RunState.STOPPING, RunState.STOPPED)) {
        modificationCount.update { it + 1 }
      }
    }
  }

  /**
   * Generates a task that will be fed into `#processRunner`. Consumer must invoke `run` or `close` on the task. New invocations
   * of this method will have no effect until previous task completes either of its `run` or `closed` methods
   *
   * @param processRunner receiver for the task that must be executed by consumer (in any thread).
   * @return true if current thread won the competition and started processing
   */
  fun tryStartProcess(processRunner: Consumer<AutoclosableProgressive>): Boolean {
    if (!shouldContinueBackgroundProcessing.compareAndSet(false, true)) {
      return false // the thread that set shouldContinueBackgroundProcessing (not this thread) should compete with the background thread
    }
    if (runState.value == RunState.RUNNING) {
      return false // there will be at least one more check of shouldContinueBackgroundProcessing in the background thread
    }
    else {
      val stoppedToStarting = runState.compareAndSet(RunState.STOPPED, RunState.STARTING)
      val thisThreadShouldProcessQueue = stoppedToStarting || runState.compareAndSet(RunState.STOPPING, RunState.STARTING)
      // whatever thread (this or background) wins the competition and sets STARTING - that thread should process the queue
      if (stoppedToStarting) {
        modificationCount.update { it + 1 }
      } else if (!thisThreadShouldProcessQueue) {
        return false
      }
    }
    processRunner.accept(StateAwareTask(task))
    return true
  }

  fun clearScheduledFlag() {
    shouldContinueBackgroundProcessing.set(false)
  }

  // At the moment there is no better way to map StateFlow to StateFlow
  // see https://github.com/Kotlin/kotlinx.coroutines/issues/2631
  private class IsRunningStateFlow(private val runState: StateFlow<RunState>) : StateFlow<Boolean> {
    private fun converter(state: RunState) = (state != RunState.STOPPED)
    override val replayCache: List<Boolean> = listOf(value)
    override val value: Boolean get() = converter(runState.value)
    override suspend fun collect(collector: FlowCollector<Boolean>): Nothing {
      coroutineScope { runState.map(::converter).stateIn(this).collect(collector) }
    }
  }

  val isRunning: StateFlow<Boolean> = IsRunningStateFlow(runState)
  val modificationTrackerAsFlow: StateFlow<Long> = modificationCount

  companion object {
    private val LOG = Logger.getInstance(SingleTaskExecutor::class.java)
  }
}