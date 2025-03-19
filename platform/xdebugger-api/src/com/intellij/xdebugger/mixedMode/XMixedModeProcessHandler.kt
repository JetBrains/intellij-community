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

  override fun detachIsDefault(): Boolean = (if (config.useLowDebugProcessDetachBehavior) low else high).detachIsDefault()
}