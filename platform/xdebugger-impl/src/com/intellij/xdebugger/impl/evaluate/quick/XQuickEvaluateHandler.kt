// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Function
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
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

  override fun createValueHintAsync(project: Project, editor: Editor, point: Point, type: ValueHintType): CancellableHint {
    val session = XDebuggerManager.getInstance(project).getCurrentSession()
    if (session == null) {
      return CancellableHint.resolved(null)
    }

    val evaluator = session.getDebugProcess().getEvaluator()
    if (evaluator == null) {
      return CancellableHint.resolved(null)
    }
    var offset = AbstractValueHint.calculateOffset(editor, point)
    val document = editor.getDocument()
    // adjust offset to match with other actions, like go to declaration
    offset = TargetElementUtil.adjustOffset(PsiDocumentManager.getInstance(project).getPsiFile(document), document, offset)
    val infoPromise: Promise<ExpressionInfo?> = getExpressionInfo(evaluator, project, type, editor, offset)
    val hintPromise = infoPromise
      .thenAsync<AbstractValueHint?>(Function { expressionInfo: ExpressionInfo? ->
        val resultPromise = AsyncPromise<AbstractValueHint?>()
        UIUtil.invokeLaterIfNeeded {
          val textLength = document.textLength
          if (expressionInfo == null) {
            resultPromise.setResult(null)
            return@invokeLaterIfNeeded
          }
          val range = expressionInfo.textRange
          if (range.startOffset > range.endOffset || range.startOffset < 0 || range.endOffset > textLength) {
            LOG.error("invalid range: $range, text length = $textLength, evaluator: $evaluator")
            resultPromise.setResult(null)
            return@invokeLaterIfNeeded
          }
          resultPromise.setResult(XValueHint(project, editor, point, type, expressionInfo, evaluator, session, false))
        }
        resultPromise
      })
    return CancellableHint(hintPromise, infoPromise)
  }


  override fun canShowHint(project: Project): Boolean {
    return isEnabled(project)
  }

  override fun getValueLookupDelay(project: Project?): Int {
    return XDebuggerSettingsManager.getInstance().getDataViewSettings().getValueLookupDelay()
  }

  companion object {
    private fun getExpressionInfo(
      evaluator: XDebuggerEvaluator, project: Project,
      type: ValueHintType?, editor: Editor, offset: Int,
    ): Promise<ExpressionInfo?> {
      val selectionModel = editor.getSelectionModel()
      val selectionStart = selectionModel.selectionStart
      val selectionEnd = selectionModel.selectionEnd
      if ((type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT) && selectionModel.hasSelection()
          && selectionStart <= offset && offset <= selectionEnd
      ) {
        return resolvedPromise<ExpressionInfo?>(ExpressionInfo(TextRange(selectionStart, selectionEnd)))
      }
      return evaluator.getExpressionInfoAtOffsetAsync(project, editor.getDocument(), offset,
                                                      type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT)
    }
  }
}
