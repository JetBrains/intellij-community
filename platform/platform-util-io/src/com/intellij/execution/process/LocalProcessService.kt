// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import java.io.OutputStream

@ApiStatus.Internal
interface LocalProcessService {
  fun startPtyProcess(
    command: List<String>,
    directory: String?,
    env: Map<String, String>,
    options: LocalPtyOptions,
    redirectErrorStream: Boolean,
  ): Process

  @Deprecated("Use the other `startPtyProcess` instead")
  fun startPtyProcess(
    command: Array<String>,
    directory: String?,
    env: Map<String, String>,
    options: LocalPtyOptions,
    app: Application?,
    redirectErrorStream: Boolean,
    windowsAnsiColorEnabled: Boolean,
    unixOpenTtyToPreserveOutputAfterTermination: Boolean
  ): Process {
    return startPtyProcess(command.toList(), directory, env, options, redirectErrorStream)
  }

  fun sendWinProcessCtrlC(process: Process): Boolean

  /**
   * For better CTRL+C emulation a process output stream is needed,
   * just sending CTRL+C event might not be enough. Consider using
   * `sendWinProcessCtrlC(process: Process)` or
   * `sendWinProcessCtrlC(pid: Int, processOutputStream: OutputStream?)` instead.
   */
  fun sendWinProcessCtrlC(pid: Int): Boolean

  fun sendWinProcessCtrlC(pid: Int, processOutputStream: OutputStream?): Boolean

  fun killWinProcessRecursively(process: Process)

  fun isLocalPtyProcess(process: Process): Boolean

  fun winPtyChildProcessId(process: Process): Int?

  fun hasControllingTerminal(process: Process): Boolean

  fun killWinProcess(pid: Int)

  /**
   * @return the command line of the process
   */
  fun getCommand(process: Process): List<String> = listOf<String>()

  companion object {
    @JvmStatic
    fun getInstance(): LocalProcessService = service<LocalProcessService>()
  }
}
