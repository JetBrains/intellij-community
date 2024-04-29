// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.execution.process.window.to.foreground.BringProcessWindowToForegroundSupport
import com.intellij.execution.process.window.to.foreground.BringProcessWindowToForegroundSupportApplicable
import com.intellij.execution.process.window.to.foreground.tryBringTerminalWindow
import com.intellij.internal.statistic.eventLog.getUiEventLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebugProcessDebuggeeInForegroundSupport
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class BringDebuggeeIntoForegroundImpl(val support: BringProcessWindowToForegroundSupport) {
  companion object {
    fun start(logger: Logger, session: XDebugSession, bringAfterMs: Long = 1000) {
      if (!isEnabled())
        return

      val bringProcessWindowSupport = BringProcessWindowToForegroundSupport.getInstance()
      if (!isApplicable(session, bringProcessWindowSupport))
        return

      val debuggerSupport = requireNotNull(getDebuggerSupport(session)) { "XDebugProcess should support XDebugProcessDebuggeeInForegroundSupport" }
      var executor: ScheduledFuture<*>? = null

      val bringDebuggeeIntoForegroundImpl = BringDebuggeeIntoForegroundImpl(bringProcessWindowSupport)

      session.addSessionListener(object : XDebugSessionListener {
        override fun sessionResumed() {
          executor?.cancel(false)
          executor = EdtScheduledExecutorService.getInstance()
            .schedule({
                        val pid = debuggerSupport.getPid()
                                  ?: run { logger.warn("Skip bringing debuggee into foreground since pid is null"); return@schedule }

                        logger.trace { "Bringing debuggee into foreground: $pid" }
                        bringDebuggeeIntoForegroundImpl.bring(logger, pid)
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

    private fun isEnabled() = Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume.supported").asBoolean() ||
                              Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume").asBoolean()

    private fun getDebuggerSupport(session: XDebugSession) = session.debugProcess as? XDebugProcessDebuggeeInForegroundSupport

    private fun isApplicable(session: XDebugSession, support: BringProcessWindowToForegroundSupport) =
      getDebuggerSupport(session) != null &&
      when (support) {
        is BringProcessWindowToForegroundSupportApplicable -> support.isApplicable()
        else -> true
      }
  }

  val dataHolder = UserDataHolderBase()

  fun bring(logger: Logger, pid: Int) {
    if (support.bring(pid)) {
      logger.trace { "Could successfully bring $pid process into foreground" }
      return
    }

    logger.trace { "Bringing terminal window into foreground if it exists" }

    support.tryBringTerminalWindow(dataHolder, pid).also { logger.trace { "Bringing cmd process to foreground : ${if (it) "succeeded" else "failed"}" } }
  }
}