package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.getResultStdoutStr
import kotlinx.coroutines.runBlocking


internal fun AbstractWslDistribution.createWslCommandLine(vararg commands: String):
  GeneralCommandLine = patchCommandLine(GeneralCommandLine(*commands), null, WSLCommandLineOptions())

/**
 * Executes [commands] on [AbstractWslDistribution], waits its completion and returns stdout as string
 */
fun AbstractWslDistribution.runCommand(vararg commands: String): Result<String> =
  runBlocking { createProcess(*commands).getResultStdoutStr() }

/**
 * Executes [commands] on [AbstractWslDistribution] and returns process
 */
fun AbstractWslDistribution.createProcess(vararg commands: String): Process =
  createWslCommandLine(*commands).createProcess()
