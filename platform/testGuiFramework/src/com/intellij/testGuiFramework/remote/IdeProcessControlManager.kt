// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.remote

import java.io.InputStream
import java.util.concurrent.TimeUnit

object IdeProcessControlManager {

  @Volatile
  private var currentIdeProcess: Process? = null

  /**
   * returns current Ide process
   */
  private fun getIdeProcess(): Process {
    return currentIdeProcess ?: throw Exception("Current IDE process is not initialised or already been killed")
  }

  /**
   * adds a new IDE process to control
   */
  fun submitIdeProcess(ideProcess: Process) {
    currentIdeProcess = ideProcess
  }

  fun waitForCurrentProcess(): Int = getIdeProcess().waitFor()

  fun waitForCurrentProcess(timeout: Long, timeUnit: TimeUnit): Boolean = getIdeProcess().waitFor(timeout, timeUnit)

  fun exitValue(): Int = getIdeProcess().exitValue()

  fun getErrorStream(): InputStream = getIdeProcess().errorStream

  /**
   * kills IDE java process if the pipeline for sockets has been crashed or IDE doesn't respond
   */
  fun killIdeProcess() {
    currentIdeProcess?.destroyForcibly() ?: throw Exception("Current IDE process is not initialised or already been killed")
  }
}