// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XExecutionStacksEvent
import com.intellij.xdebugger.impl.rpc.XSuspendContextDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class FrontendXSuspendContext(
  private val suspendContextDto: XSuspendContextDto,
  private val project: Project,
  private val cs: CoroutineScope,
) : XSuspendContext() {
  private val id = suspendContextDto.id

  val isStepping: Boolean
    get() = suspendContextDto.isStepping

  override fun computeExecutionStacks(container: XExecutionStackContainer) {
    cs.launch {
      XDebugSessionApi.getInstance().computeExecutionStacks(id).collect { executionStackEvent ->
        when (executionStackEvent) {
          is XExecutionStacksEvent.ErrorOccurred -> {
            container.errorOccurred(executionStackEvent.errorMessage)
          }
          is XExecutionStacksEvent.NewExecutionStacks -> {
            // TODO[IJPL-177087] narrower scope?
            val feStacks = executionStackEvent.stacks.map { FrontendXExecutionStack(it, project, cs) }
            container.addExecutionStack(feStacks, executionStackEvent.last)
          }
        }
      }
    }
  }
}