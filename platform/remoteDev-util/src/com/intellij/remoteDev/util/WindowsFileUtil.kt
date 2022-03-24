package com.intellij.remoteDev.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.execution.ParametersListUtil
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

  private const val SEE_MASK_NO_CLOSE_PROCESS = 0x00000040

  fun windowsShellExecute(executable: Path, workingDirectory: Path, parameters: List<String>, waitForProcess: Duration? = null) : WinNT.HANDLE {
    val info = ShellAPI.SHELLEXECUTEINFO()
    info.cbSize = info.size()
    info.lpFile = executable.toString()
    info.lpVerb = "open"
    info.lpParameters = ParametersListUtil.join(parameters)
    info.lpDirectory = workingDirectory.toString()
    info.nShow = WinUser.SW_NORMAL
    info.fMask = SEE_MASK_NO_CLOSE_PROCESS

    val shellExecuteDebugParams = "ShellExecuteEx(" +
                                  "lpFile='${info.lpFile}', " +
                                  "lpVerb='${info.lpVerb}', " +
                                  "lpParameters='${info.lpParameters}', " +
                                  "lpDirectory='${info.lpDirectory}', " +
                                  "nShow='${info.nShow}', " +
                                  "fMask='0x${Integer.toHexString(info.fMask)}')"

    LOG.info("Calling $shellExecuteDebugParams")

    if (!Shell32.INSTANCE.ShellExecuteEx(info)) {
      throw IOException("$shellExecuteDebugParams returned 0x" + Integer.toHexString(Kernel32.INSTANCE.GetLastError()))
    }

    /*
     * known reasons for a null hProcess:
     *   1) ShellExecuteEx didn't result in creation of a new process
     *   2) lpFile is a symlink and was not resolved
     */
    require(info.hProcess != null) {
      "hProcess should not be null in our case"
    }

    if (waitForProcess != null) {
      val exitCode = IntByReference(WinBase.INFINITE)

      val waitRc = Kernel32.INSTANCE.WaitForSingleObject(info.hProcess, WinBase.INFINITE)
      if (waitRc == WinError.WAIT_TIMEOUT) {
        throw IOException("$shellExecuteDebugParams: timeout waiting for process to exit")
      }

      Kernel32.INSTANCE.GetExitCodeProcess(info.hProcess, exitCode)
      Kernel32.INSTANCE.CloseHandle(info.hProcess)

      if (exitCode.value == WinBase.INFINITE) {
        throw IOException("$shellExecuteDebugParams: could not read exit code")
      }

      if (exitCode.value != 0) {
        throw IOException("$shellExecuteDebugParams: non-zero exit code: ${exitCode.value}")
      }
    }

    return info.hProcess
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