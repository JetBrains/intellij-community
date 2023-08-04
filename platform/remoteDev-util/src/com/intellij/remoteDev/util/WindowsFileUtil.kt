package com.intellij.remoteDev.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.execution.ParametersListUtil
import com.sun.jna.Memory
import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.IntByReference
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.IllegalStateException
import java.nio.file.Path
import java.time.Duration

@ApiStatus.Experimental
object WindowsFileUtil {
  private val LOG = Logger.getInstance(javaClass)

  fun windowsCreateProcess(
    executable: Path,
    workingDirectory: Path,
    parameters: List<String>,
    environment: Map<String, String> = emptyMap(),
    waitForProcess: Duration? = null
  ) : WinNT.HANDLE {

    val si = WinBase.STARTUPINFO()
    val pi = WinBase.PROCESS_INFORMATION()

    val commandLine = ParametersListUtil.join(listOf(executable.toString()) + parameters)

    // A=1\0B=1\0\0
    val environmentValue = environment
      .map { "${it.key}=${it.value}" }.sorted()
      .joinToString(separator = "${Char.MIN_VALUE}", postfix = "${Char.MIN_VALUE}${Char.MIN_VALUE}")

    val createProcessDebugParams = "CreateProcess(" +
                                   "lpApplicationName=null, " +
                                   "lpCommandLine='$commandLine', " +
                                   "lpProcessAttributes=null, " +
                                   "lpThreadAttributes=null, " +
                                   "bInheritHandles=true, " +
                                   "dwCreationFlags=0, " +
                                   "lpEnvironment=$environmentValue, " +
                                   "lpCurrentDirectory='$workingDirectory', " +
                                   "lpStartupInfo=si, " +
                                   "lpProcessInformation=pi)"

    LOG.info("Calling $createProcessDebugParams")

    val environmentBytes = environmentValue.toByteArray(Charsets.UTF_16LE)
    val environmentBlock = Memory(environmentBytes.size.toLong())
    environmentBlock.write(0, environmentBytes, 0, environmentBytes.size)

    if (!Kernel32.INSTANCE.CreateProcess(
        /* lpApplicationName    = */ null,
        /* lpCommandLine        = */ commandLine,
        /* lpProcessAttributes  = */ null,
        /* lpThreadAttributes   = */ null,
        /* bInheritHandles      = */ true,
        /* dwCreationFlags      = */ WinDef.DWORD(0),
        /* lpEnvironment        = */ environmentBlock,
        /* lpCurrentDirectory   = */ workingDirectory.toString(),
        /* lpStartupInfo        = */ si,
        /* lpProcessInformation = */ pi)) {
      throw IOException("$createProcessDebugParams returned error: " + Kernel32.INSTANCE.GetLastError())
    }

    if (waitForProcess != null) {
      val exitCode = IntByReference(WinBase.INFINITE)

      val waitRc = Kernel32.INSTANCE.WaitForSingleObject(pi.hProcess, WinBase.INFINITE)
      if (waitRc == WinError.WAIT_TIMEOUT) {
        throw IOException("$createProcessDebugParams: timeout waiting for process to exit")
      }

      Kernel32.INSTANCE.GetExitCodeProcess(pi.hProcess, exitCode)
      Kernel32.INSTANCE.CloseHandle(pi.hProcess)

      if (exitCode.value == WinBase.INFINITE) {
        throw IOException("$createProcessDebugParams: could not read exit code")
      }

      if (exitCode.value != 0) {
        throw IOException("$createProcessDebugParams: non-zero exit code: ${exitCode.value}")
      }
    }

    return pi.hProcess
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