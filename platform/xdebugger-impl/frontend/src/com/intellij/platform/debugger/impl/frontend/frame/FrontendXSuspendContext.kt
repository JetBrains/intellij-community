// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XExecutionStacksEvent
import com.intellij.xdebugger.impl.rpc.XSuspendContextDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class FrontendXSuspendContext(
  private val suspendContextDto: XSuspendContextDto,
  private val project: Project,
  private val lifetimeScope: CoroutineScope,
) : XSuspendContext() {
  private val id = suspendContextDto.id

  val isStepping: Boolean
    get() = suspendContextDto.isStepping

  internal var activeExecutionStack: FrontendXExecutionStack? = null

  override fun getActiveExecutionStack(): XExecutionStack? {
    return activeExecutionStack
  }

  override fun computeExecutionStacks(container: XExecutionStackContainer) {
    lifetimeScope.launch {
      XDebugSessionApi.getInstance().computeExecutionStacks(id).collect { executionStackEvent ->
        when (executionStackEvent) {
          is XExecutionStacksEvent.ErrorOccurred -> {
            container.errorOccurred(executionStackEvent.errorMessage)
          }
          is XExecutionStacksEvent.NewExecutionStacks -> {
            // TODO[IJPL-177087]: here we are binding FrontendXExecutionStack to the suspend context scope,
            //  which is the safest-narrowest scope in our possession.
            //  However, maybe it's possible to set up, for example, a scope that ends when another stack is selected from a combobox.
            //  But it requires further investigation.
            val feStacks = executionStackEvent.stacks.map { FrontendXExecutionStack(it, project, lifetimeScope) }
            container.addExecutionStack(feStacks, executionStackEvent.last)
          }
        }
      }
    }
  }

  fun cancel() {
    lifetimeScope.cancel()
  }
}