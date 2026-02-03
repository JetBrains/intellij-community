package com.intellij.remoteDev.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.execution.ParametersListUtil
import com.sun.jna.Memory
import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
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

    val si = WinBase.STARTUPINFO().apply {
      dwFlags = WinBase.STARTF_USESHOWWINDOW
      wShowWindow = WinDef.WORD(WinUser.SW_NORMAL.toLong())
    }
    val pi = WinBase.PROCESS_INFORMATION()

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
      val environmentBlockStr = environmentBlockBuilder.append(Char.MIN_VALUE).toString()

      val environmentBytes = environmentBlockStr.toByteArray(Charsets.UTF_16LE)
      val environmentBlock = Memory(environmentBytes.size.toLong())
      environmentBlock.write(0, environmentBytes, 0, environmentBytes.size)

      environmentBlock
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

    @Suppress("LocalVariableName")
    val CREATE_UNICODE_PROCESS_ENVIRONMENT = WinDef.DWORD(0x00000400)

    if (!Kernel32.INSTANCE.CreateProcessW(
        /* lpApplicationName    = */ null,
        /* lpCommandLine        = */ (commandLine + "\u0000").toCharArray(),
        /* lpProcessAttributes  = */ null,
        /* lpThreadAttributes   = */ null,
        /* bInheritHandles      = */ false,
        /* dwCreationFlags      = */ CREATE_UNICODE_PROCESS_ENVIRONMENT,
        /* lpEnvironment        = */ environmentBlock,
        /* lpCurrentDirectory   = */ workingDirectory.toString() + "\u0000",
        /* lpStartupInfo        = */ si,
        /* lpProcessInformation = */ pi)) {

      val lastError = Kernel32.INSTANCE.GetLastError()

      val lpBuffer = PointerByReference()
      val result = Kernel32.INSTANCE.FormatMessage(
        Kernel32.FORMAT_MESSAGE_ALLOCATE_BUFFER or Kernel32.FORMAT_MESSAGE_FROM_SYSTEM or Kernel32.FORMAT_MESSAGE_IGNORE_INSERTS,
        null,
        lastError,
        Kernel32.LANG_USER_DEFAULT,
        lpBuffer,
        0,
        null
      )

      val buffer = lpBuffer.value
      val errorTextBuffer = buffer.getCharArray(0, result)
      Kernel32.INSTANCE.LocalFree(buffer)

      val message = String(errorTextBuffer)
      throw IOException("$createProcessDebugParams returned error $lastError: $message")
    }


    /*
     * known reasons for a null hProcess:
     *   1) CreateProcess didn't result in creation of a new process
     *   2) lpCommandLine first / lpWorkingDir arg is a symlink and was not resolved
     */
    require(pi.hProcess != null) {
      "hProcess should not be null in our case"
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