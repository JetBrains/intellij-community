// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.ui.ExecutionPositionUi
import com.intellij.xdebugger.impl.ui.showExecutionPointUi
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


internal class XDebuggerExecutionPointManager(private val project: Project,
                                              parentScope: CoroutineScope) {
  private val coroutineScope: CoroutineScope = parentScope.childScope(Dispatchers.EDT + CoroutineName(javaClass.simpleName))

  private val updateRequestFlow = MutableSharedFlow<ExecutionPositionUpdateRequest>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

  private val _activeSourceKindState = MutableStateFlow(XSourceKind.MAIN)
  private val activeSourceKindState: StateFlow<XSourceKind> = _activeSourceKindState.asStateFlow()
  var activeSourceKind: XSourceKind by _activeSourceKindState::value

  private val _gutterIconRendererState = MutableStateFlow<GutterIconRenderer?>(null)
  private val gutterIconRendererState: StateFlow<GutterIconRenderer?> = _gutterIconRendererState.asStateFlow()
  var gutterIconRenderer: GutterIconRenderer? by _gutterIconRendererState::value

  private val executionPointVmState = MutableStateFlow<ExecutionPointVm?>(null)
  private var executionPointVm: ExecutionPointVm? by executionPointVmState::value

  init {
    val uiScope = coroutineScope.childScope(CoroutineName("${javaClass.simpleName}/UI"))
    showExecutionPointUi(project, uiScope, executionPointVmState)

    // navigate when execution point changes
    uiScope.launch {
      executionPointVmState.filterNotNull().collect {
        it.navigateTo(ExecutionPositionNavigationMode.OPEN)
      }
    }

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      uiScope.launch {
        executionPointVmState
          .map { it != null }.distinctUntilChanged()
          .dropWhile { !it }  // ignore initial 'false' value
          .collect { hasHighlight ->
            if (hasHighlight) {
              EditorMouseHoverPopupControl.disablePopups(project)
            }
            else {
              EditorMouseHoverPopupControl.enablePopups(project)
            }
          }
      }
    }
  }

  fun clearExecutionPoint() {
    executionPointVm = null
  }

  fun setExecutionPoint(mainSourcePosition: XSourcePosition?, alternativeSourcePosition: XSourcePosition?, isTopFrame: Boolean) {
    executionPointVm = ExecutionPointVmImpl.create(project,
                                                   coroutineScope,
                                                   mainSourcePosition,
                                                   alternativeSourcePosition,
                                                   isTopFrame,
                                                   activeSourceKindState,
                                                   gutterIconRendererState,
                                                   updateRequestFlow)
  }

  @RequiresEdt
  fun isFullLineHighlighterAt(file: VirtualFile, line: Int, project: Project, isToCheckTopFrameOnly: Boolean): Boolean {
    return isCurrentFile(file) && ExecutionPositionUi.isFullLineHighlighterAt(file, line, project, isToCheckTopFrameOnly)
  }

  private fun isCurrentFile(file: VirtualFile): Boolean {
    val pointVm = executionPointVmState.value ?: return false
    return pointVm.mainPositionVm?.file == file ||
           pointVm.alternativePositionVm?.file == file
  }

  fun updateExecutionPosition(file: VirtualFile, toNavigate: Boolean) {
    if (isCurrentFile(file)) {
      val updateRequest = ExecutionPositionUpdateRequest(file, toNavigate)
      updateRequestFlow.tryEmit(updateRequest).also { check(it) }
    }
  }

  fun showExecutionPosition() {
    executionPointVm?.navigateTo(ExecutionPositionNavigationMode.OPEN)
  }
}

internal data class ExecutionPositionUpdateRequest(val file: VirtualFile, val isToScrollToPosition: Boolean)
