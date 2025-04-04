// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.FrontendXDebuggerManager
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.common.RemoteValueHint
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.evaluate.childCoroutineScope
import com.intellij.xdebugger.impl.evaluate.quick.XValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeProxy
import com.intellij.xdebugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.concurrency.asPromise
import java.awt.Point

private val LOG = Logger.getInstance(XQuickEvaluateHandler::class.java)

internal class XQuickEvaluateHandler : QuickEvaluateHandler() {
  override fun isEnabled(project: Project): Boolean {
    val currentSession = FrontendXDebuggerManager.getInstance(project).currentSession.value
    return currentSession != null && currentSession.currentEvaluator != null
  }

  override fun createValueHint(project: Project, editor: Editor, point: Point, type: ValueHintType?): AbstractValueHint? {
    return null
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun createValueHintAsync(project: Project, editor: Editor, point: Point, type: ValueHintType): CancellableHint {
    val offset = AbstractValueHint.calculateOffset(editor, point)
    val document = editor.getDocument()
    val documentCoroutineScope = editor.childCoroutineScope("XQuickEvaluateHandler#valueHint")
    val projectId = project.projectId()
    val editorId = editor.editorId()
    val adjustedOffsetDeferred = documentCoroutineScope.async(Dispatchers.IO) {
      XDebuggerValueLookupHintsRemoteApi.getInstance().adjustOffset(projectId, editorId, offset)
    }
    val expressionInfoDeferred = documentCoroutineScope.async(Dispatchers.IO) {
      val adjustedOffset = adjustedOffsetDeferred.await()
      val remoteApi = XDebuggerValueLookupHintsRemoteApi.getInstance()
      remoteApi.getExpressionInfo(projectId, editorId, adjustedOffset, type)
    }
    val hintDeferred: Deferred<AbstractValueHint?> = documentCoroutineScope.async(Dispatchers.IO) {
      val adjustedOffset = adjustedOffsetDeferred.await()
      val expressionInfo = expressionInfoDeferred.await()
      val textLength = document.textLength
      if (expressionInfo == null) {
        return@async null
      }
      val range = expressionInfo.textRange
      if (range.startOffset > range.endOffset || range.startOffset < 0 || range.endOffset > textLength) {
        LOG.error("invalid range: $range, text length = $textLength")
        return@async null
      }
      val frontendType = FrontendApplicationInfo.getFrontendType()
      if (useFeProxy() || (frontendType is FrontendType.RemoteDev && !frontendType.isLuxSupported)) {
        val currentSession = FrontendXDebuggerManager.getInstance(project).currentSession.value ?: return@async null
        val frontendEvaluator = currentSession.currentEvaluator ?: return@async null
        val valueMarkers = currentSession.valueMarkers
        val editorsProvider = currentSession.editorsProvider
        val sourcePosition = currentSession.sourcePosition.value
        XValueHint(project, editorsProvider, editor, point, type, adjustedOffset, expressionInfo, frontendEvaluator, valueMarkers, sourcePosition, false)
      }
      else if (frontendType is FrontendType.RemoteDev) {
        RemoteValueHint(project, projectId, editor, point, type, adjustedOffset, expressionInfo)
      }
      else {
        val session = XDebuggerManager.getInstance(project).getCurrentSession()
        if (session == null) {
          return@async null
        }
        val evaluator = session.getDebugProcess().getEvaluator()
        if (evaluator == null) {
          return@async null
        }
        XValueHint(project, editor, point, type, adjustedOffset, expressionInfo, evaluator, session, false)
      }
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
}