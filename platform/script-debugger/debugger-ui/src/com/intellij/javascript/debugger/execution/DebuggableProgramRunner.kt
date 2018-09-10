/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.debugger.DebuggableRunConfiguration

open class DebuggableProgramRunner : AsyncProgramRunner<RunnerSettings>() {
  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    FileDocumentManager.getInstance().saveAllDocuments()
    val configuration = environment.runProfile as DebuggableRunConfiguration
    val socketAddress = configuration.computeDebugAddress(state)
    val starter = { executionResult: ExecutionResult? ->
      startSession(environment) { configuration.createDebugProcess(socketAddress, it, executionResult, environment) }.runContentDescriptor
    }
    @Suppress("IfThenToElvis")
    if (state is DebuggableRunProfileState) {
      return state.execute(socketAddress.port).then(starter)
    }
    else {
      return resolvedPromise(starter(null))
    }
  }

  override fun getRunnerId(): String = "debuggableProgram"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return DefaultDebugExecutor.EXECUTOR_ID == executorId && profile is DebuggableRunConfiguration && profile.canRun(executorId, profile)
  }
}

inline fun startSession(environment: ExecutionEnvironment, crossinline starter: (session: XDebugSession) -> XDebugProcess): XDebugSession {
  return XDebuggerManager.getInstance(environment.project).startSession(environment, xDebugProcessStarter(starter))
}

inline fun xDebugProcessStarter(crossinline starter: (session: XDebugSession) -> XDebugProcess): XDebugProcessStarter = object : XDebugProcessStarter() {
  override fun start(session: XDebugSession) = starter(session)
}