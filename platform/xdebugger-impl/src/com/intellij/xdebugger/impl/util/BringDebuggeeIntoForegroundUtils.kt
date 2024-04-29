// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.execution.process.window.to.foreground.BringProcessWindowToForegroundSupport
import com.intellij.execution.process.window.to.foreground.BringProcessWindowToForegroundSupportApplicable
import com.intellij.execution.process.window.to.foreground.WinBringProcessWindowToForegroundSupport
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
import kotlin.jvm.optionals.getOrNull

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

    private val TerminalPIDKey = Key<Int?>("TerminalPIDKey")
    private val TerminalBroughtSuccessfullyKey = Key<Boolean>("TerminalBroughtSuccessfullyKey")
  }

  val dataHolder = UserDataHolderBase()

  fun bring(logger: Logger, pid: Int) {
    if (support.bring(pid)) {
      logger.trace { "Could successfully bring $pid process into foreground" }
      return
    }

    logger.trace { "Bringing terminal window into foreground if it exists" }

    tryBringTerminalWindow(logger, pid).also { logger.trace { "Bringing cmd process to foreground : ${if (it) "succeeded" else "failed"}" } }
  }

  private fun tryBringTerminalWindow(logger: Logger, pid: Int): Boolean {
    if (dataHolder.getUserData(TerminalBroughtSuccessfullyKey) == false)
      return false

    val result = if (SystemInfo.isWindows)
      // on windows WindowsTerminal.exe process is not a parent of the debuggee, so we have to find the terminal windows associated with the debuggee first
      return tryBringWindowsTerminalInForeground(logger, pid)
    else
      when (val terminalPid = dataHolder.getOrCreateUserData(TerminalPIDKey) {
        (tryFindParentProcess(logger, pid, listOf("MacOS/Terminal", "gnome-terminal")) ?: run {
          logger.trace { "Could find neither main window of $pid process, nor parent cmd process. Exiting" };
          return@getOrCreateUserData null
        }
        ).pid().toInt()
      }) {
        null -> false
        else -> support.bring(terminalPid)
      }

    return result.also { dataHolder.putUserDataIfAbsent(TerminalBroughtSuccessfullyKey, it) }
  }

  private fun tryFindParentProcess(logger: Logger, pid: Int, parentProcessesWeLookingFor: List<String>): ProcessHandle? {
    val debuggeeProcess = ProcessHandle.allProcesses().filter { it.pid() == pid.toLong() }.findFirst().getOrNull()
                          ?: run { logger.trace { "Can't find the process with pid $pid" }; return null }

    val ideProcess = ProcessHandle.current()

    var parentProcess = debuggeeProcess.parent().getOrNull()

    while (parentProcess != null && parentProcess != ideProcess) {
      val command = parentProcess.info().command().getOrNull()
      if (command != null && parentProcessesWeLookingFor.any { command.contains(it) })
        return parentProcess

      parentProcess = parentProcess.parent().getOrNull()
    }

    return null
  }

  private fun tryBringWindowsTerminalInForeground(logger: Logger, pid: Int): Boolean {
    if (tryFindParentProcess(logger, pid, listOf("cmd.exe")) == null) {
      logger.trace { "The process hasn't been launched under cmd.exe" }
      return false
    }

    // On windows only 1 instance of terminal can be launched
    val windowsTerminalPid = dataHolder.getOrCreateUserData(TerminalPIDKey) {
      ProcessHandle.allProcesses()
        .filter {
          val command = it.info().command().getOrNull() ?: return@filter false
          command.contains("Program Files\\WindowsApps\\Microsoft.WindowsTerminal") && command.endsWith("WindowsTerminal.exe")
        }
        .findFirst()
        .getOrNull()
        ?.pid()
        ?.toInt()
    } ?: return false

    // if there are more than 1 Debugger.Worker.exe window, we will bring none of them
    return (support as WinBringProcessWindowToForegroundSupport)
      .bringWindowWithName(windowsTerminalPid, dataHolder, "Debugger.Worker.exe")
  }
}