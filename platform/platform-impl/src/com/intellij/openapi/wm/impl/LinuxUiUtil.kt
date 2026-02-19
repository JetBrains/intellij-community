// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
sealed interface ExecResult {
  class Success(val output: String) : ExecResult

  /**
   * [exitValue] cannot be 0
   */
  class ExitValue(val exitValue: Int) : ExecResult
  class Failure : ExecResult
}

@ApiStatus.Internal
object LinuxUiUtil {

  private val LOG = logger<LinuxUiUtil>()

  private val unsupportedCommands = ConcurrentHashMap<String, Boolean>()

  /**
   * Executes the command and returns its output. If the command is unsupported by OS, returns [ExecResult.Failure] and doesn't
   * try to execute/log the problem anymore
   */
  @JvmStatic
  fun exec(errorMessage: String, vararg command: String): ExecResult {
    ThreadingAssertions.assertBackgroundThread()

    if (command.isEmpty()) {
      LOG.error(errorMessage, "No command provided")
      return ExecResult.Failure()
    }

    if (unsupportedCommands.containsKey(command[0])) {
      // Avoid running and logging unsupported commands
      return ExecResult.Failure()
    }

    try {
      val processOutput = ExecUtil.execAndGetOutput(GeneralCommandLine(*command), 5000)
      val exitCode = processOutput.exitCode
      if (exitCode != 0) {
        LOG.debug("$errorMessage: exit code $exitCode")
        return ExecResult.ExitValue(exitCode)
      }

      val output = processOutput.stdout.trim { it <= ' ' }
      return ExecResult.Success(output)
    }
    catch (e: ExecutionException) {
      if (e is ProcessNotCreatedException && isNoFileOrDirectory(e.cause)) {
        unsupportedCommands[command[0]] = true
        LOG.info("$errorMessage: ${e.message}")
        LOG.trace(e)
      }
      else {
        LOG.info(errorMessage, e)
      }
      return ExecResult.Failure()
    }
  }
}

/**
 * There is no good API in jdk for such a check. The string comes from the JDK and is not localizable
 * (see os.cpp: `X(ENOENT, "No such file or directory")`), therefore, this solution should work on different OS-s and locales.
 */
private fun isNoFileOrDirectory(e: Throwable?): Boolean {
  return e?.message?.contains("No such file or directory") == true
}

@ApiStatus.Internal
fun ExecResult.output(): String? {
  return (this as? ExecResult.Success)?.output
}
