// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

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

  fun startPtyProcess(
    command: RawCommandLineString,
    directory: String?,
    env: Map<String, String>,
    options: LocalPtyOptions,
    redirectErrorStream: Boolean,
  ): Process

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

  fun hasControllingTerminal(process: Process): Boolean

  /**
   * @return the command line of the process
   */
  fun getCommand(process: Process): List<String> = listOf()

  companion object {
    @JvmStatic
    fun getInstance(): LocalProcessService = service<LocalProcessService>()
  }
}

@ApiStatus.Internal
data class RawCommandLineString(val commandLine: String)
