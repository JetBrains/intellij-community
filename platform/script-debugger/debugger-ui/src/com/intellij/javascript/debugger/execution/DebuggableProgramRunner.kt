// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javascript.debugger.execution

import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.DebuggableRunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.debugger.DebuggableRunConfiguration

private val LOG = logger<DebuggableProgramRunner>()

@InternalIgnoreDependencyViolation
@Deprecated("Please consider implementing AsyncProgramRunner directly")
open class DebuggableProgramRunner : AsyncProgramRunner<RunnerSettings>() {
  override fun execute(environment: ExecutionEnvironment,
                       state: RunProfileState): Promise<RunContentDescriptor?> = doExecuteDebuggableProgram(environment, state)

  override fun getRunnerId(): String = "debuggableProgram"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return DefaultDebugExecutor.EXECUTOR_ID == executorId && profile is DebuggableRunConfiguration && profile.canRun(executorId, profile)
  }
}

@ApiStatus.Internal
inline fun startSession(environment: ExecutionEnvironment, crossinline starter: (session: XDebugSession) -> XDebugProcess): XDebugSession {
  return XDebuggerManager.getInstance(environment.project).startSession(environment, xDebugProcessStarter(starter))
}

@ApiStatus.Internal
inline fun xDebugProcessStarter(crossinline starter: (session: XDebugSession) -> XDebugProcess): XDebugProcessStarter = object : XDebugProcessStarter() {
  override fun start(session: XDebugSession) = starter(session)
}

private fun doExecuteDebuggableProgram(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
  FileDocumentManager.getInstance().saveAllDocuments()
  val configuration = environment.runProfile as DebuggableRunConfiguration

  return configuration.computeDebugAddressAsync(state).thenAsync{ socketAddress ->
    val starter = { executionResult: ExecutionResult? ->
      LOG.info("Debug session started address=$socketAddress, configuration=$configuration")
      startSession(environment) { configuration.createDebugProcess(socketAddress, it, executionResult, environment) }.runContentDescriptor
    }
    if (state is DebuggableRunProfileState) {
      state.execute(socketAddress.port).then(starter)
    }
    else {
      resolvedPromise(starter(null))
    }
  }
}