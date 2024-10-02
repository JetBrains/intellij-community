// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.psi.PsiDocumentManager
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.evaluate.quick.XValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.RemoteValueHintId
import com.intellij.xdebugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.await
import java.awt.Point
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class BackendXDebuggerValueLookupHintsRemoteApi : XDebuggerValueLookupHintsRemoteApi {
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
      // adjust offset to match with other actions, like go to declaration
      val adjustedOffset = TargetElementUtil.adjustOffset(PsiDocumentManager.getInstance(project).getPsiFile(document), document, offset)
      val selectionModel = editor.getSelectionModel()
      val selectionStart = selectionModel.selectionStart
      val selectionEnd = selectionModel.selectionEnd
      val hasSelection = selectionModel.hasSelection()
      EditorEvaluateExpressionData(adjustedOffset, hasSelection, selectionStart, selectionEnd)
    }
    if ((type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT) && evaluateExpressionData.hasSelection
        && evaluateExpressionData.adjustedOffset in evaluateExpressionData.selectionStart..evaluateExpressionData.selectionEnd
    ) {
      return ExpressionInfo(TextRange(evaluateExpressionData.selectionStart, evaluateExpressionData.selectionEnd))
    }
    val expressionInfo = evaluator.getExpressionInfoAtOffsetAsync(project, document, evaluateExpressionData.adjustedOffset,
                                                                  type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT).await()
    return expressionInfo
  }


  override suspend fun canShowHint(projectId: ProjectId, editorId: EditorId, offset: Int, hintType: ValueHintType): Boolean {
    return withContext(Dispatchers.EDT) {
      val project = projectId.findProject()
      val editor = editorId.findEditor()
      val point = editor.offsetToXY(offset)

      val canShowHint = getValueHintFromDebuggerPlugins(project, editor, point, hintType) != null
      return@withContext canShowHint
    }
  }

  override suspend fun createHint(
    projectId: ProjectId,
    editorId: EditorId,
    offset: Int,
    hintType: ValueHintType,
    fromPlugins: Boolean,
  ): RemoteValueHintId? {
    return withContext(Dispatchers.EDT) {
      val project = projectId.findProject()
      val editor = editorId.findEditor()
      val point = editor.offsetToXY(offset)

      val hint = if (fromPlugins) {
        getValueHintFromDebuggerPlugins(project, editor, point, hintType) ?: return@withContext null
      }
      else {
        val session = XDebuggerManager.getInstance(project).getCurrentSession()
        if (session == null) {
          return@withContext null
        }
        val evaluator = session.getDebugProcess().getEvaluator()
        if (evaluator == null) {
          return@withContext null
        }
        val expressionInfo = getExpressionInfo(evaluator, project, hintType, editor, offset) ?: return@withContext null
        XValueHint(project, editor, point, hintType, expressionInfo, evaluator, session, false)
      }
      val hintId = BackendDebuggerValueLookupHintsHolder.getInstance(project).registerNewHint(hint)
      RemoteValueHintId(hintId)
    }
  }

  private suspend fun getValueHintFromDebuggerPlugins(
    project: Project,
    editor: Editor,
    point: Point,
    hintType: ValueHintType,
  ): AbstractValueHint? {
    for (support in DebuggerSupport.getDebuggerSupports()) {
      val handler = support.quickEvaluateHandler
      if (handler.isEnabled(project)) {
        val hint = handler.createValueHintAsync(project, editor, point, hintType).hintPromise.await()
        if (hint != null) {
          return hint
        }
      }
    }
    return null
  }

  override suspend fun showHint(projectId: ProjectId, hintId: RemoteValueHintId): Flow<Unit> {
    val project = projectId.findProject()
    val hint = BackendDebuggerValueLookupHintsHolder.getInstance(project).getHintById(hintId.id) ?: return emptyFlow()
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

  override suspend fun removeHint(projectId: ProjectId, hintId: RemoteValueHintId, force: Boolean) {
    val project = projectId.findProject()
    val hint = BackendDebuggerValueLookupHintsHolder.getInstance(project).getHintById(hintId.id) ?: return
    BackendDebuggerValueLookupHintsHolder.getInstance(project).removeHint(hintId.id)
    if (force) {
      withContext(Dispatchers.EDT) {
        hint.hideHint()
      }
    }
  }
}

// exposed only for backend.split part
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class BackendDebuggerValueLookupHintsHolder(project: Project) {
  private val idCounter = AtomicInteger()
  private val hints = ConcurrentHashMap<Int, AbstractValueHint>()

  fun registerNewHint(hint: AbstractValueHint): Int {
    val newHintId = idCounter.incrementAndGet()
    hints[newHintId] = hint
    return newHintId
  }

  fun removeHint(hintId: Int) {
    hints.remove(hintId)
  }

  fun getHintById(hintId: Int): AbstractValueHint? {
    return hints[hintId]
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BackendDebuggerValueLookupHintsHolder = project.service<BackendDebuggerValueLookupHintsHolder>()
  }
}