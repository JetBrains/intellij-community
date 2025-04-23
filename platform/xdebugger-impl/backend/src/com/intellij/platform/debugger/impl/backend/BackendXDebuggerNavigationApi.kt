// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.rpc.XDebuggerNavigationApi
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.ui.tree.actions.computeSourcePositionWithTimeout
import kotlinx.coroutines.*

internal class BackendXDebuggerNavigationApi : XDebuggerNavigationApi {
  override suspend fun navigateToXValue(xValueId: XValueId): Deferred<Boolean> {
    return navigate(xValueId) { xValue, navigatable ->
      xValue.computeSourcePosition(navigatable)
    }
  }

  override suspend fun navigateToXValueType(xValueId: XValueId): Deferred<Boolean> {
    return navigate(xValueId) { xValue, navigatable ->
      xValue.computeTypeSourcePosition(navigatable)
    }
  }

  private fun navigate(xValueId: XValueId, compute: (XValue, XNavigatable) -> Unit): Deferred<Boolean> {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return CompletableDeferred(false)
    val project = xValueModel.session.project
    val xValue = xValueModel.xValue
    return project.service<BackendXDebuggerNavigationCoroutineScope>().cs.async(Dispatchers.EDT) {
      val sourcePosition = computeSourcePositionWithTimeout { navigatable ->
        compute(xValue, navigatable)
      } ?: return@async false
      sourcePosition.createNavigatable(project).navigate(true)
      true
    }
  }
}

@Service(Service.Level.PROJECT)
private class BackendXDebuggerNavigationCoroutineScope(project: Project, val cs: CoroutineScope)