// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs a system tool and returns its standard output as lines.
 *
 * The caller passes a fixed command, never user input. The tool gets a closed standard input and its standard error is
 * discarded. A tool that runs longer than [timeout] is killed, and the call fails with an [IOException].
 */
@Throws(IOException::class)
internal fun readCommandOutput(vararg command: String, timeout: Duration = 20.seconds): List<String> {
  val process = ProcessBuilder(*command)
    .redirectError(ProcessBuilder.Redirect.DISCARD)
    .start()
  try {
    process.outputStream.close()
    val lines = process.inputStream.bufferedReader().use { it.readLines() }
    if (!process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
      throw IOException("The command '${command.first()}' did not finish in $timeout")
    }
    return lines
  }
  finally {
    process.destroyForcibly()
  }
}
