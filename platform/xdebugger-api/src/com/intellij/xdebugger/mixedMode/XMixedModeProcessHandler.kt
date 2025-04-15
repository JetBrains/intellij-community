// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.execution.process.CompositeProcessHandler
import com.intellij.execution.process.ProcessHandler
import org.jetbrains.annotations.ApiStatus

/**
 * Responsible for correctly starting and terminating a process in mixed mode
 */
@ApiStatus.Internal
class XMixedModeProcessHandler(
  highLevelProcessHandler: ProcessHandler,
  lowLevelProcessHandler: ProcessHandler,
  private val config: XMixedModeProcessesConfiguration,
) : CompositeProcessHandler(listOf(highLevelProcessHandler, lowLevelProcessHandler)) {

  private val high get() = handlers[0]
  private val low get() = handlers[1]

  override fun startNotifyHandler(handler: ProcessHandler) {
    if (handler.isStartNotified) return
    handler.startNotify()
  }

  // If the processes can't agree on the detach behavior, we will let the one that wants to detach do that first,
  // and the other will destroy the process (see destroyProcessImpl method that's called when detachIsDefault() returns false)
  override fun detachIsDefault(): Boolean = high.detachIsDefault() && low.detachIsDefault()

  // if this method was called, at least one of the processes wants to destroy the process
  override fun destroyProcessImpl() {
    val bothWantDestroy = !low.detachIsDefault() && !high.detachIsDefault()
    if (bothWantDestroy) {
      super.destroyProcessImpl()
      return
    }

    // First, let's detach if any process handler wants it and do destroy afterward
    val (handlerWantsDetach, handlerWantsDestroy) = if (low.detachIsDefault()) Pair(low, high) else Pair(high, low)
    handlerWantsDetach.detachProcess()
    handlerWantsDestroy.destroyProcess()
  }
}