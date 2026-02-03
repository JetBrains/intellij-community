// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.EdtScheduler
import com.intellij.xdebugger.XDebugProcessDebuggeeInForeground
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<XDebugProcessDebuggeeInForeground>()

@ApiStatus.Internal
fun XDebugProcessDebuggeeInForeground.start(session: XDebugSession, bringAfterMs: Int = 1_000) {
  var job: Job? = null

  val support = this
  session.addSessionListener(object : XDebugSessionListener {
    override fun sessionResumed() {
      job?.cancel()

      if (!isEnabled())
        return

      job = EdtScheduler.getInstance().schedule(bringAfterMs) {
          LOG.trace { "Bringing debuggee into foreground" }
          support.bringToForeground()
        }
    }

    override fun sessionPaused() {
      job?.cancel()
    }

    override fun sessionStopped() {
      job?.cancel()
    }
  })
}

private fun isEnabled() = Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume.supported").asBoolean() &&
                          Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume").asBoolean()
