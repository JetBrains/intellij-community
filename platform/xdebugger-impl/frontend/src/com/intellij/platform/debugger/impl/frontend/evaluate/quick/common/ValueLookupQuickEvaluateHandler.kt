// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.impl.evaluate.childCoroutineScope
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.RemoteValueHint
import com.intellij.xdebugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.asPromise
import org.jetbrains.concurrency.resolvedPromise
import java.awt.Point

private val LOG = logger<ValueLookupManagerQuickEvaluateHandler>()

/**
 * Bridge between [QuickEvaluateHandler]s provided by [DebuggerSupport]s and [ValueLookupManager]
 */
// TODO: QuickEvaluateHandler should be converted to frontend EP and this one should be just XQuickEvaluateHandler
@ApiStatus.Internal
open class ValueLookupManagerQuickEvaluateHandler : QuickEvaluateHandler() {
  override fun isEnabled(project: Project): Boolean {
    return true
  }

  override fun createValueHint(project: Project, editor: Editor, point: Point, type: ValueHintType?): AbstractValueHint? {
    return null
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun createValueHintAsync(project: Project, editor: Editor, point: Point, type: ValueHintType?): CancellableHint {
    if (type == null) {
      return CancellableHint(resolvedPromise(), null)
    }
    val offset = AbstractValueHint.calculateOffset(editor, point)
    val document = editor.getDocument()
    val documentCoroutineScope = editor.childCoroutineScope("ValueLookupManagerQuickEvaluateHandler#valueHint")
    val projectId = project.projectId()
    val editorId = editor.editorId()
    val canShowHint = documentCoroutineScope.async(Dispatchers.IO) {
      withKernel {
        val remoteApi = XDebuggerValueLookupHintsRemoteApi.getInstance()
        remoteApi.canShowHint(projectId, editorId, offset, type)
      }
    }
    val expressionInfoDeferred = documentCoroutineScope.async(Dispatchers.IO) {
      if (!canShowHint.await()) {
        return@async null
      }
      withKernel {
        val remoteApi = XDebuggerValueLookupHintsRemoteApi.getInstance()
        val projectId = project.projectId()
        val editorId = editor.editorId()
        remoteApi.getExpressionInfo(projectId, editorId, offset, type)
      }
    }
    val hintDeferred: Deferred<AbstractValueHint?> = documentCoroutineScope.async(Dispatchers.IO) {
      if (!canShowHint.await()) {
        return@async null
      }
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
      val hint = ValueLookupManagerValueHint(project, projectId, editor, point, type, offset, expressionInfo)
      return@async hint
    }
    hintDeferred.invokeOnCompletion {
      documentCoroutineScope.cancel()
    }

    return CancellableHint(hintDeferred.asCompletableFuture().asPromise(), expressionInfoDeferred)
  }

  override fun canShowHint(project: Project): Boolean {
    return true
  }

  override fun getValueLookupDelay(project: Project?): Int {
    return XDebuggerSettingsManager.getInstance().getDataViewSettings().getValueLookupDelay()
  }
}

private class ValueLookupManagerValueHint(
  project: Project, private val projectId: ProjectId,
  private val editor: Editor,
  point: Point,
  private val type: ValueHintType,
  private val offset: Int,
  expressionInfo: ExpressionInfo,
) : AbstractValueHint(project, editor, point, type, expressionInfo.textRange) {
  private var remoteHint: Deferred<RemoteValueHint?>? = null
  private var hintCoroutineScope: CoroutineScope? = null

  @OptIn(DelicateCoroutinesApi::class)
  override fun evaluateAndShowHint() {
    hintCoroutineScope = editor.childCoroutineScope("ValueLookupManagerValueHintScope")
    val remoteHint = hintCoroutineScope!!.async(Dispatchers.IO) {
      val remoteApi = XDebuggerValueLookupHintsRemoteApi.getInstance()
      remoteApi.createHint(projectId, editor.editorId(), offset, type)
    }
    this@ValueLookupManagerValueHint.remoteHint = remoteHint
    hintCoroutineScope!!.launch(Dispatchers.IO) {
      val hint = remoteHint.await() ?: return@launch
      val remoteApi = XDebuggerValueLookupHintsRemoteApi.getInstance()
      val closedEvent = remoteApi.showHint(projectId, hint.id)
      withContext(Dispatchers.EDT) {
        closedEvent.collect {
          hideHint()
          processHintHidden()
        }
      }
    }
  }

  override fun hideHint() {
    hintCoroutineScope?.launch(Dispatchers.IO) {
      val hint = remoteHint?.await()
      if (hint == null) {
        hintCoroutineScope?.cancel()
        return@launch
      }
      val remoteApi = XDebuggerValueLookupHintsRemoteApi.getInstance()
      remoteApi.removeHint(projectId, hint.id)
      hintCoroutineScope?.cancel()
    }
    super.hideHint()
  }
}