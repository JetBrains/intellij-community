// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.ThreadId
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Application-level signal that some project's Engine event store changed. Lets adapters (e.g. the
 * legacy session-provider that surfaces Engine threads in the existing tool window) trigger a refresh
 * without each consumer wiring into per-project message buses.
 */
object EngineChangeBus {
  private val _changes = MutableSharedFlow<ThreadId>(
    extraBufferCapacity = 256,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /** Emits the thread id whose log just changed. */
  val changes: SharedFlow<ThreadId> = _changes

  fun fireChanged(threadId: ThreadId) {
    _changes.tryEmit(threadId)
  }
}
