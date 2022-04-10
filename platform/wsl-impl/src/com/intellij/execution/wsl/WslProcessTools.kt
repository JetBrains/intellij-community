package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import java.util.concurrent.CompletableFuture


internal fun AbstractWslDistribution.createWslCommandLine(vararg commands: String):
  GeneralCommandLine = patchCommandLine(GeneralCommandLine(*commands), null, WSLCommandLineOptions())

/**
 * Waits for process to complete and returns stdout as String. Uses [waitProcess] under the hood
 */
fun AbstractWslDistribution.runCommand(vararg commands: String): String {
  val process = createWslCommandLine(*commands).createProcess()
  val stdout = CompletableFuture.supplyAsync {
    process.inputStream.readAllBytes()
  }
  waitProcess(process, commands.joinToString(" "))
  return stdout.get().decodeToString().trimEnd('\n')
}

/**
 * Waits for process, and in case of error (exit code != 0) throws exception with stderr
 */
internal fun waitProcess(process: Process, tool: String) {
  val stderr = CompletableFuture.supplyAsync {
    process.errorStream.readAllBytes()
  }
  val exitCode = process.waitFor()
  if (exitCode != 0) {
    throw Exception("Can't execute $tool: $exitCode. ${stderr.get().decodeToString()}")
  }
}
