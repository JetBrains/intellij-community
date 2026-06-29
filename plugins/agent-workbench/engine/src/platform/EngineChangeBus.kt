// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.ThreadId
import org.jetbrains.annotations.ApiStatus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

internal data class EngineChange(
  @JvmField val projectPath: String,
  val threadId: ThreadId,
)

/**
 * Application-level signal that some project's Engine event store changed. Lets adapters (e.g. the
 * legacy session-provider that surfaces Engine threads in the existing tool window) trigger a refresh
 * without each consumer wiring into per-project message buses.
 */
internal object EngineChangeBus {
  private val _changes = MutableSharedFlow<EngineChange>(
    extraBufferCapacity = 256,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /** Emits the thread id whose log just changed. */
  val changes: SharedFlow<EngineChange> = _changes

  fun fireChanged(projectPath: String, threadId: ThreadId) {
    _changes.tryEmit(EngineChange(projectPath = projectPath, threadId = threadId))
  }
}

@ApiStatus.Internal
fun fireEngineThreadPresentationChanged(projectPath: String, threadId: ThreadId) {
  EngineChangeBus.fireChanged(projectPath = projectPath, threadId = threadId)
}
