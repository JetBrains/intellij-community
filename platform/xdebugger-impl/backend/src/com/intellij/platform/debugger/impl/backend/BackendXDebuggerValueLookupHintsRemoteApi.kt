// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.debugger.impl.rpc.RemoteValueHintId
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.deleteValueById
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.psi.PsiDocumentManager
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.XValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.await

internal class BackendXDebuggerValueLookupHintsRemoteApi : XDebuggerValueLookupHintsRemoteApi {
  override suspend fun adjustOffset(projectId: ProjectId, editorId: EditorId, offset: Int): Int {
    val project = projectId.findProject()
    val editor = editorId.findEditor()
    return readAction {
      // adjust offset to match with other actions, like go to declaration
      val document = editor.document
      TargetElementUtil.adjustOffset(PsiDocumentManager.getInstance(project).getPsiFile(document), document, offset)
    }
  }

  override suspend fun getExpressionInfo(projectId: ProjectId, editorId: EditorId, offset: Int, hintType: ValueHintType): ExpressionInfo? {
    return withContext(Dispatchers.EDT) {
      val project = projectId.findProject()
      val editor = editorId.findEditor()
      val evaluator = XDebuggerManager.getInstance(project).getCurrentSession()?.debugProcess?.evaluator ?: return@withContext null
      val expressionInfo = getExpressionInfo(evaluator, project, hintType, editor, offset)

      return@withContext expressionInfo
    }
  }

  private data class EditorEvaluateExpressionData(
    val adjustedOffset: Int,
    val hasSelection: Boolean,
    val selectionStart: Int,
    val selectionEnd: Int,
  )

  private suspend fun getExpressionInfo(
    evaluator: XDebuggerEvaluator, project: Project,
    type: ValueHintType?, editor: Editor, offset: Int,
  ): ExpressionInfo? {
    val document = editor.getDocument()
    val evaluateExpressionData = readAction {
      val selectionModel = editor.getSelectionModel()
      val selectionStart = selectionModel.selectionStart
      val selectionEnd = selectionModel.selectionEnd
      val hasSelection = selectionModel.hasSelection()
      EditorEvaluateExpressionData(offset, hasSelection, selectionStart, selectionEnd)
    }
    if ((type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT) && evaluateExpressionData.hasSelection
        && evaluateExpressionData.adjustedOffset in evaluateExpressionData.selectionStart..evaluateExpressionData.selectionEnd
    ) {
      return ExpressionInfo(TextRange(evaluateExpressionData.selectionStart, evaluateExpressionData.selectionEnd), isManualSelection = true)
    }
    val expressionInfo = readAction {
      evaluator.getExpressionInfoAtOffsetAsync(project, document, evaluateExpressionData.adjustedOffset,
                                               type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT)
    }.await()
    return expressionInfo
  }

  override suspend fun createHint(
    projectId: ProjectId,
    editorId: EditorId,
    offset: Int,
    hintType: ValueHintType,
  ): RemoteValueHintId? {
    return withContext(Dispatchers.EDT) {
      val project = projectId.findProject()
      val editor = editorId.findEditor()
      val point = editor.offsetToXY(offset)
      val session = XDebuggerManager.getInstance(project).getCurrentSession()
      if (session == null) {
        return@withContext null
      }
      val evaluator = session.getDebugProcess().getEvaluator()
      if (evaluator == null) {
        return@withContext null
      }
      val expressionInfo = getExpressionInfo(evaluator, project, hintType, editor, offset) ?: return@withContext null
      val hint = XValueHint(project, editor, point, hintType, offset, expressionInfo, evaluator, session, false)
      val hintId = storeValueGlobally(hint, type = RemoteValueHintValueIdType)
      hintId
    }
  }

  override suspend fun showHint(hintId: RemoteValueHintId): Flow<Unit> {
    val hint = findValueById(hintId, type = RemoteValueHintValueIdType) ?: return emptyFlow()

    return callbackFlow {
      withContext(Dispatchers.EDT) {
        hint.invokeHint {
          trySend(Unit)
          close()
        }
      }
      awaitClose()
    }
  }

  override suspend fun removeHint(hintId: RemoteValueHintId, force: Boolean) {
    val hint = findValueById(hintId, type = RemoteValueHintValueIdType) ?: return
    try {
      if (force) {
        withContext(Dispatchers.EDT) {
          hint.hideHint()
        }
      }
    }
    finally {
      deleteValueById(hintId, type = RemoteValueHintValueIdType)
    }
  }

  private object RemoteValueHintValueIdType : BackendValueIdType<RemoteValueHintId, XValueHint>(::RemoteValueHintId)
}