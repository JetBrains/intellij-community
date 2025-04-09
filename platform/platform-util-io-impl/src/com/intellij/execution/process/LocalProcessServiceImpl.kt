// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SystemProperties
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.windows.conpty.WinConPtyProcess
import com.pty4j.windows.winpty.WinPtyProcess
import org.jetbrains.annotations.ApiStatus
import org.jvnet.winp.WinProcess
import java.io.OutputStream

@ApiStatus.Internal
class LocalProcessServiceImpl : LocalProcessService {
  override fun startPtyProcess(
    command: List<String>,
    directory: String?,
    env: Map<String, String>,
    options: LocalPtyOptions,
    redirectErrorStream: Boolean,
  ): PtyProcess {
    val builder = PtyProcessBuilder(command.toTypedArray())
      .setEnvironment(env)
      .setDirectory(directory)
      .setInitialColumns(if (options.initialColumns > 0) options.initialColumns else null)
      .setInitialRows(if (options.initialRows > 0) options.initialRows else null)
      .setConsole(options.consoleMode)
      .setCygwin(options.useCygwinLaunch && SystemInfo.isWindows)
      .setUseWinConPty(options.useWinConPty)
      .setRedirectErrorStream(redirectErrorStream)
      .setWindowsAnsiColorEnabled(!SystemProperties.getBooleanProperty("pty4j.win.disable.ansi.in.console.mode", false))
      .setUnixOpenTtyToPreserveOutputAfterTermination(SystemProperties.getBooleanProperty("pty4j.open.child.tty", SystemInfo.isMac))
      .setSpawnProcessUsingJdkOnMacIntel(Registry.`is`("run.processes.using.pty.helper.on.mac.intel", true))
    return builder.start()
  }

  override fun sendWinProcessCtrlC(process: Process): Boolean {
    return sendWinProcessCtrlC(process.pid().toInt(), process.outputStream)
  }

  override fun sendWinProcessCtrlC(pid: Int): Boolean {
    return sendWinProcessCtrlC(pid, null)
  }

  override fun sendWinProcessCtrlC(pid: Int, processOutputStream: OutputStream?): Boolean {
    val r = createWinProcess(pid).sendCtrlC()
    try {
      processOutputStream?.apply {
        // CTRL-C on Windows sends "-1" to the stdin
        // It unblocks ReadConsoleW/ReadFile
        // Sending CTRL+C with GenerateConsoleCtrlEvent is not enough, because it doesn't unblock ReadConsoleW
        // There is no such problem on **nix because of siginterrupt
        // See PY-50064
        write(-1)
        flush()
      }
    }
    catch (_: Exception) {
    }
    return r
  }

  override fun killWinProcessRecursively(process: Process) {
    createWinProcess(process).killRecursively()
  }

  override fun isLocalPtyProcess(process: Process): Boolean {
    return process is PtyProcess
  }

  override fun winPtyChildProcessId(process: Process): Int? {
    return if (process is WinPtyProcess) {
      return process.pid().toInt()
    } else {
      null
    }
  }

  override fun hasControllingTerminal(process: Process): Boolean = process is PtyProcess && !process.isConsoleMode

  private fun createWinProcess(process: Process): WinProcess {
    return WinProcess(process.pid().toInt())
  }

  private fun createWinProcess(pid: Int) = WinProcess(pid)

  override fun killWinProcess(pid: Int) {
    createWinProcess(pid).kill()
  }

  override fun getCommand(process: Process): List<String> {
    return when (process) {
      is WinConPtyProcess -> process.command
      is WinPtyProcess -> process.command
      else -> {
        val processInfo: ProcessHandle.Info = try {
          process.info()
        }
        catch (e: UnsupportedOperationException) {
          logger<LocalProcessServiceImpl>().debug("Cannot find command line for ${process.javaClass}", e)
          return listOf()
        }
        processInfo.command().orElse(null)?.let {
          return listOf(it, *processInfo.arguments().orElse(emptyArray()))
        }
        return listOf()
      }
    }
  }
}
