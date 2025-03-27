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
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler
import com.intellij.xdebugger.impl.frame.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.XDebuggerLuxApi
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.findValue
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog
import com.intellij.xdebugger.impl.ui.tree.actions.ShowReferringObjectsAction
import kotlinx.coroutines.*
import org.jetbrains.concurrency.await

internal class BackendXDebuggerLuxApi : XDebuggerLuxApi {
  override suspend fun showLuxEvaluateDialog(frameId: XStackFrameId, editorId: EditorId?, fileId: VirtualFileId?, xValueId: XValueId?) {
    val stackFrameModel = frameId.findValue() ?: return
    val evaluator = stackFrameModel.stackFrame.evaluator ?: return
    val session = stackFrameModel.session
    val project = session.project
    val editor = editorId?.findEditorOrNull()

    val editorVirtualFile = editor?.document?.let { FileDocumentManager.getInstance().getFile(it) }
    val virtualFile = fileId?.virtualFile() ?: editorVirtualFile

    val psiFile = editor?.let { PsiEditorUtil.getPsiFile(it) }

    val selectedValue = xValueId?.let { BackendXValueModel.findById(it)?.xValue }

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