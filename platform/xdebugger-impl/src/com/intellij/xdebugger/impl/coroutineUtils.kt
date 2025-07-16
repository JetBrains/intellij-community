// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerApi
import com.intellij.platform.debugger.impl.rpc.XValueApi
import com.intellij.platform.debugger.impl.rpc.sourcePosition
import com.intellij.platform.project.projectId
import com.intellij.util.ThreeState
import com.intellij.xdebugger.frame.XInlineDebuggerDataCallback
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Used only for Java code, since MutableStateFlow function cannot be called there.
internal fun <T> createMutableStateFlow(initialValue: T): MutableStateFlow<T> {
  return MutableStateFlow(initialValue)
}

internal fun <T, R> mapFlow(f: Flow<T>, mapper: (T) -> R): Flow<R> = f.map(mapper)

// Used only for Java code, since MutableSharedFlow function cannot be called there.
internal fun <T> createMutableSharedFlow(replay: Int, extraBufferCapacity: Int): MutableSharedFlow<T> {
  return MutableSharedFlow(replay, extraBufferCapacity)
}

@Service(Service.Level.PROJECT)
internal class XDebugSessionSelectionService(project: Project, scope: CoroutineScope) {
  init {
    scope.launch {
      XDebugManagerProxy.getInstance().getCurrentSessionFlow(project).collectLatest { currentSession ->
        // switch to EDT, so select can execute immediately (it uses invokeLaterIfNeeded)
        withContext(Dispatchers.EDT) {
          currentSession?.sessionTab?.select()
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun startCurrentSessionListening(project: Project) {
      project.service<XDebugSessionSelectionService>()
    }
  }
}

internal fun performDebuggerActionAsync(e: AnActionEvent, action: suspend () -> Unit) {
  performDebuggerActionAsync(e.project, e.dataContext, action)
}

internal fun performDebuggerActionAsync(
  project: Project?,
  dataContext: DataContext,
  action: suspend () -> Unit,
) {
  val coroutineScope = project?.service<FrontendDebuggerActionProjectCoroutineScope>()?.cs
                       ?: service<FrontendDebuggerActionCoroutineScope>().cs

  coroutineScope.launch {
    action()
    val editor = dataContext.getData(CommonDataKeys.EDITOR)
    if (project != null && editor != null) {
      withContext(Dispatchers.EDT) {
        XDebuggerManagerApi.getInstance().reshowInlays(project.projectId(), editor.editorId())
      }
    }
  }
}

@Service(Service.Level.APP)
private class FrontendDebuggerActionCoroutineScope(val cs: CoroutineScope)

@Service(Service.Level.PROJECT)
private class FrontendDebuggerActionProjectCoroutineScope(val project: Project, val cs: CoroutineScope)

internal fun updateInlineDebuggerData(session: XDebugSessionProxy, xValue: XValue, callback: XInlineDebuggerDataCallback) {
  val manager = XDebugManagerProxy.getInstance()
  if (!manager.canShowInlineDebuggerData(xValue)) {
    return
  }

  val log = logger<XValueNodeImpl>()
  val scope = session.currentSuspendContextCoroutineScope
  log.info("Inline debugger: update for $xValue")
  if (scope == null) {
    log.info("Inline debugger: updateInlineDebuggerData skipped, current scope is null")
    return
  }
  scope.launch {
    manager.withId(xValue, session) { xValueId ->
      val (canCompute, positionFlow) = XValueApi.getInstance().computeInlineData(xValueId) ?: return@withId
      log.info("Inline debugger: computeInlineData returned $canCompute")
      if (canCompute != ThreeState.UNSURE) {
        positionFlow.toFlow().collect {
          withContext(Dispatchers.EDT) {
            val sourcePosition = it.sourcePosition()
            log.info("Inline debugger: updateInlineDebuggerData position is $sourcePosition")
            callback.computed(sourcePosition)
          }
        }
      }
      else {
        xValue.computeSourcePosition(callback::computed)
      }
    }
  }
}