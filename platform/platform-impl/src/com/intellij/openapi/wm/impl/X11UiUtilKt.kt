// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
internal object X11UiUtilKt {

  private val LOG = logger<X11UiUtilKt>()

  private val unsupportedCommands = ConcurrentHashMap<String, Boolean>()

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
      val process = ProcessBuilder(*command).start()
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        LOG.info("$errorMessage: timeout")
        process.destroyForcibly()
        return ExecResult.Failure()
      }

      if (process.exitValue() != 0) {
        LOG.info(errorMessage + ": exit code " + process.exitValue())
        return ExecResult.ExitValue(process.exitValue())
      }
      val output = process.inputReader(StandardCharsets.UTF_8).readText().trim { it <= ' ' }
      return ExecResult.Success(output)
    }
    catch (e: Exception) {
      val exceptionMessage = e.message
      if (exceptionMessage?.contains("No such file or directory") == true) {
        unsupportedCommands[command[0]] = true
        LOG.info("$errorMessage: $exceptionMessage")
        LOG.trace(e)
      }
      else {
        LOG.info(errorMessage, e)
      }

      return ExecResult.Failure()
    }
  }
}

@ApiStatus.Internal
fun ExecResult.output(): String? {
  return (this as? ExecResult.Success)?.output
}
