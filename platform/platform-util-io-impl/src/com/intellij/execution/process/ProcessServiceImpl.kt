// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.pty4j.PtyProcessBuilder
import com.pty4j.windows.WinPtyProcess
import org.jvnet.winp.WinProcess
import java.io.File

class ProcessServiceImpl: ProcessService {
  override fun startPtyProcess(command: Array<String>,
                               directory: String?,
                               env: MutableMap<String, String>,
                               options: PtyCommandLineOptions,
                               app: Application?,
                               redirectErrorStream: Boolean,
                               windowsAnsiColorEnabled: Boolean,
                               unixOpenTtyToPreserveOutputAfterTermination: Boolean): Process {
    val builder = PtyProcessBuilder(command)
      .setEnvironment(env)
      .setDirectory(directory)
      .setInitialColumns(if (options.initialColumns > 0) options.initialColumns else null)
      .setInitialRows(if (options.initialRows > 0) options.initialRows else null)
      .setConsole(options.consoleMode)
      .setCygwin(options.useCygwinLaunch && SystemInfo.isWindows)
      .setLogFile(if (app != null && app.isEAP) File(PathManager.getLogPath(), "pty.log") else null)
      .setRedirectErrorStream(redirectErrorStream)
      .setWindowsAnsiColorEnabled(windowsAnsiColorEnabled)
      .setUnixOpenTtyToPreserveOutputAfterTermination(unixOpenTtyToPreserveOutputAfterTermination)
    return builder.start()
  }

  override fun sendWinProcessCtrlC(process: Process): Boolean {
    return createWinProcess(process).sendCtrlC()
  }

  override fun sendWinProcessCtrlC(pid: Int): Boolean {
    return createWinProcess(pid).sendCtrlC()
  }

  override fun killWinProcessRecursively(process: Process) {
    createWinProcess(process).killRecursively();
  }

  override fun isWinPty(process: Process): Boolean {
    return process is WinPtyProcess;
  }

  override fun winPtyChildProcessId(process: Process): Int? {
    return if (process is WinPtyProcess) {
      return process.childProcessId
    } else {
      null
    }
  }

  private fun createWinProcess(process: Process): WinProcess {
    return if (process is WinPtyProcess) {
      WinProcess(process.pid)
    }
    else WinProcess(process)
  }

  private fun createWinProcess(pid: Int) = WinProcess(pid)

  override fun killWinProcess(pid: Int) {
    createWinProcess(pid).kill();
  }
}
