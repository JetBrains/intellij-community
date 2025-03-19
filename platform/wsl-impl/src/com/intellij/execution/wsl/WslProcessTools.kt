// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus.Internal


internal fun AbstractWslDistribution.createWslCommandLine(vararg commands: String,
                                                          options: WSLCommandLineOptions = WSLCommandLineOptions()):
  GeneralCommandLine = patchCommandLine(GeneralCommandLine(*commands), null, options)

/**
 * Executes [commands] on [AbstractWslDistribution], waits its completion and returns stdout as string
 */
@RequiresBackgroundThread
@Internal
fun AbstractWslDistribution.runCommand(vararg commands: String, options: WSLCommandLineOptions = WSLCommandLineOptions()): Result<String> {
  val process = createProcess(commands = commands, options = options)
  return runBlockingMaybeCancellable {
    process.getResultStdoutStr()
  }
}

/**
 * Executes [commands] on [AbstractWslDistribution] and returns process
 */
@Internal
fun AbstractWslDistribution.createProcess(vararg commands: String, options: WSLCommandLineOptions = WSLCommandLineOptions()): Process =
  createWslCommandLine(commands = commands, options = options).createProcess()
