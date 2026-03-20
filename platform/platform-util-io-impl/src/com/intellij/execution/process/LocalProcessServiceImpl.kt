// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SystemProperties
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.pty4j.Command
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
  ): PtyProcess = startPtyProcess(Command.CommandList(command), directory, env, options, redirectErrorStream)

  override fun startPtyProcess(
    command: RawCommandLineString,
    directory: String?,
    env: Map<String, String>,
    options: LocalPtyOptions,
    redirectErrorStream: Boolean,
  ): PtyProcess = startPtyProcess(Command.RawCommandString(command.commandLine), directory, env, options, redirectErrorStream)

  @OptIn(LowLevelLocalMachineAccess::class)
  private fun startPtyProcess(
    command: Command,
    directory: String?,
    env: Map<String, String>,
    options: LocalPtyOptions,
    redirectErrorStream: Boolean,
  ): PtyProcess {
    val builder = PtyProcessBuilder(command)
      .setEnvironment(env)
      .setDirectory(directory)
      .setInitialColumns(if (options.initialColumns > 0) options.initialColumns else null)
      .setInitialRows(if (options.initialRows > 0) options.initialRows else null)
      .setConsole(options.consoleMode)
      .setCygwin(options.useCygwinLaunch && OS.CURRENT == OS.Windows)
      .setUseWinConPty(options.useWinConPty)
      .setRedirectErrorStream(redirectErrorStream)
      .setWindowsAnsiColorEnabled(!SystemProperties.getBooleanProperty("pty4j.win.disable.ansi.in.console.mode", false))
      .setUnixOpenTtyToPreserveOutputAfterTermination(SystemProperties.getBooleanProperty("pty4j.open.child.tty", OS.CURRENT == OS.macOS))
      .setSpawnProcessUsingJdkOnMacIntel(Registry.`is`("run.processes.using.pty.helper.on.mac.intel", true))
      .setConPtyInheritCursor(options.winConPtyInheritCursor)

    if (options.winSuspendedProcessCallback != null) {
      builder.setWindowsSuspendedProcessCallback(options.winSuspendedProcessCallback!!)
    }

    val process = builder.start()

    if (options.winSuspendedProcessCallback != null && process !is WinConPtyProcess) {
      logger<LocalProcessServiceImpl>().error("Windows suspended process callback is only applicable for a WinConPtyProcess instance")
    }

    return process
  }

  override fun sendWinProcessCtrlC(process: Process): Boolean = sendWinProcessCtrlC(process.pid().toInt(), process.outputStream)

  override fun sendWinProcessCtrlC(pid: Int): Boolean = sendWinProcessCtrlC(pid, null)

  override fun sendWinProcessCtrlC(pid: Int, processOutputStream: OutputStream?): Boolean {
    val r = WinProcess(pid).sendCtrlC()
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
    catch (_: Exception) { }
    return r
  }

  override fun killWinProcessRecursively(process: Process) {
    WinProcess(process.pid().toInt()).killRecursively()
  }

  override fun isLocalPtyProcess(process: Process): Boolean = process is PtyProcess

  override fun hasControllingTerminal(process: Process): Boolean = process is PtyProcess && !process.isConsoleMode

  override fun getCommand(process: Process): List<String> {
    return when (process) {
      is WinConPtyProcess -> process.commandWrapper.toList()
      is WinPtyProcess -> process.commandWrapper.toList()
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
        listOf()
      }
    }
  }
}
