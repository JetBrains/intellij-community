package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.runBlocking


internal fun AbstractWslDistribution.createWslCommandLine(vararg commands: String,
                                                          options: WSLCommandLineOptions = WSLCommandLineOptions()):
  GeneralCommandLine = patchCommandLine(GeneralCommandLine(*commands), null, options)

/**
 * Executes [commands] on [AbstractWslDistribution], waits its completion and returns stdout as string
 */
@RequiresBackgroundThread
fun AbstractWslDistribution.runCommand(vararg commands: String, options: WSLCommandLineOptions = WSLCommandLineOptions()): Result<String> =
  runBlocking { createProcess(commands = commands, options = options).getResultStdoutStr() }

/**
 * Executes [commands] on [AbstractWslDistribution] and returns process
 */
fun AbstractWslDistribution.createProcess(vararg commands: String, options: WSLCommandLineOptions = WSLCommandLineOptions()): Process =
  createWslCommandLine(commands = commands, options = options).createProcess()
