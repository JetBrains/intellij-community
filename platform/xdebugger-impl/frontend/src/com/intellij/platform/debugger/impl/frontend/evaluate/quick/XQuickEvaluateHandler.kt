// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.debugger.impl.frontend.FrontendXDebuggerManager
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.xdebugger.impl.evaluate.quick.XValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.concurrency.asPromise
import org.jetbrains.concurrency.await
import java.awt.Point

private val LOG = Logger.getInstance(XQuickEvaluateHandler::class.java)

internal class XQuickEvaluateHandler : QuickEvaluateHandler() {
  override fun isEnabled(project: Project): Boolean {
    val currentSession = FrontendXDebuggerManager.getInstance(project).currentSession
    return currentSession != null && currentSession.currentEvaluator != null
  }

  override fun createValueHint(project: Project, editor: Editor, point: Point, type: ValueHintType?): AbstractValueHint? {
    return null
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun createValueHintAsync(project: Project, editor: Editor, point: Point, type: ValueHintType): CancellableHint {
    val offset = AbstractValueHint.calculateOffset(editor, point)
    val document = editor.getDocument()
    val documentCoroutineScope = editor.childCoroutineScope(project, "XQuickEvaluateHandler#valueHint")
    val frontendType = FrontendApplicationInfo.getFrontendType()
    val currentSession = if (frontendType is FrontendType.Remote) {
      FrontendXDebuggerManager.getInstance(project).currentSession
    }
    else {
      // Monolith case, we do not want to break plugins e.g., IJPL-176963
      XDebugManagerProxy.getInstance().getCurrentSessionProxy(project)
    }

    val hintDeferred: Deferred<AbstractValueHint?> = documentCoroutineScope.async(Dispatchers.IO) {
      if (currentSession == null) return@async null
      val evaluator = currentSession.currentEvaluator ?: return@async null

      val adjustedOffset = readAction {
          // adjust offset to match with other actions, like go to declaration
          val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@readAction null
          TargetElementUtil.adjustOffset(psiFile, document, offset)
      } ?: return@async null

      val sideEffectsAllowed = type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT
      val expressionInfo = evaluator.getExpressionInfoAtOffsetAsync(project, document, adjustedOffset, sideEffectsAllowed).await()
                           ?: return@async null

      val range = expressionInfo.textRange
      if (range.startOffset > range.endOffset || range.startOffset < 0 || range.endOffset > document.textLength) {
        LOG.error("invalid range: $range, text length = ${document.textLength}")
        return@async null
      }
        XValueHint(project, editor, point, type, adjustedOffset, expressionInfo, evaluator, currentSession, false)
    }
    hintDeferred.invokeOnCompletion {
      documentCoroutineScope.cancel()
    }
    return CancellableHint(hintDeferred.asCompletableFuture().asPromise())
  }


  override fun canShowHint(project: Project): Boolean {
    return isEnabled(project)
  }

  override fun getValueLookupDelay(project: Project?): Int {
    return XDebuggerSettingsManager.getInstance().getDataViewSettings().getValueLookupDelay()
  }
}

/**
 * Please use this function only when really needed. Since returned [kotlinx.coroutines.CoroutineScope] should be manually closed.
 * In 99.9% of cases CoroutineScope should be provided from top, instead of manual creation based on Editor, Project etc.
 */
@DelicateCoroutinesApi
private fun Editor.childCoroutineScope(project: Project, name: String): CoroutineScope {
  val parentScope = project.service<HintScopeProvider>().cs
  val coroutineScope = parentScope.childScope(name)
  if (this is EditorImpl) {
    Disposer.register(disposable) {
      coroutineScope.cancel()
    }
  }
  return coroutineScope
}

@Service(Service.Level.PROJECT)
private class HintScopeProvider(val cs: CoroutineScope)