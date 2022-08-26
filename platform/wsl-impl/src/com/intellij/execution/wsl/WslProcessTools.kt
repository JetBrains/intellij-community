package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.ProcessExistedNotZeroException
import com.intellij.execution.processTools.waitGetResultStdout


internal fun AbstractWslDistribution.createWslCommandLine(vararg commands: String):
  GeneralCommandLine = patchCommandLine(GeneralCommandLine(*commands), null, WSLCommandLineOptions())

/**
 * Executes [commands] on [AbstractWslDistribution], waits its completion and returns stdout as string
 * or throws [ProcessExistedNotZeroException]
 */
@Throws(ProcessExistedNotZeroException::class)
fun AbstractWslDistribution.runCommand(vararg commands: String): String =
  createProcess(*commands).waitGetResultStdout()

/**
 * Executes [commands] on [AbstractWslDistribution] and returns process
 */
fun AbstractWslDistribution.createProcess(vararg commands: String): Process =
  createWslCommandLine(*commands).createProcess()
