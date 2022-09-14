package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import java.util.concurrent.CompletableFuture


internal fun AbstractWslDistribution.createWslCommandLine(vararg commands: String):
  GeneralCommandLine = patchCommandLine(GeneralCommandLine(*commands), null, WSLCommandLineOptions())

/**
 * Waits for process to complete and returns stdout as String. Uses [waitProcess] under the hood
 */
fun AbstractWslDistribution.runCommand(vararg commands: String,
                                       ignoreExitCode: Boolean = false): String {
  val process = createWslCommandLine(*commands).createProcess()
  val stdout = CompletableFuture.supplyAsync {
    process.inputStream.readAllBytes()
  }
  waitProcess(process, commands.joinToString(" "), ignoreExitCode)
  return stdout.get().decodeToString().trimEnd('\n')
}


class ProcessExistedNotZeroException(private val stdError: String, val exitCode: Int, val tool: String) :
  Exception("Can't execute $tool: $exitCode. $stdError")

/**
 * Waits for process, and in case of error (exit code != 0) throws exception with stderr
 * if not [ignoreExitCode] throws [ProcessExistedNotZeroException] in case of error
 */
internal fun waitProcess(process: Process, tool: String, ignoreExitCode: Boolean = false) {
  val stderr = CompletableFuture.supplyAsync {
    process.errorStream.readAllBytes()
  }
  val exitCode = process.waitFor()
  if (exitCode != 0 && !ignoreExitCode) {
    throw ProcessExistedNotZeroException(stderr.get().decodeToString(), exitCode, tool)
  }
}
