// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.platform.project.findProject
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler
import com.intellij.xdebugger.impl.rhizome.XDebuggerEvaluatorEntity
import com.intellij.xdebugger.impl.rhizome.XValueEntity
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import com.intellij.xdebugger.impl.rpc.XDebuggerLuxApi
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog
import com.intellij.xdebugger.impl.ui.tree.actions.ShowReferringObjectsAction
import com.jetbrains.rhizomedb.entity
import kotlinx.coroutines.*
import org.jetbrains.concurrency.await

internal class BackendXDebuggerLuxApi : XDebuggerLuxApi {
  override suspend fun showLuxEvaluateDialog(evaluatorId: XDebuggerEvaluatorId, editorId: EditorId?, fileId: VirtualFileId?, xValueId: XValueId?) {
    val evaluatorEntity = entity(XDebuggerEvaluatorEntity.EvaluatorId, evaluatorId) ?: return
    val sessionEntity = evaluatorEntity.sessionEntity
    val session = sessionEntity.session
    val project = sessionEntity.projectEntity.projectId.findProject()
    val evaluator = evaluatorEntity.evaluator
    val editor = editorId?.findEditorOrNull()

    val editorVirtualFile = editor?.document?.let { FileDocumentManager.getInstance().getFile(it) }
    val virtualFile = fileId?.virtualFile() ?: editorVirtualFile

    val psiFile = editor?.let { PsiEditorUtil.getPsiFile(it) }

    val selectedValue = xValueId?.let { entity(XValueEntity.XValueId, it)?.xValue }

    val expressionPromise = XDebuggerEvaluateActionHandler.getSelectedExpressionAsync(project, evaluator, editor, psiFile, selectedValue)

    project.service<BackendXDebuggerLuxApiCoroutineScope>().cs.launch {
      val expression = expressionPromise.await()
      withContext(Dispatchers.EDT) {
        val editorsProvider = session.debugProcess.editorsProvider
        val stackFrame = session.currentStackFrame
        XDebuggerEvaluateActionHandler.showDialog(session, virtualFile, editorsProvider, stackFrame, evaluator, expression)
      }
    }
  }

  override suspend fun showLuxInspectDialog(xValueId: XValueId, nodeName: String) {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return
    val sessionEntity = xValueEntity.sessionEntity
    val session = sessionEntity.session
    val project = sessionEntity.projectEntity.projectId.findProject()
    val xValue = xValueEntity.xValue
    val sourcePositionDeferred = CompletableDeferred<XSourcePosition?>()
    xValue.computeSourcePosition {
      sourcePositionDeferred.complete(it)
    }
    val editorsProvider = session.debugProcess.editorsProvider
    val valueMarkers = (session as? XDebugSessionImpl)?.valueMarkers

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
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return
    val sessionEntity = xValueEntity.sessionEntity
    val session = sessionEntity.session
    val project = sessionEntity.projectEntity.projectId.findProject()
    val xValue = xValueEntity.xValue
    val sourcePositionDeferred = CompletableDeferred<XSourcePosition?>()
    xValue.computeSourcePosition {
      sourcePositionDeferred.complete(it)
    }
    val valueMarkers = (session as? XDebugSessionImpl)?.valueMarkers

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