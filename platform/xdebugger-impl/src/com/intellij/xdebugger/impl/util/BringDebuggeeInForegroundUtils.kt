// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.xdebugger.XDebugProcessDebuggeeInForeground
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val logger: Logger = Logger.getInstance(XDebugProcessDebuggeeInForeground::class.java)

@ApiStatus.Internal
fun XDebugProcessDebuggeeInForeground.start(session: XDebugSession, bringAfterMs: Long = 1000) {
  if (!isEnabled())
    return

  var executor: ScheduledFuture<*>? = null

  val support = this
  session.addSessionListener(object : XDebugSessionListener {
    override fun sessionResumed() {
      executor?.cancel(false)
      executor = EdtScheduledExecutorService.getInstance()
        .schedule({
                    logger.trace { "Bringing debuggee into foreground" }
                    support.bringToForeground()
                  }, bringAfterMs, TimeUnit.MILLISECONDS)
    }

    override fun sessionPaused() {
      executor?.cancel(false)
    }

    override fun sessionStopped() {
      executor?.cancel(false)
    }
  })
}

private fun isEnabled() = Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume.supported").asBoolean() &&
                          Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume").asBoolean()
