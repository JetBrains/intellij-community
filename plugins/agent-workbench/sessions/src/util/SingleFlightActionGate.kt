// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class SingleFlightPolicy {
  DROP,
  RESTART_LATEST,
  QUEUE,
}

internal data class SingleFlightProgressRequest(
  val project: Project,
  val title: @ProgressTitle String,
  val cancellation: TaskCancellation = TaskCancellation.cancellable(),
  val visibleInStatusBar: Boolean = true,
)

/**
 * Prevents duplicate execution of semantically same actions keyed by action key.
 */
internal class SingleFlightActionGate {
  private val lock = Any()
  private val states = HashMap<String, KeyState>()

  fun isInFlight(key: String): Boolean {
    return synchronized(lock) {
      states[key]?.running == true
    }
  }

  fun launch(
    scope: CoroutineScope,
    key: String,
    policy: SingleFlightPolicy = SingleFlightPolicy.DROP,
    progress: SingleFlightProgressRequest? = null,
    onDrop: (() -> Unit)? = null,
    block: suspend () -> Unit,
  ): Job? {
    val state: KeyState
    synchronized(lock) {
      state = states.getOrPut(key) { KeyState() }
      when (policy) {
        SingleFlightPolicy.DROP -> {
          if (state.running) {
            onDrop?.invoke()
            return null
          }
        }
        SingleFlightPolicy.RESTART_LATEST -> {
          if (state.running) {
            state.latestBlock = block
            return null
          }
        }
        SingleFlightPolicy.QUEUE -> {
          if (state.running) {
            state.queuedBlocks.addLast(block)
            return null
          }
        }
      }

      state.running = true
    }

    return scope.launch {
      runLoop(key, state = state, policy = policy, progress = progress, initialBlock = block)
    }
  }

  private suspend fun runLoop(
    key: String,
    state: KeyState,
    policy: SingleFlightPolicy,
    progress: SingleFlightProgressRequest?,
    initialBlock: suspend () -> Unit,
  ) {
    var nextBlock: (suspend () -> Unit)? = initialBlock
    try {
      while (true) {
        val blockToRun = nextBlock ?: break
        if (progress != null) {
          withBackgroundProgress(
            project = progress.project,
            title = progress.title,
            cancellation = progress.cancellation,
            suspender = null,
            visibleInStatusBar = progress.visibleInStatusBar,
          ) {
            blockToRun.invoke()
          }
        }
        else {
          blockToRun.invoke()
        }
        nextBlock = synchronized(lock) {
          if (states[key] !== state) return@synchronized null
          val pending = when (policy) {
            SingleFlightPolicy.DROP -> null
            SingleFlightPolicy.RESTART_LATEST -> state.latestBlock.also { state.latestBlock = null }
            SingleFlightPolicy.QUEUE -> state.queuedBlocks.removeFirstOrNull()
          }
          if (pending == null) {
            states.remove(key, state)
          }
          pending
        }
      }
    }
    finally {
      synchronized(lock) {
        states.remove(key, state)
      }
    }
  }

  private class KeyState {
    var running: Boolean = false
    var latestBlock: (suspend () -> Unit)? = null
    val queuedBlocks: ArrayDeque<suspend () -> Unit> = ArrayDeque()
  }
}
