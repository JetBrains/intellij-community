// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.findProjectOrNull
import com.intellij.xdebugger.impl.rhizome.XValueEntity
import com.intellij.xdebugger.impl.rpc.XDebuggerNavigationApi
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.ui.tree.actions.XJumpToSourceActionBase
import com.jetbrains.rhizomedb.entity
import kotlinx.coroutines.*

internal class BackendXDebuggerNavigationApi : XDebuggerNavigationApi {
  override suspend fun navigateToXValue(xValueId: XValueId): Deferred<Boolean> {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return CompletableDeferred(false)
    val project = xValueEntity.sessionEntity.projectEntity.projectId.findProjectOrNull() ?: return CompletableDeferred(false)
    val xValue = xValueEntity.xValue
    return project.service<BackendXDebuggerNavigationCoroutineScope>().cs.async(Dispatchers.EDT) {
      XJumpToSourceActionBase.navigateByNavigatable(project) { navigatable ->
        xValue.computeSourcePosition(navigatable)
      }
    }
  }

  override suspend fun navigateToXValueType(xValueId: XValueId): Deferred<Boolean> {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return CompletableDeferred(false)
    val project = xValueEntity.sessionEntity.projectEntity.projectId.findProjectOrNull() ?: return CompletableDeferred(false)
    val xValue = xValueEntity.xValue
    return project.service<BackendXDebuggerNavigationCoroutineScope>().cs.async(Dispatchers.EDT) {
      XJumpToSourceActionBase.navigateByNavigatable(project) { navigatable ->
        xValue.computeTypeSourcePosition(navigatable)
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class BackendXDebuggerNavigationCoroutineScope(project: Project, val cs: CoroutineScope)