// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.evaluate.childCoroutineScope
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.concurrency.asPromise
import org.jetbrains.concurrency.await
import java.awt.Point

private val LOG = Logger.getInstance(XQuickEvaluateHandler::class.java)

class XQuickEvaluateHandler : QuickEvaluateHandler() {
  override fun isEnabled(project: Project): Boolean {
    val session = XDebuggerManager.getInstance(project).getCurrentSession()
    return session != null && session.getDebugProcess().getEvaluator() != null
  }

  override fun createValueHint(project: Project, editor: Editor, point: Point, type: ValueHintType?): AbstractValueHint? {
    return null
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun createValueHintAsync(project: Project, editor: Editor, point: Point, type: ValueHintType): CancellableHint {
    val session = XDebuggerManager.getInstance(project).getCurrentSession()
    if (session == null) {
      return CancellableHint.resolved(null)
    }
    val evaluator = session.getDebugProcess().getEvaluator()
    if (evaluator == null) {
      return CancellableHint.resolved(null)
    }
    val offset = AbstractValueHint.calculateOffset(editor, point)
    val document = editor.getDocument()
    val documentCoroutineScope = editor.childCoroutineScope("XQuickEvaluateHandler#valueHint")
    val expressionInfoDeferred = documentCoroutineScope.async(Dispatchers.IO) {
      getExpressionInfo(evaluator, project, type, editor, offset)
    }
    val hintDeferred: Deferred<AbstractValueHint?> = documentCoroutineScope.async(Dispatchers.IO) {
      val expressionInfo = expressionInfoDeferred.await()
      val textLength = document.textLength
      if (expressionInfo == null) {
        return@async null
      }
      val range = expressionInfo.textRange
      if (range.startOffset > range.endOffset || range.startOffset < 0 || range.endOffset > textLength) {
        LOG.error("invalid range: $range, text length = $textLength, evaluator: $evaluator")
        return@async null
      }
      XValueHint(project, editor, point, type, expressionInfo, evaluator, session, false)
    }
    hintDeferred.invokeOnCompletion {
      documentCoroutineScope.cancel()
    }
    return CancellableHint(hintDeferred.asCompletableFuture().asPromise(), expressionInfoDeferred)
  }


  override fun canShowHint(project: Project): Boolean {
    return isEnabled(project)
  }

  override fun getValueLookupDelay(project: Project?): Int {
    return XDebuggerSettingsManager.getInstance().getDataViewSettings().getValueLookupDelay()
  }

  companion object {
    private data class EditorEvaluateExpressionData(val adjustedOffset: Int, val hasSelection: Boolean, val selectionStart: Int, val selectionEnd: Int)

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
  }
}
