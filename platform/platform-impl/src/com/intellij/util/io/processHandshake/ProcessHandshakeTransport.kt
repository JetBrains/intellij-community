// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.processHandshake

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.OSProcessHandler
import java.io.Closeable
import java.io.IOException

interface ProcessHandshakeTransport<H> : Closeable {
  fun createProcessHandler(commandLine: GeneralCommandLine): BaseOSProcessHandler {
    return OSProcessHandler.Silent(commandLine)
  }

  /**
   * Blocks until the greeting message from the daemon process.
   * Returns null if the stream has reached EOF prematurely.
   */
  @Throws(IOException::class)
  fun readHandshake(): H?
}
