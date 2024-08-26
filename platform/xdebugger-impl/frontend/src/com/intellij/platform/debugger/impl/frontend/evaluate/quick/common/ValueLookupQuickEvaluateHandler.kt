// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.asEntity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler.CancellableHint
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.RemoteValueHint
import com.intellij.xdebugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import fleet.rpc.remoteApiDescriptor
import fleet.util.UID
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.asCancellablePromise
import org.jetbrains.concurrency.resolvedPromise
import java.awt.Point

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
    val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
    val coroutineScope = editor.childCoroutineScope("ValueLookupManagerEditorScope")
    val hintCoroutineScope = editor.childCoroutineScope("ValueLookupManagerValueHintParentScope")

    val hint: Deferred<AbstractValueHint?> = coroutineScope.async(Dispatchers.IO) {
      withKernel {
        val remoteApi = RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>())
        val projectEntity = project.asEntity()
        if (projectEntity == null) {
          return@withKernel null
        }
        val projectId = projectEntity.projectId
        val editorId = editor.editorId()
        val canShowHint = remoteApi.canShowHint(projectId, editorId, offset, type)
        if (!canShowHint) {
          return@withKernel null
        }
        val hint = ValueLookupManagerValueHint(hintCoroutineScope, project, projectId, editor, point, type, offset)
        return@withKernel hint
      }
    }
    val hintPromise = hint.asCompletableFuture().asCancellablePromise()
    hintPromise.onProcessed {
      coroutineScope.cancel()
    }
    hintPromise.onError {
      if (it is java.util.concurrent.CancellationException) {
        hintCoroutineScope.cancel()
      }
    }

    return CancellableHint(hintPromise, null)
  }

  override fun canShowHint(project: Project): Boolean {
    return true
  }

  override fun getValueLookupDelay(project: Project?): Int {
    return XDebuggerSettingsManager.getInstance().getDataViewSettings().getValueLookupDelay()
  }
}

private class ValueLookupManagerValueHint(
  private val parentCoroutineScope: CoroutineScope,
  project: Project, private val projectId: UID,
  private val editor: Editor,
  point: Point,
  private val type: ValueHintType,
  private val offset: Int,
) : AbstractValueHint(project, editor, point, type, TextRange(offset, offset)) {
  private var remoteHint: Deferred<RemoteValueHint?>? = null
  private var hintCoroutineScope: CoroutineScope? = null

  @OptIn(DelicateCoroutinesApi::class)
  override fun evaluateAndShowHint() {
    hintCoroutineScope = parentCoroutineScope.childScope("ValueLookupManagerValueHintScope")
    val remoteHint = hintCoroutineScope!!.async(Dispatchers.IO) {
      val remoteApi = RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>())
      remoteApi.createHint(projectId, editor.editorId(), offset, type)
    }
    this@ValueLookupManagerValueHint.remoteHint = remoteHint
    hintCoroutineScope!!.launch(Dispatchers.IO) {
      val hint = remoteHint.await() ?: return@launch
      val remoteApi = RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>())
      val closedEvent = remoteApi.showHint(projectId, hint.id)
      withContext(Dispatchers.Main) {
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
      val remoteApi = RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>())
      remoteApi.removeHint(projectId, hint.id)
      hintCoroutineScope?.cancel()
    }
    super.hideHint()
  }
}

@OptIn(DelicateCoroutinesApi::class)
// TODO: migrate to coroutine scopes passed from top
private fun Editor.childCoroutineScope(name: String): CoroutineScope {
  val coroutineScope = GlobalScope.childScope(name)
  val disposable = (this as? EditorImpl)?.disposable ?: project
  if (disposable != null) {
    Disposer.register(disposable) {
      coroutineScope.cancel()
    }
  }
  return coroutineScope
}