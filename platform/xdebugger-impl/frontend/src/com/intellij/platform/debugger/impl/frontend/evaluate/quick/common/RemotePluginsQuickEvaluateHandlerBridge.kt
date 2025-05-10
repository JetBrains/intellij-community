// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.impl.evaluate.childCoroutineScope
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.platform.debugger.impl.rpc.RemoteValueHintId
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import kotlinx.coroutines.*
import java.awt.Point

internal class RemoteValueHint(
  project: Project, private val projectId: ProjectId,
  private val editor: Editor,
  point: Point,
  private val type: ValueHintType,
  private val offset: Int,
  expressionInfo: ExpressionInfo,
) : AbstractValueHint(project, editor, point, type, expressionInfo.textRange) {
  private var remoteHint: Deferred<RemoteValueHintId?>? = null
  private var hintCoroutineScope: CoroutineScope? = null

  @OptIn(DelicateCoroutinesApi::class)
  override fun evaluateAndShowHint() {
    hintCoroutineScope = editor.childCoroutineScope("RemoteValueHintScope")
    val remoteHint = hintCoroutineScope!!.async(Dispatchers.IO) {
      val remoteApi = XDebuggerValueLookupHintsRemoteApi.getInstance()
      remoteApi.createHint(projectId, editor.editorId(), offset, type)
    }
    this@RemoteValueHint.remoteHint = remoteHint
    hintCoroutineScope!!.launch(Dispatchers.IO) {
      val hint = remoteHint.await() ?: return@launch
      val remoteApi = XDebuggerValueLookupHintsRemoteApi.getInstance()
      val closedEvent = remoteApi.showHint(hint)
      withContext(Dispatchers.EDT) {
        closedEvent.collect {
          hideHint(force = false)
          processHintHidden()
        }
      }
    }
  }

  /**
   * Since by current design AbstractValueHint may be hidden, but not closed,
   * for example, when we hover variable and click on expand the new popup will be shown,
   * while ValueLookupManager will forget about this hint and won't control hints hiding.
   * For this case [force] is used, it is [true] only when we want to close all the popups on backend too
   * Otherwise, we will forget about the hint on frontend side, and backend will control it.
   */
  fun hideHint(force: Boolean) {
    hintCoroutineScope?.launch(Dispatchers.IO) {
      val hint = remoteHint?.await()
      if (hint == null) {
        hintCoroutineScope?.cancel()
        return@launch
      }
      val remoteApi = XDebuggerValueLookupHintsRemoteApi.getInstance()
      remoteApi.removeHint(hint, force)
      hintCoroutineScope?.cancel()
    }
    super.hideHint()
  }

  override fun hideHint() {
    hideHint(false)
  }
}