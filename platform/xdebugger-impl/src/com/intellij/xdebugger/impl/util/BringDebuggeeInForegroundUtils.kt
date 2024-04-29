// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.execution.process.window.to.foreground.BringProcessWindowToForegroundSupport
import com.intellij.execution.process.window.to.foreground.BringProcessWindowToForegroundSupportApplicable
import com.intellij.execution.process.window.to.foreground.tryBringTerminalWindow
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.xdebugger.BringDebuggeeInForegroundSupport
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val logger: Logger = Logger.getInstance(BringDebuggeeInForegroundSupport::class.java)

fun BringDebuggeeInForegroundSupport.start(session: XDebugSession, bringAfterMs: Long = 1000) {
  if (!isEnabled())
    return

  val bringProcessWindowSupport = BringProcessWindowToForegroundSupport.getInstance()
  if (!bringProcessWindowSupport.isApplicable())
    return

  var executor: ScheduledFuture<*>? = null

  val support = this
  val dataHolder = UserDataHolderBase()
  session.addSessionListener(object : XDebugSessionListener {
    override fun sessionResumed() {
      executor?.cancel(false)
      executor = EdtScheduledExecutorService.getInstance()
        .schedule({
                    logger.trace { "Bringing debuggee into foreground: ${support.getPid()}" }
                    support.bring(bringProcessWindowSupport, dataHolder)
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

private fun BringDebuggeeInForegroundSupport.bring(bringProcessWindowSupport: BringProcessWindowToForegroundSupport,
                                                   dataHolder: UserDataHolderBase) {
  val pid = this.getPid()
  if (bringProcessWindowSupport.bring(pid)) {
    logger.trace { "Could successfully bring $pid process into foreground" }
    return
  }

  logger.trace { "Bringing terminal window into foreground if it exists" }

  bringProcessWindowSupport.tryBringTerminalWindow(dataHolder, pid)
    .also { logger.trace { "Bringing cmd process to foreground : ${if (it) "succeeded" else "failed"}" } }
}

private fun isEnabled() = Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume.supported").asBoolean() ||
                          Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume").asBoolean()

private fun BringProcessWindowToForegroundSupport.isApplicable() =
  ((this as? BringProcessWindowToForegroundSupportApplicable)?.isApplicable() ?: true)