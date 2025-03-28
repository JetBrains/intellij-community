// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.rpc.XDebuggerLuxApi
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog
import com.intellij.xdebugger.impl.ui.tree.actions.ShowReferringObjectsAction
import kotlinx.coroutines.*

internal class BackendXDebuggerLuxApi : XDebuggerLuxApi {
  override suspend fun showLuxInspectDialog(xValueId: XValueId, nodeName: String) {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return
    val session = xValueModel.session
    val project = session.project
    val xValue = xValueModel.xValue
    val sourcePositionDeferred = CompletableDeferred<XSourcePosition?>()
    xValue.computeSourcePosition {
      sourcePositionDeferred.complete(it)
    }
    val editorsProvider = session.debugProcess.editorsProvider
    val valueMarkers = session.valueMarkers

    project.service<BackendXDebuggerLuxApiCoroutineScope>().cs.launch {
      val sourcePosition = sourcePositionDeferred.await()
      withContext(Dispatchers.EDT) {
        val dialog = XInspectDialog(project, editorsProvider, sourcePosition, nodeName, xValue,
                                    valueMarkers, session, true)
        dialog.show()
      }
    }
  }

  override suspend fun showReferringObjectsDialog(xValueId: XValueId, nodeName: String) {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return
    val session = xValueModel.session
    val project = session.project
    val xValue = xValueModel.xValue
    val sourcePositionDeferred = CompletableDeferred<XSourcePosition?>()
    xValue.computeSourcePosition {
      sourcePositionDeferred.complete(it)
    }
    val valueMarkers = session.valueMarkers

    project.service<BackendXDebuggerLuxApiCoroutineScope>().cs.launch {
      val sourcePosition = sourcePositionDeferred.await()
      withContext(Dispatchers.EDT) {
        val dialog = ShowReferringObjectsAction.createReferringObjectsDialog(
          xValue, session, nodeName, sourcePosition, valueMarkers
        )
        dialog?.show()
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class BackendXDebuggerLuxApiCoroutineScope(project: Project, val cs: CoroutineScope)