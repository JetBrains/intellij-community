// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import java.time.Duration

@ApiStatus.Experimental
object WindowsFileUtil {
  private val LOG = Logger.getInstance(javaClass)

  /** @return the process handle; the caller owns it when [waitForProcess] is `null` */
  fun windowsCreateProcess(
    executable: Path,
    workingDirectory: Path,
    parameters: List<String>,
    environment: Map<String, String> = emptyMap(),
    waitForProcess: Duration? = null
  ) : Long {
    val commandLine = ParametersListUtil.join(listOf(executable.toString()) + parameters)

    val environmentBlock = run {
      if (environment.isEmpty()) return@run null

      // not passing nullptr in lpEnvironment will wipe the environment of the created process (no inheritance)
      val fullEnvironment = System.getenv().toMutableMap().toSortedMap()
      fullEnvironment.putAll(environment)

      // A=1\0B=1\0\0
      val environmentBlockBuilder = StringBuilder()
      fullEnvironment
        .forEach { (key, value) -> environmentBlockBuilder.append("$key=$value${Char.MIN_VALUE}") }
      environmentBlockBuilder.append(Char.MIN_VALUE).toString()
    }

    val envString = environmentBlock?.let { "System.getenv()+{${environment.map { "${it.key}=${it.value}" }.joinToString()}}, "}

    val createProcessDebugParams = "CreateProcessW(" +
                                   "lpApplicationName=null, " +
                                   "lpCommandLine='$commandLine', " +
                                   "lpProcessAttributes=null, " +
                                   "lpThreadAttributes=null, " +
                                   "bInheritHandles=false, " +
                                   "dwCreationFlags=CREATE_UNICODE_PROCESS_ENVIRONMENT, " +
                                   "lpEnvironment=$envString" +
                                   "lpCurrentDirectory='$workingDirectory', " +
                                   "lpStartupInfo=si, " +
                                   "lpProcessInformation=pi)"

    LOG.info("Calling $createProcessDebugParams")

    val hProcess = try {
      WindowsProcesses.createProcess(commandLine, environmentBlock, workingDirectory.toString(), WindowsProcesses.SW_NORMAL)
    }
    catch (e: IOException) {
      throw IOException("$createProcessDebugParams returned ${e.message}", e)
    }

    /*
     * known reasons for a null hProcess:
     *   1) CreateProcess didn't result in creation of a new process
     *   2) lpCommandLine first / lpWorkingDir arg is a symlink and was not resolved
     */
    require(hProcess != 0L) {
      "hProcess should not be null in our case"
    }

    if (waitForProcess != null) {
      val waitRc = WindowsProcesses.waitForSingleObject(hProcess, WindowsProcesses.INFINITE)
      if (waitRc == WindowsProcesses.WAIT_TIMEOUT) {
        throw IOException("$createProcessDebugParams: timeout waiting for process to exit")
      }

      val exitCode = WindowsProcesses.getExitCodeProcess(hProcess)
      WindowsProcesses.closeHandle(hProcess)

      if (exitCode == null) {
        throw IOException("$createProcessDebugParams: could not read exit code")
      }

      if (exitCode != 0) {
        throw IOException("$createProcessDebugParams: non-zero exit code: $exitCode")
      }
    }

    return hProcess
  }

  fun createJunction(junctionFile: Path, targetFile: Path) {
    if (!SystemInfo.isWindows) {
      throw UnsupportedOperationException("Requires Windows OS")
    }

    runCommand("cmd", "/C", "mklink", "/J", junctionFile.toString(), targetFile.toString())
  }

  private fun runCommand(vararg command: String) {
    val cmd = GeneralCommandLine(*command).withRedirectErrorStream(true)
    val timeoutMs = 30000
    val output = ExecUtil.execAndGetOutput(cmd, timeoutMs)
    when {
      output.exitCode != 0 ->
        throw IOException("Could not create a windows junction with mklink: exit code ${output.exitCode}; mklink output: ${output.stdout.trim()}")
      output.isTimeout ->
        throw IllegalStateException("Failed to create junction in $timeoutMs ms, cmd: '$cmd'")
    }
  }
}